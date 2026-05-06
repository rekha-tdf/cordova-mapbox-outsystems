var exec = require("cordova/exec");

function call(action, args, successCallback, errorCallback) {
  exec(successCallback, errorCallback, "Mapbox", action, args || []);
}

module.exports = {
  show: function (options, successCallback, errorCallback) {
    call("show", [options || {}], successCallback, errorCallback);
  },

  hide: function (options, successCallback, errorCallback) {
    call("hide", [options || {}], successCallback, errorCallback);
  },

  addMarkers: function (options, successCallback, errorCallback) {
    call("addMarkers", [options || []], successCallback, errorCallback);
  },

  removeAllMarkers: function (successCallback, errorCallback) {
    call("removeAllMarkers", [], successCallback, errorCallback);
  },

  addMarkerCallback: function (callback) {
    call("addMarkerCallback", [], callback, null);
  },

  animateCamera: function (options, successCallback, errorCallback) {
    call("animateCamera", [options || {}], successCallback, errorCallback);
  },

  addGeoJSON: function (options, successCallback, errorCallback) {
    call("addGeoJSON", [options || {}], successCallback, errorCallback);
  },

  setCenter: function (options, successCallback, errorCallback) {
    call("setCenter", [options || {}], successCallback, errorCallback);
  },

  getCenter: function (successCallback, errorCallback) {
    call("getCenter", [], successCallback, errorCallback);
  },

  setTilt: function (options, successCallback, errorCallback) {
    call("setTilt", [options || {}], successCallback, errorCallback);
  },

  getTilt: function (successCallback, errorCallback) {
    call("getTilt", [], successCallback, errorCallback);
  },

  getZoomLevel: function (successCallback, errorCallback) {
    call("getZoomLevel", [], successCallback, errorCallback);
  },

  setZoomLevel: function (options, successCallback, errorCallback) {
    call("setZoomLevel", [options || {}], successCallback, errorCallback);
  },

  getBounds: function (successCallback, errorCallback) {
    call("getBounds", [], successCallback, errorCallback);
  },

  setBounds: function (options, successCallback, errorCallback) {
    call("setBounds", [options || {}], successCallback, errorCallback);
  },

  addPolygon: function (options, successCallback, errorCallback) {
    call("addPolygon", [options || {}], successCallback, errorCallback);
  },

  convertCoordinate: function (options, successCallback, errorCallback) {
    call("convertCoordinate", [options || {}], successCallback, errorCallback);
  },

  convertPoint: function (options, successCallback, errorCallback) {
    call("convertPoint", [options || {}], successCallback, errorCallback);
  },

  onRegionWillChange: function (callback) {
    call("onRegionWillChange", [], callback, null);
  },

  onRegionIsChanging: function (callback) {
    call("onRegionIsChanging", [], callback, null);
  },

  onRegionDidChange: function (callback) {
    call("onRegionDidChange", [], callback, null);
  }
};
