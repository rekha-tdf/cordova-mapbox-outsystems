var exec = require('cordova/exec');

var SERVICE = 'MapboxPlugin';

function call(action, args) {
  return new Promise(function (resolve, reject) {
    exec(resolve, reject, SERVICE, action, args || []);
  });
}

var api = {
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
    return call('initialize', [options || {}]);
  },

  ping: function () {
    return call('ping', []);
  },

  setCamera: function (options) {
    return call('setCamera', [options || {}]);
  },

  setViewport: function (options) {
    return call('setViewport', [options || {}]);
  },

  setTouchableRects: function (rects) {
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

  addMarker: function (options) {
    return call('addMarker', [options || {}]);
  },

  loadMarkers: function (markers, options) {
    options = options || {};
    options.markers = markers || [];
    return call('loadMarkers', [options]);
  },

  removeMarker: function (id) {
    return call('removeMarker', [{ id: id }]);
  },

  clearMarkers: function () {
    return call('clearMarkers', []);
  },

  getCamera: function () {
    return call('getCamera', []);
  },

  close: function () {
    return call('close', []);
  }
};

if (typeof window !== 'undefined') {
  window.MapboxPlugin = window.MapboxPlugin || api;
}

module.exports = api;
