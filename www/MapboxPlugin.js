var exec = require('cordova/exec');

var SERVICE = 'MapboxPlugin';

function call(action, args) {
  return new Promise(function (resolve, reject) {
    exec(resolve, reject, SERVICE, action, args || []);
  });
}

module.exports = {
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

  addMarker: function (options) {
    return call('addMarker', [options || {}]);
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
