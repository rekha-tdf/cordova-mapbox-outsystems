 // Converts Waypoint marker binary images into image sources the native layer
// already renders (data:image/...;base64,...). Find markers are never touched:
// they keep passing imageUrl exactly as before.

// Native policy (MapboxSecurity): these types only, base64 only, 1 MiB of image data.
var NATIVE_TYPES = /^image\/(png|jpeg|gif|webp)$/;
var MAX_NATIVE_BYTES = 1024 * 1024;
// Larger camera photos are accepted because they are reduced before sending.
var MAX_SOURCE_BYTES = 10 * 1024 * 1024;
// Native code center-crops images into a circle and decodes them to <= 128 px, so a
// 192 px center square looks the same and keeps the bridge payload small.
var TARGET_SIZE = 192;
var DECODE_TIMEOUT_MS = 10000;
var MAX_PARALLEL_DECODES = 4;

var ADD_KEYS = { isFind: 'isFind', url: 'imageUrl', binary: 'imageBinary', mime: 'imageMimeType' };
var LOAD_KEYS = { isFind: 'IsFind', url: 'ImageUrl', binary: 'ImageBinary', mime: 'ImageMimeType' };

var warnedFind = false;

function warn(win, message, detail) {
  var c = (win && win.console) || (typeof console !== 'undefined' ? console : null);
  if (c && c.warn) c.warn(message, detail === undefined ? '' : detail);
}

function base64Encode(win, binary) {
  var encode = (win && win.btoa) || (typeof btoa === 'function' ? btoa : null);
  if (!encode) throw new Error('Base64 encoding is not available.');
  return encode(binary);
}

function base64Decode(win, text) {
  var decode = (win && win.atob) || (typeof atob === 'function' ? atob : null);
  if (!decode) throw new Error('Base64 decoding is not available.');
  return decode(text);
}

function bytesToBase64(win, bytes) {
  var chunks = [];
  for (var i = 0; i < bytes.length; i += 0x8000) {
    chunks.push(String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000)));
  }
  return base64Encode(win, chunks.join(''));
}

function normalizeMime(value) {
  if (typeof value !== 'string') return '';
  var mime = value.trim().toLowerCase();
  if (mime === 'image/jpg' || mime === 'jpg' || mime === 'jpeg') return 'image/jpeg';
  if (/^(png|gif|webp|bmp)$/.test(mime)) return 'image/' + mime;
  return /^image\/[a-z0-9.+-]+$/.test(mime) ? mime : '';
}

// Returns { base64, mime } or throws for input that is not binary data at all.
function readBinary(win, value, declaredMime) {
  var mime = normalizeMime(declaredMime);
  var base64;
  if (typeof value === 'string') {
    var text = value.trim();
    if (text.indexOf('data:') === 0) {
      var comma = text.indexOf(',');
      var header = comma < 0 ? '' : text.slice(0, comma + 1).toLowerCase();
      var match = /^data:([a-z0-9.+\/-]*)(;[^,]*)?;base64,$/.exec(header);
      if (!match) throw new Error('data URI must be base64 encoded.');
      mime = normalizeMime(match[1]) || mime;
      text = text.slice(comma + 1);
    }
    // OutSystems BinaryToBase64 output, possibly wrapped or URL-safe.
    base64 = text.replace(/\s+/g, '').replace(/-/g, '+').replace(/_/g, '/');
    var rest = base64.length % 4;
    if (rest === 1) throw new Error('not valid base64.');
    if (rest) base64 += rest === 2 ? '==' : '=';
    if (!/^[A-Za-z0-9+\/]+={0,2}$/.test(base64)) throw new Error('not valid base64.');
  } else if (value instanceof ArrayBuffer) {
    base64 = bytesToBase64(win, new Uint8Array(value));
  } else if (value && ArrayBuffer.isView(value)) {
    base64 = bytesToBase64(win, new Uint8Array(value.buffer, value.byteOffset, value.byteLength));
  } else if (Array.isArray(value)) {
    for (var i = 0; i < value.length; i++) {
      if (typeof value[i] !== 'number' || value[i] < 0 || value[i] > 255 || value[i] % 1) {
        throw new Error('byte arrays may contain only integers 0-255.');
      }
    }
    base64 = bytesToBase64(win, Uint8Array.from(value));
  } else {
    throw new Error('expected a base64 string, data URI, ArrayBuffer, Uint8Array or byte array.');
  }
  var padding = /==$/.test(base64) ? 2 : /=$/.test(base64) ? 1 : 0;
  var size = base64.length / 4 * 3 - padding;
  if (size <= 0) throw new Error('image data is empty.');
  if (size > MAX_SOURCE_BYTES) throw new Error('image data exceeds 10 MiB.');
  return { base64: base64, mime: sniffMime(win, base64) || mime, size: size };
}

// The file signature wins over a declared type (OutSystems often reports application/octet-stream).
function sniffMime(win, base64) {
  var head;
  try { head = base64Decode(win, base64.slice(0, 16)); } catch (e) { return ''; }
  function at(i) { return head.charCodeAt(i); }
  if (at(0) === 0x89 && head.slice(1, 4) === 'PNG') return 'image/png';
  if (at(0) === 0xFF && at(1) === 0xD8 && at(2) === 0xFF) return 'image/jpeg';
  if (head.slice(0, 4) === 'GIF8') return 'image/gif';
  if (head.slice(0, 4) === 'RIFF' && head.slice(8, 12) === 'WEBP') return 'image/webp';
  if (head.slice(0, 2) === 'BM') return 'image/bmp';
  return '';
}

var activeDecodes = 0;
var decodeQueue = [];

function withDecodeSlot(work) {
  return new Promise(function (resolve) {
    function run() {
      activeDecodes++;
      work().then(resolve, function () { resolve(null); }).then(function () {
        activeDecodes--;
        var next = decodeQueue.shift();
        if (next) next();
      });
    }
    if (activeDecodes < MAX_PARALLEL_DECODES) run(); else decodeQueue.push(run);
  });
}

// Decodes with the WebView's image decoder and redraws a center square as PNG/JPEG.
// Resolves null when the browser cannot decode it; never rejects.
function redraw(win, image) {
  return withDecodeSlot(function () {
    return new Promise(function (resolve) {
      if (!win || !win.document || typeof win.Image !== 'function') { resolve(null); return; }
      var img = new win.Image();
      var settled = false;
      var timer = win.setTimeout(function () { finish(null); }, DECODE_TIMEOUT_MS);
      function finish(result) {
        if (settled) return;
        settled = true;
        win.clearTimeout(timer);
        img.onload = img.onerror = null;
        resolve(result);
      }
      img.onerror = function () { finish(null); };
      img.onload = function () {
        try {
          var width = img.naturalWidth || img.width;
          var height = img.naturalHeight || img.height;
          if (!width || !height) { finish(null); return; }
          var side = Math.min(width, height);
          var out = Math.max(1, Math.min(side, TARGET_SIZE));
          var canvas = win.document.createElement('canvas');
          canvas.width = canvas.height = out;
          var context = canvas.getContext && canvas.getContext('2d');
          if (!context) { finish(null); return; }
          context.imageSmoothingEnabled = true;
          context.imageSmoothingQuality = 'high';
          context.drawImage(img, (width - side) / 2, (height - side) / 2, side, side, 0, 0, out, out);
          // JPEG stays JPEG (photos); everything else becomes PNG to keep transparency.
          var type = image.mime === 'image/jpeg' ? 'image/jpeg' : 'image/png';
          var data = canvas.toDataURL(type, 0.9);
          finish(/^data:image\/(png|jpeg);base64,[A-Za-z0-9+\/]+={0,2}$/.test(data) ? data : null);
        } catch (e) {
          finish(null);
        }
      };
      img.src = 'data:' + (image.mime || 'application/octet-stream') + ';base64,' + image.base64;
    });
  });
}

// Resolves a data URI the native layer accepts, or null when the image is unusable.
function toImageSource(win, image) {
  return redraw(win, image).then(function (data) {
    if (data) return data;
    // No canvas (or decode blocked): send the original if native can decode it itself.
    if (NATIVE_TYPES.test(image.mime) && image.size <= MAX_NATIVE_BYTES) {
      return 'data:' + image.mime + ';base64,' + image.base64;
    }
    return null;
  });
}

function hasField(marker, keys) {
  return Object.prototype.hasOwnProperty.call(marker, keys.binary) ||
    Object.prototype.hasOwnProperty.call(marker, keys.mime);
}

function isEmptyBinary(value) {
  if (value == null) return true;
  if (typeof value === 'string') return value.trim() === '';
  if (value instanceof ArrayBuffer || ArrayBuffer.isView(value)) return value.byteLength === 0;
  return Array.isArray(value) && value.length === 0;
}

function needsWork(markers, keys) {
  for (var i = 0; i < markers.length; i++) {
    var marker = markers[i];
    if (marker && typeof marker === 'object' && hasField(marker, keys)) return true;
  }
  return false;
}

// Returns a Promise for a copy of the marker with the binary converted into the url key.
// cache shares one conversion between markers that carry the same image.
function prepare(win, marker, keys, cache, label) {
  if (!marker || typeof marker !== 'object' || !hasField(marker, keys)) return Promise.resolve(marker);
  var copy = {};
  Object.keys(marker).forEach(function (key) { copy[key] = marker[key]; });
  var binary = copy[keys.binary];
  var mime = copy[keys.mime];
  delete copy[keys.binary];
  delete copy[keys.mime];
  // Shared OutSystems structures leave the unused URL null. Android reads JSON null as the
  // text "null" (an invalid image source that rejects the whole call); omit it instead.
  if (copy[keys.url] === null) delete copy[keys.url];

  if (copy[keys.isFind] === true) {
    // Find markers keep their URL behaviour; binary data is not used for them.
    if (!isEmptyBinary(binary) && !warnedFind) {
      warnedFind = true;
      warn(win, 'Mapbox: ' + keys.binary + ' is ignored for Find markers; use ' + keys.url + '.');
    }
    return Promise.resolve(copy);
  }
  if (isEmptyBinary(binary)) return Promise.resolve(copy);

  var image;
  try {
    image = readBinary(win, binary, mime);
  } catch (error) {
    return Promise.reject(new Error('Invalid ' + keys.binary + ' for marker ' + label + ': ' + error.message));
  }
  var cacheKey = image.mime + '|' + image.base64;
  var source = cache[cacheKey];
  if (!source) source = cache[cacheKey] = toImageSource(win, image);
  return source.then(function (data) {
    if (data) {
      copy[keys.url] = data;
    } else {
      // Same outcome as an image URL that fails to load: keep any URL, else a plain pin.
      warn(win, 'Mapbox: ' + keys.binary + ' for marker ' + label + ' is not a decodable image; ' +
        (copy[keys.url] ? 'using ' + keys.url + ' instead.' : 'showing a plain pin.'));
    }
    return copy;
  });
}

// Bridge size as native measures it: Android org.json and iOS JSONSerialization escape "/".
function nativeJsonSize(value) {
  var json = JSON.stringify(value);
  var slashes = json.match(/\//g);
  return json.length + (slashes ? slashes.length : 0);
}

module.exports = {
  ADD_KEYS: ADD_KEYS,
  LOAD_KEYS: LOAD_KEYS,
  needsWork: needsWork,
  prepare: prepare,
  nativeJsonSize: nativeJsonSize,
  readBinary: readBinary,
  _resetWarnings: function () { warnedFind = false; }
};
