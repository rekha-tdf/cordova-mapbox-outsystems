package com.outsystems.mapbox;

import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import com.mapbox.common.MapboxOptions;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.Style;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

public class MapboxPluginEntry extends CordovaPlugin {
    private MapView mapView;
    private FrameLayout rootView;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        JSONObject options = args.optJSONObject(0);
        if (options == null) {
            options = new JSONObject();
        }

        switch (action) {
            case "ping":
                try {
                    JSONObject result = new JSONObject();
                    result.put("status", "ok");
                    result.put("service", "MapboxPlugin");
                    result.put("class", "MapboxPluginEntry");
                    callbackContext.success(result);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
                return true;
            case "initialize":
                initialize(options, callbackContext);
                return true;
            case "setViewport":
                setViewport(options, callbackContext);
                return true;
            case "setCamera":
                setCamera(options, callbackContext);
                return true;
            case "getCamera":
                getCamera(callbackContext);
                return true;
            case "addMarker":
                callbackContext.error("Markers are temporarily disabled in the Java entry build.");
                return true;
            case "removeMarker":
            case "clearMarkers":
                callbackContext.success();
                return true;
            case "close":
                close(callbackContext);
                return true;
            default:
                return false;
        }
    }

    private void initialize(JSONObject options, CallbackContext callback) {
        cordova.getActivity().runOnUiThread(() -> {
            try {
                String token = options.optString("token", "");
                if (token.trim().isEmpty()) {
                    token = preferences.getString("MAPBOX_ACCESS_TOKEN", "");
                }
                if ("__MAPBOX_ACCESS_TOKEN_NOT_SET__".equals(token)) {
                    token = "";
                }
                if (token.trim().isEmpty()) {
                    callback.error("Mapbox access token is required.");
                    return;
                }

                MapboxOptions.setAccessToken(token);

                String styleUrl = options.optString("styleUrl", Style.MAPBOX_STREETS);
                double latitude = options.optDouble("latitude", 0.0);
                double longitude = options.optDouble("longitude", 0.0);
                double zoom = options.optDouble("zoom", 12.0);

                closeInternal();

                ViewGroup decor = (ViewGroup) cordova.getActivity().getWindow().getDecorView();

                rootView = new FrameLayout(cordova.getActivity());
                rootView.setBackgroundColor(Color.WHITE);
                rootView.setLayoutParams(layoutParamsFromOptions(options));

                mapView = new MapView(cordova.getActivity());
                mapView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                ));

                rootView.addView(mapView);

                if (!options.optBoolean("inline", false)) {
                    Button closeButton = new Button(cordova.getActivity());
                    closeButton.setText("Close");
                    closeButton.setOnClickListener(view -> closeInternal());

                    FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.END
                    );
                    buttonParams.topMargin = 48;
                    buttonParams.rightMargin = 24;
                    closeButton.setLayoutParams(buttonParams);
                    rootView.addView(closeButton);
                }

                decor.addView(rootView);

                mapView.getMapboxMap().setCamera(new CameraOptions.Builder()
                    .center(Point.fromLngLat(longitude, latitude))
                    .zoom(zoom)
                    .build());

                mapView.getMapboxMap().loadStyle(styleUrl);

                JSONObject result = new JSONObject();
                result.put("status", "initialized");
                callback.success(result);
            } catch (Exception e) {
                callback.error(e.getMessage() == null ? "Failed to initialize Mapbox map." : e.getMessage());
            }
        });
    }

    private void setViewport(JSONObject options, CallbackContext callback) {
        cordova.getActivity().runOnUiThread(() -> {
            if (rootView == null) {
                callback.error("Map is not initialized.");
                return;
            }

            rootView.setLayoutParams(layoutParamsFromOptions(options));
            rootView.requestLayout();
            callback.success();
        });
    }

    private void setCamera(JSONObject options, CallbackContext callback) {
        cordova.getActivity().runOnUiThread(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized.");
                return;
            }

            mapView.getMapboxMap().setCamera(new CameraOptions.Builder()
                .center(Point.fromLngLat(
                    options.optDouble("longitude", 0.0),
                    options.optDouble("latitude", 0.0)
                ))
                .zoom(options.optDouble("zoom", mapView.getMapboxMap().getCameraState().getZoom()))
                .bearing(options.optDouble("bearing", mapView.getMapboxMap().getCameraState().getBearing()))
                .pitch(options.optDouble("pitch", mapView.getMapboxMap().getCameraState().getPitch()))
                .build());

            callback.success();
        });
    }

    private void getCamera(CallbackContext callback) {
        cordova.getActivity().runOnUiThread(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized.");
                return;
            }

            try {
                JSONObject result = new JSONObject();
                result.put("latitude", mapView.getMapboxMap().getCameraState().getCenter().latitude());
                result.put("longitude", mapView.getMapboxMap().getCameraState().getCenter().longitude());
                result.put("zoom", mapView.getMapboxMap().getCameraState().getZoom());
                result.put("bearing", mapView.getMapboxMap().getCameraState().getBearing());
                result.put("pitch", mapView.getMapboxMap().getCameraState().getPitch());
                callback.success(result);
            } catch (Exception e) {
                callback.error(e.getMessage());
            }
        });
    }

    private void close(CallbackContext callback) {
        cordova.getActivity().runOnUiThread(() -> {
            closeInternal();
            callback.success();
        });
    }

    private void closeInternal() {
        if (mapView != null) {
            mapView.onStop();
            mapView.onDestroy();
        }

        if (rootView != null && rootView.getParent() instanceof ViewGroup) {
            ((ViewGroup) rootView.getParent()).removeView(rootView);
        }

        mapView = null;
        rootView = null;
    }

    private FrameLayout.LayoutParams layoutParamsFromOptions(JSONObject options) {
        if (!options.optBoolean("inline", false)) {
            return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            );
        }

        float density = cordova.getActivity().getResources().getDisplayMetrics().density;
        int width = Math.max(1, (int) (options.optDouble("width", 1.0) * density));
        int height = Math.max(1, (int) (options.optDouble("height", 1.0) * density));
        int x = (int) (options.optDouble("x", 0.0) * density);
        int y = (int) (options.optDouble("y", 0.0) * density);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = x;
        params.topMargin = y;
        return params;
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        if (mapView != null) {
            mapView.onStart();
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        if (mapView != null) {
            mapView.onStop();
        }
        super.onPause(multitasking);
    }

    @Override
    public void onDestroy() {
        closeInternal();
        super.onDestroy();
    }
}
