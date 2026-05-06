package com.outsystems.plugins.mapbox;

import android.content.Context;
import android.content.res.Resources;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.CameraState;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.ScreenCoordinate;
import com.mapbox.maps.Style;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

public class MapboxPlugin extends CordovaPlugin {
  private static final String TOKEN_RESOURCE = "mapbox_access_token";

  private MapView mapView;
  private MapboxMap mapboxMap;
  private CallbackContext regionDidChangeCallback;

  @Override
  public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
    if ("show".equals(action)) {
      show(args.getJSONObject(0), callbackContext);
      return true;
    }
    if ("hide".equals(action)) {
      hide(callbackContext);
      return true;
    }
    if ("setCenter".equals(action)) {
      setCenter(args.getJSONObject(0), callbackContext);
      return true;
    }
    if ("getCenter".equals(action)) {
      getCenter(callbackContext);
      return true;
    }
    if ("setZoomLevel".equals(action)) {
      setZoomLevel(args.getJSONObject(0), callbackContext);
      return true;
    }
    if ("getZoomLevel".equals(action)) {
      getZoomLevel(callbackContext);
      return true;
    }
    if ("setTilt".equals(action)) {
      setTilt(args.getJSONObject(0), callbackContext);
      return true;
    }
    if ("getTilt".equals(action)) {
      getTilt(callbackContext);
      return true;
    }
    if ("animateCamera".equals(action)) {
      animateCamera(args.getJSONObject(0), callbackContext);
      return true;
    }
    if ("getBounds".equals(action)) {
      getBounds(callbackContext);
      return true;
    }
    if ("convertCoordinate".equals(action)) {
      convertCoordinate(args.getJSONObject(0), callbackContext);
      return true;
    }
    if ("convertPoint".equals(action)) {
      convertPoint(args.getJSONObject(0), callbackContext);
      return true;
    }
    if ("onRegionDidChange".equals(action)) {
      this.regionDidChangeCallback = callbackContext;
      PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
      result.setKeepCallback(true);
      callbackContext.sendPluginResult(result);
      return true;
    }
    if ("onRegionWillChange".equals(action) || "onRegionIsChanging".equals(action)
        || "addMarkers".equals(action) || "removeAllMarkers".equals(action)
        || "addMarkerCallback".equals(action) || "addPolygon".equals(action)
        || "addGeoJSON".equals(action) || "setBounds".equals(action)) {
      callbackContext.error(action + " is not implemented in this O11 starter. Implement with Mapbox v11 Annotation/Style APIs before production use.");
      return true;
    }
    return false;
  }

  private void show(final JSONObject options, final CallbackContext callbackContext) {
    cordova.getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        try {
          String accessToken = readAccessToken();
          if (accessToken == null || accessToken.length() == 0) {
            callbackContext.error("ACCESS_TOKEN is missing.");
            return;
          }

          Context context = cordova.getActivity();
          com.mapbox.maps.MapboxOptions.INSTANCE.setAccessToken(accessToken);

          JSONObject center = options.optJSONObject("center");
          double lat = center == null ? 0.0 : center.optDouble("lat", 0.0);
          double lng = center == null ? 0.0 : center.optDouble("lng", 0.0);
          double zoom = options.optDouble("zoomLevel", center == null ? 0.0 : 10.0);

          CameraOptions camera = new CameraOptions.Builder()
              .center(Point.fromLngLat(lng, lat))
              .zoom(zoom)
              .build();

          mapView = new MapView(context);
          mapboxMap = mapView.getMapboxMap();
          mapboxMap.setCamera(camera);
          mapboxMap.loadStyle(resolveStyle(options.optString("style", "streets")));

          JSONObject margins = options.optJSONObject("margins");
          int left = margins == null ? 0 : margins.optInt("left", 0);
          int top = margins == null ? 0 : margins.optInt("top", 0);
          int right = margins == null ? 0 : margins.optInt("right", 0);
          int bottom = margins == null ? 0 : margins.optInt("bottom", 0);

          int webViewWidth = webView.getView().getWidth();
          int webViewHeight = webView.getView().getHeight();
          FrameLayout layout = (FrameLayout) webView.getView().getParent();
          FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(webViewWidth - left - right, webViewHeight - top - bottom);
          params.setMargins(left, top, right, bottom);
          mapView.setLayoutParams(params);
          layout.addView(mapView);
          callbackContext.success();
        } catch (Throwable t) {
          callbackContext.error(t.getMessage());
        }
      }
    });
  }

  private void hide(final CallbackContext callbackContext) {
    cordova.getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (mapView != null) {
          ViewGroup parent = (ViewGroup) mapView.getParent();
          if (parent != null) {
            parent.removeView(mapView);
          }
          mapView.onDestroy();
          mapView = null;
          mapboxMap = null;
        }
        callbackContext.success();
      }
    });
  }

  private void setCenter(JSONObject options, CallbackContext callbackContext) {
    if (!ensureMap(callbackContext)) return;
    Point point = Point.fromLngLat(options.optDouble("lng"), options.optDouble("lat"));
    mapboxMap.setCamera(new CameraOptions.Builder().center(point).build());
    sendRegionDidChange();
    callbackContext.success();
  }

  private void getCenter(CallbackContext callbackContext) {
    if (!ensureMap(callbackContext)) return;
    Point center = mapboxMap.getCameraState().getCenter();
    JSONObject json = new JSONObject();
    try {
      json.put("lat", center.latitude());
      json.put("lng", center.longitude());
      callbackContext.success(json);
    } catch (JSONException e) {
      callbackContext.error(e.getMessage());
    }
  }

  private void setZoomLevel(JSONObject options, CallbackContext callbackContext) {
    if (!ensureMap(callbackContext)) return;
    mapboxMap.setCamera(new CameraOptions.Builder().zoom(options.optDouble("level")).build());
    sendRegionDidChange();
    callbackContext.success();
  }

  private void getZoomLevel(CallbackContext callbackContext) {
    if (!ensureMap(callbackContext)) return;
    callbackContext.success(String.valueOf(mapboxMap.getCameraState().getZoom()));
  }

  private void setTilt(JSONObject options, CallbackContext callbackContext) {
    if (!ensureMap(callbackContext)) return;
    mapboxMap.setCamera(new CameraOptions.Builder().pitch(options.optDouble("pitch", 0.0)).build());
    sendRegionDidChange();
    callbackContext.success();
  }

  private void getTilt(CallbackContext callbackContext) {
    if (!ensureMap(callbackContext)) return;
    callbackContext.success(String.valueOf(mapboxMap.getCameraState().getPitch()));
  }

  private void animateCamera(JSONObject options, CallbackContext callbackContext) {
    if (!ensureMap(callbackContext)) return;
    JSONObject target = options.optJSONObject("target");
    CameraOptions.Builder builder = new CameraOptions.Builder();
    if (target != null) {
      builder.center(Point.fromLngLat(target.optDouble("lng"), target.optDouble("lat")));
    }
    if (options.has("zoomLevel")) builder.zoom(options.optDouble("zoomLevel"));
    if (options.has("tilt")) builder.pitch(options.optDouble("tilt"));
    if (options.has("bearing")) builder.bearing(options.optDouble("bearing"));
    mapboxMap.setCamera(builder.build());
    sendRegionDidChange();
    callbackContext.success();
  }

  private void getBounds(CallbackContext callbackContext) {
    if (!ensureMap(callbackContext)) return;
    try {
      Point sw = mapboxMap.coordinateForPixel(new ScreenCoordinate(0.0, mapView.getHeight()));
      Point ne = mapboxMap.coordinateForPixel(new ScreenCoordinate(mapView.getWidth(), 0.0));
      JSONObject json = new JSONObject();
      json.put("sw_lat", sw.latitude());
      json.put("sw_lng", sw.longitude());
      json.put("ne_lat", ne.latitude());
      json.put("ne_lng", ne.longitude());
      callbackContext.success(json);
    } catch (JSONException e) {
      callbackContext.error(e.getMessage());
    }
  }

  private void convertCoordinate(JSONObject options, CallbackContext callbackContext) {
    if (!ensureMap(callbackContext)) return;
    try {
      ScreenCoordinate point = mapboxMap.pixelForCoordinate(Point.fromLngLat(options.optDouble("lng"), options.optDouble("lat")));
      JSONObject json = new JSONObject();
      json.put("x", point.getX());
      json.put("y", point.getY());
      callbackContext.success(json);
    } catch (JSONException e) {
      callbackContext.error(e.getMessage());
    }
  }

  private void convertPoint(JSONObject options, CallbackContext callbackContext) {
    if (!ensureMap(callbackContext)) return;
    try {
      Point point = mapboxMap.coordinateForPixel(new ScreenCoordinate(options.optDouble("x"), options.optDouble("y")));
      JSONObject json = new JSONObject();
      json.put("lat", point.latitude());
      json.put("lng", point.longitude());
      callbackContext.success(json);
    } catch (JSONException e) {
      callbackContext.error(e.getMessage());
    }
  }

  private boolean ensureMap(CallbackContext callbackContext) {
    if (mapView == null || mapboxMap == null) {
      callbackContext.error("Mapbox.show must be called first.");
      return false;
    }
    return true;
  }

  private String readAccessToken() {
    try {
      Resources resources = cordova.getActivity().getResources();
      int id = resources.getIdentifier(TOKEN_RESOURCE, "string", cordova.getActivity().getPackageName());
      return id == 0 ? null : resources.getString(id);
    } catch (Resources.NotFoundException e) {
      return null;
    }
  }

  private String resolveStyle(String requested) {
    if ("light".equalsIgnoreCase(requested)) return Style.LIGHT;
    if ("dark".equalsIgnoreCase(requested)) return Style.DARK;
    if ("satellite".equalsIgnoreCase(requested)) return Style.SATELLITE;
    if ("hybrid".equalsIgnoreCase(requested)) return Style.SATELLITE_STREETS;
    if ("streets".equalsIgnoreCase(requested)) return Style.MAPBOX_STREETS;
    if (requested != null && requested.startsWith("mapbox://")) return requested;
    return Style.MAPBOX_STREETS;
  }

  private void sendRegionDidChange() {
    if (regionDidChangeCallback == null || mapboxMap == null) return;
    try {
      CameraState camera = mapboxMap.getCameraState();
      JSONObject json = new JSONObject();
      json.put("lat", camera.getCenter().latitude());
      json.put("lng", camera.getCenter().longitude());
      json.put("camPitch", camera.getPitch());
      json.put("camHeading", camera.getBearing());
      PluginResult result = new PluginResult(PluginResult.Status.OK, json);
      result.setKeepCallback(true);
      regionDidChangeCallback.sendPluginResult(result);
    } catch (JSONException ignored) {
    }
  }

  @Override
  public void onPause(boolean multitasking) {
    if (mapView != null) mapView.onStop();
  }

  @Override
  public void onResume(boolean multitasking) {
    if (mapView != null) mapView.onStart();
  }

  @Override
  public void onDestroy() {
    if (mapView != null) mapView.onDestroy();
  }
}
