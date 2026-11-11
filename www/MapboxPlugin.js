var nativeExec = require('cordova/exec');
var callbacks = new Set();
var lifecycleGeneration = 0;
function isCancellationError(error) {
  var message = typeof error === 'string' ? error : error && error.message;
  return !!(error && error.code === 'MAPBOX_OPERATION_CANCELLED') ||
    message === 'Map closed; operation cancelled.' ||
    message === 'Location request was cancelled.' ||
    message === 'Location request superseded.';
}
function cancellationError(action, message) {
  var error = new Error(message || 'Map closed; operation cancelled.');
  error.name = 'MapboxCancellationError';
  error.code = 'MAPBOX_OPERATION_CANCELLED';
  error.action = action;
  return error;
}
function exec(success, error, service, action, args) {
  callbacks.forEach(function (previous) {
    if (previous.stream && previous.action === action) {
      previous.active = false; callbacks.delete(previous);
    }
  });
  var record = { action: action, error: error, active: true, stream: action.indexOf('register') === 0 };
  callbacks.add(record);
  function finish(handler, value) {
    if (!record.active) return;
    if (!record.stream) { record.active = false; callbacks.delete(record); }
    if (handler) handler(value);
  }
  try {
    nativeExec(function (value) { finish(success, value); }, function (value) {
      if (isCancellationError(value)) {
        value = cancellationError(action, typeof value === 'string' ? value : value.message);
      }
      record.stream = false; finish(error, value);
    }, service, action, args);
  } catch (e) { record.stream = false; finish(error, e); }
}
function cancelCallbacks() {
  var old = Array.from(callbacks);
  old.forEach(function (record) {
    if (record.action === 'close') return;
    callbacks.delete(record);
    record.active = false;
    if (!record.stream && record.error) {
      try { record.error(cancellationError(record.action)); }
      catch (e) { if (typeof console !== 'undefined') console.warn('Mapbox cancellation callback failed.', e); }
    }
  });
}
function observeNativeClose(generation) {
  nativeExec(function () {
    if (generation !== lifecycleGeneration) return;
    lifecycleGeneration++;
    stopTouchRegions();
    cancelCallbacks();
  }, function () {}, SERVICE, 'registerCloseCallback', []);
}

var SERVICE = 'MapboxPlugin';
var touchRegions = null;
var touchGeneration = 0;

function validateCameraZoomBounds(options) {
  ['minZoom', 'maxZoom'].forEach(function (key) {
    var value = options[key];
    if (value != null && (typeof value !== 'number' || !isFinite(value) || value < 0 || value > 25.5)) {
      throw new Error(key + ' must be a finite number between 0 and 25.5.');
    }
  });
  if (options.minZoom != null && options.maxZoom != null && options.minZoom > options.maxZoom) {
    throw new Error('minZoom must be less than or equal to maxZoom.');
  }
}

function stopTouchRegions() {
  touchGeneration++;
  var previous = touchRegions;
  touchRegions = null;
  if (previous) cleanup(function () { previous.stop(); });
  stopLegacyTouchBridge();
}

function cleanup(work) {
  try { work(); } catch (e) {
    if (typeof console !== 'undefined') console.warn('Mapbox cleanup failed.', e);
  }
}

function stopLegacyTouchBridge() {
  if (typeof window === 'undefined') return;
  var win = window;
  var bridge = win.__mapboxTouchBridge;
  var handlers = win.__mapboxTouchHandlers;
  // Snapshot before stop(): older implementations clear their own globals.
  var observers = [win.__mapboxTouchObserver, win.__mapboxResizeObserver];
  var frame = bridge && bridge.frame;
  var retry = bridge && bridge.retry;
  if (bridge) {
    if (typeof bridge.stop === 'function') cleanup(function () { bridge.stop(); });
    cleanup(function () { bridge.stopped = true; });
    if (frame != null) cleanup(function () { win.cancelAnimationFrame(frame); });
    if (retry != null) cleanup(function () { win.clearTimeout(retry); });
  }
  observers.forEach(function (observer) {
    if (observer && typeof observer.disconnect === 'function') cleanup(function () { observer.disconnect(); });
  });
  function remove(target, names, handler, capture) {
    if (!target || typeof handler !== 'function') return;
    names.forEach(function (name) {
      cleanup(function () { target.removeEventListener(name, handler, capture); });
    });
  }
  if (handlers) {
    remove(win, ['resize'], handlers.schedule, false);
    remove(win, ['scroll'], handlers.schedule, true);
    remove(win.document, ['transitionrun', 'transitionend', 'transitioncancel',
      'animationstart', 'animationend', 'animationcancel', 'load'], handlers.schedule, true);
    remove(win, ['pagehide'], handlers.pagehide, false);
  }
  ['__mapboxTouchBridge', '__mapboxTouchHandlers', '__mapboxTouchObserver', '__mapboxResizeObserver'].forEach(function (key) {
    cleanup(function () { win[key] = null; });
  });
}

function call(action, args) {
  return new Promise(function (resolve, reject) {
    try {
      var json = JSON.stringify(args || []);
      if (json.length > 4 * 1024 * 1024) throw new Error('Map input exceeds the 4 MiB limit.');
      var options = (args || [])[0] || {};
      if (action === 'downloadOfflineRegion' || action === 'downloadOfflineRegionForRect') {
        var minZoom = options.minZoom === undefined ? 10 : Number(options.minZoom);
        var maxZoom = options.maxZoom === undefined ? 16 : Number(options.maxZoom);
        if (!isFinite(minZoom) || !isFinite(maxZoom) || minZoom < 2 || maxZoom > 18 || minZoom > maxZoom) {
          throw new Error('Offline zoom must be between 2 and 18 with minZoom <= maxZoom.');
        }
      }
    } catch (error) {
      reject(error);
      return;
    }
    exec(resolve, reject, SERVICE, action, args || []);
  });
}

// Loaded on first use, like AndroidTouchRegions, so module load order never matters.
function markerImages() { return require('./MarkerImageSource'); }

// Checked here, without loading the module, so URL-only and Find markers keep the exact
// original call path. Keys: addMarker uses imageBinary/imageMimeType, loadMarkers the
// capitalized ImageBinary/ImageMimeType.
function hasBinaryFields(markers, binaryKey, mimeKey) {
  for (var i = 0; i < markers.length; i++) {
    var marker = markers[i];
    if (marker && typeof marker === 'object' &&
        (Object.prototype.hasOwnProperty.call(marker, binaryKey) ||
         Object.prototype.hasOwnProperty.call(marker, mimeKey))) return true;
  }
  return false;
}
// Native rejects calls above 4 MiB; keep headroom for the options wrapper.
var MARKER_BATCH_BUDGET = 4 * 1024 * 1024 - 64 * 1024;

function markerLabel(id, index) {
  if (id !== undefined && id !== null && id !== '') return '"' + String(id).slice(0, 64) + '"';
  return index === null ? '(no id)' : 'at index ' + index;
}

// Waypoint binaries become data URIs. Every marker is converted and validated before
// any native change; an invalid binary rejects the whole call, like an invalid imageUrl.
function loadMarkersWithImages(options) {
  var lifecycle = lifecycleGeneration;
  var cache = {};
  var keys = markerImages().LOAD_KEYS;
  return Promise.all(options.markers.map(function (marker, index) {
    return markerImages().prepare(window, marker, keys, cache, markerLabel(marker && marker.Id, index));
  })).then(function (prepared) {
    if (lifecycle !== lifecycleGeneration) throw cancellationError('loadMarkers');
    var base = {};
    Object.keys(options).forEach(function (key) { if (key !== 'markers') base[key] = options[key]; });
    var batches = splitMarkerBatches(prepared, base);
    if (batches.length === 1) {
      base.markers = prepared;
      return call('loadMarkers', [base]);
    }
    // Too large for one bridge call: the first batch honours replace, later ones append.
    // Missing Ids keep their whole-list index, matching the single-call default.
    batches.forEach(function (batch) {
      batch.items.forEach(function (entry) {
        if (entry.marker && typeof entry.marker === 'object' &&
            (entry.marker.Id === undefined || entry.marker.Id === null)) {
          entry.marker = Object.assign({}, entry.marker, { Id: String(entry.index) });
        }
      });
    });
    var chain = Promise.resolve();
    batches.forEach(function (batch, number) {
      chain = chain.then(function () {
        if (lifecycle !== lifecycleGeneration) throw cancellationError('loadMarkers');
        var request = Object.assign({}, base);
        if (number > 0) request.replace = false;
        request.markers = batch.items.map(function (entry) { return entry.marker; });
        return call('loadMarkers', [request]);
      });
    });
    return chain.then(function () { return undefined; });
  });
}

function splitMarkerBatches(markers, base) {
  var overhead = markerImages().nativeJsonSize(Object.assign({}, base, { replace: false, markers: [] }));
  var batches = [];
  var current = { items: [], size: overhead };
  markers.forEach(function (marker, index) {
    var size = markerImages().nativeJsonSize(marker === undefined ? null : marker) + 1;
    if (current.items.length && current.size + size > MARKER_BATCH_BUDGET) {
      batches.push(current);
      current = { items: [], size: overhead };
    }
    current.items.push({ marker: marker, index: index });
    current.size += size;
  });
  batches.push(current);
  return batches;
}

var api = {
  // Cancellation settles the old request; it does not mean the next map failed.
  isCancellationError: isCancellationError,
  diagnostic: function () {
    var result = {
      cordova: !!window.cordova,
      service: SERVICE,
      pluginObject: !!window.MapboxPlugin,
      plugins: []
    };

    try {
      var pluginList = cordova.require('cordova/plugin_list');
      result.plugins = pluginList.map(function (plugin) {
        return {
          id: plugin.id,
          pluginId: plugin.pluginId,
          clobbers: plugin.clobbers || []
        };
      });
    } catch (e) {
      result.pluginListError = e && e.message ? e.message : String(e);
    }

    return result;
  },

  initialize: function (options) {
    options = options || {};
    // Validate before serialization (which turns NaN/Infinity into null) or changing touch routing.
    try {
      validateCameraZoomBounds(options);
    } catch (error) {
      return Promise.reject(error);
    }
    delete options.token;
    stopTouchRegions();
    cancelCallbacks();
    var lifecycle = ++lifecycleGeneration;
    var generation = touchGeneration;
    var automatic = typeof window !== 'undefined' && window.cordova &&
      (window.cordova.platformId === 'android' || window.cordova.platformId === 'ios') &&
      options.behindWebView === true;
    var controlsOnly = options.autoTouchRouting === false;
    return call('initialize', [options]).then(function (result) {
      if (lifecycle !== lifecycleGeneration) throw cancellationError('initialize');
      if (typeof window !== 'undefined' && window.cordova &&
          /^(android|ios)$/.test(window.cordova.platformId)) observeNativeClose(lifecycle);
      if (!automatic || generation !== touchGeneration) return result;
      var start = require('./AndroidTouchRegions');
      touchRegions = start(window, function (rects) { return call('setTouchableRects', [rects]); },
        { controlsOnly: controlsOnly });
      return touchRegions.ready.then(function () {
        if (lifecycle !== lifecycleGeneration) throw cancellationError('initialize');
        return result;
      });
    });
  },

  ping: function () {
    return call('ping', []);
  },

  getMapboxVersion: function (success, error) {
    exec(success, error, SERVICE, 'getMapboxVersion', []);
  },

  setCamera: function (options) {
    return call('setCamera', [options || {}]);
  },

  resetMapBearing: function () {
    return call('resetMapBearing', []);
  },

  flyTo: function (options) {
    return call('flyTo', [options || {}]);
  },

  setViewport: function (options) {
    return call('setViewport', [options || {}]);
  },

  resizeMap: function (options) {
    return call('resizeMap', [options || {}]);
  },

  setTouchableRects: function (rects) {
    // An explicit caller takes ownership until the next initialize.
    stopTouchRegions();
    return call('setTouchableRects', [rects || []]);
  },

  enableUserLocation: function () {
    return call('enableUserLocation', []);
  },

  setDeviceHeadingEnabled: function (options) {
    return call('setDeviceHeadingEnabled', [options || {}]);
  },

  setHeadingFollowMode: function (options) {
    return call('setHeadingFollowMode', [options || {}]);
  },

  setUserTrackingEnabled: function (options) {
    return call('setUserTrackingEnabled', [options || {}]);
  },

  moveToCurrentLocation: function (options) {
    return call('moveToCurrentLocation', [options || {}]);
  },

  downloadOfflineRegion: function (options) {
    return call('downloadOfflineRegion', [options || {}]);
  },

  downloadOfflineRegionForRect: function (options) {
    return call('downloadOfflineRegionForRect', [options || {}]);
  },

  showOfflineRegion: function (options) {
    return call('showOfflineRegion', [options || {}]);
  },

  deleteOfflineRegion: function (options) {
    return call('deleteOfflineRegion', [options || {}]);
  },

  onOfflineDownloadProgress: function (callback, errorCallback) {
    exec(callback, errorCallback || function () {}, SERVICE, 'registerOfflineDownloadProgressCallback', []);
  },

  setWaypointSelectionEnabled: function (options) {
    return call('setWaypointSelectionEnabled', [options || {}]);
  },

  onWaypointSelected: function (callback, errorCallback) {
    exec(callback, errorCallback || function () {}, SERVICE, 'registerWaypointSelectedCallback', []);
  },

  onMarkerClick: function (callback, errorCallback) {
    exec(callback, errorCallback || function () {}, SERVICE, 'registerMarkerClickCallback', []);
  },

  onMapTap: function (callback, errorCallback) {
    exec(
      callback,
      errorCallback || function () {},
      SERVICE,
      'registerMapTapCallback',
      []
    );
  },

  onBoundaryAreaChanged: function (callback, errorCallback) {
    exec(
      callback,
      errorCallback || function () {},
      SERVICE,
      'registerBoundaryAreaCallback',
      []
    );
  },

  onTrackingStatusChanged: function (callback, errorCallback) {
    exec(callback, errorCallback || function () {}, SERVICE, 'registerTrackingStatusCallback', []);
  },

  onLocationAccuracyChanged: function (callback, errorCallback) {
    exec(
      callback,
      errorCallback || function () {},
      SERVICE,
      'registerLocationAccuracyCallback',
      []
    );
  },

  onCompassChanged: function (callback, errorCallback) {
    exec(
      callback,
      errorCallback || function () {},
      SERVICE,
      'registerCompassCallback',
      []
    );
  },

  addMarker: function (options) {
    options = options || {};
    // Find markers and URL-only markers take the original synchronous path unchanged.
    if (!hasBinaryFields([options], 'imageBinary', 'imageMimeType')) {
      return call('addMarker', [options]);
    }
    var lifecycle = lifecycleGeneration;
    return markerImages().prepare(window, options, markerImages().ADD_KEYS, {},
      markerLabel(options.id, null)).then(function (prepared) {
      if (lifecycle !== lifecycleGeneration) throw cancellationError('addMarker');
      return call('addMarker', [prepared]);
    });
  },

  loadMarkers: function (markers, options) {
    options = options || {};
    options.markers = markers || [];
    var MAX_MARKERS = 10000;
    if (options.markers.length > MAX_MARKERS) {
      return Promise.reject(new Error('Too many markers: maximum allowed is ' + MAX_MARKERS + '.'));
    }
    if (!Array.isArray(options.markers) ||
        !hasBinaryFields(options.markers, 'ImageBinary', 'ImageMimeType')) {
      return call('loadMarkers', [options]);
    }
    return loadMarkersWithImages(options);
  },

  // Returns a Promise; optional callbacks also support Cordova/OutSystems callers.
  removeMarker: function (markerId, success, error) {
    var request = typeof markerId === 'string' && markerId.length > 0
      ? call('removeMarker', [{ id: markerId }])
      : Promise.reject(new Error('markerId must be a non-empty string.'));
    return request.then(success, error);
  },

  clearMarkers: function () {
    return call('clearMarkers', []);
  },

  loadBoundaries: function (boundaries, options) {
    if (typeof boundaries === 'string') {
      if (boundaries.length > 4 * 1024 * 1024) {
        return Promise.reject(new Error('Boundary input is too large.'));
      }

      try {
        boundaries = JSON.parse(boundaries);
      } catch (error) {
        return Promise.reject(new Error(
          'Invalid boundaries JSON: ' + (error.message || 'Invalid JSON.')
        ));
      }
    }

    if (!Array.isArray(boundaries) && boundaries &&
        typeof boundaries === 'object' && options === undefined) {
      options = boundaries;
      boundaries = options.boundaries;
    }

    if (!Array.isArray(boundaries)) {
      return Promise.reject(new Error(
        'Invalid payload. Expected a boundaries array.'
      ));
    }

    var MAX_BOUNDARIES = 1000;
    if (boundaries.length > MAX_BOUNDARIES) {
      return Promise.reject(new Error('Too many boundaries: maximum allowed is ' + MAX_BOUNDARIES + '.'));
    }

    var vertices = 0;
    for (var i = 0; i < boundaries.length; i++) {
      var geometry = boundaries[i] && boundaries[i].geometry;
      vertices += Array.isArray(geometry) ? geometry.length : 0;
      if (vertices > 20000) return Promise.reject(new Error('Too many boundary vertices: maximum total is 20000.'));
    }

    options = Object.assign({}, options || {});
    options.boundaries = boundaries;
    return call('loadBoundaries', [options]);
  },

  setBoundaryVisibility: function (options) {
    return call('setBoundaryVisibility', [options || {}]);
  },

  setLayerVisibility: function (options) {
    return call('setLayerVisibility', [options || {}]);
  },

  getLayerIds: function () {
    return call('getLayerIds', []);
  },

  clearBoundaries: function () {
    return call('clearBoundaries', []);
  },

  startBoundaryDrawing: function () {
    return call('startBoundaryDrawing', [{}]);
  },

  undoBoundaryPoint: function () {
    return call('undoBoundaryPoint', []);
  },

  saveBoundaryDrawing: function (options) {
    return call('saveBoundaryDrawing', [options || {}]);
  },

  cancelBoundaryDrawing: function () {
    return call('cancelBoundaryDrawing', []);
  },

  getCamera: function () {
    return call('getCamera', []);
  },

  getCurrentLocationAccuracy: function () {
    return call('getCurrentLocationAccuracy', []);
  },

  close: function () {
    lifecycleGeneration++;
    stopTouchRegions();
    cancelCallbacks();
    return call('close', []);
  },

  setMapStyle: function (options) {
    return call('setMapStyle', [options || {}]);
  },

  startPathTracking: function (options) {
    return call('startPathTracking', [options || {}]);
  },

  stopPathTracking: function () {
    return call('stopPathTracking', []);
  },

  pausePathTracking: function () {
    return call('pausePathTracking', []);
  },

  continuePathTracking: function () {
    return call('continuePathTracking', []);
  },

  loadPath: function (pathData, options) {
    options = options || {};
    if (typeof pathData === 'string') {
      if (pathData.length > 4 * 1024 * 1024) return Promise.reject(new Error('Path input is too large.'));
      try {
        pathData = JSON.parse(pathData);
      } catch (error) {
        return Promise.reject(new Error(
          'Failed to parse path data: ' + (error.message || 'Invalid JSON.')
        ));
      }
    }
    if (!pathData || !Array.isArray(pathData.points)) {
      return Promise.reject(new Error('Path data must have a "points" array.'));
    }
    if (pathData.points.length < 2 || pathData.points.length > 20000) {
      return Promise.reject(new Error('A path requires between 2 and 20000 points.'));
    }
    // Style and segments may sit on the path object itself, as documented; an
    // explicit second argument still wins.
    ['lineColor', 'lineWidth', 'lineOpacity', 'segments'].forEach(function (key) {
      if (options[key] === undefined && pathData[key] !== undefined) {
        options[key] = pathData[key];
      }
    });
    options.points = pathData.points;
    return call('loadPath', [options]);
  },

  clearPaths: function () {
    return call('clearPaths', []);
  },

  setPathVisibility: function (options) {
    return call('setPathVisibility', [options || {}]);
  }
};

if (typeof window !== 'undefined') {
  window.MapboxPlugin = window.MapboxPlugin || api;
}

module.exports = api;
