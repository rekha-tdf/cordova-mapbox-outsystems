package com.outsystems.mapbox;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mapbox.common.MapboxOptions;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.ScreenCoordinate;
import com.mapbox.maps.Style;
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor;
import com.mapbox.maps.plugin.PuckBearing;
import com.mapbox.maps.plugin.Plugin;
import com.mapbox.maps.plugin.annotation.AnnotationPlugin;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManagerKt;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions;
import com.mapbox.maps.plugin.gestures.GesturesPlugin;
import com.mapbox.maps.plugin.gestures.OnMapClickListener;
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;

public class MapboxPluginEntry extends CordovaPlugin {
    private MapView mapView;
    private FrameLayout rootView;
    private final List<TouchRect> touchableRects = new ArrayList<>();
    private SensorManager sensorManager;
    private SensorEventListener headingSensorListener;
    private final float[] headingRotationMatrix = new float[9];
    private final float[] headingOrientationValues = new float[3];
    private double lastHeadingBearing = -1.0;
    private long lastHeadingUpdateMs = 0L;
    private LocationManager locationManager;
    private LocationListener userTrackingListener;
    private long lastUserTrackingUpdateMs = 0L;
    private PointAnnotationManager pointAnnotationManager;
    private final Map<String, String> markerRecordIds = new HashMap<>();
    private final Map<String, PointAnnotation> markerAnnotationsByRecordId = new HashMap<>();
    private final Map<String, Point> markerPointsByRecordId = new HashMap<>();
    private CallbackContext waypointSelectedCallback;
    private CallbackContext markerClickCallback;
    private boolean waypointSelectionEnabled = false;
    private boolean autoAddWaypointMarker = false;
    private OnMapClickListener mapClickListener;
    private float rawTapDownX = 0.0f;
    private float rawTapDownY = 0.0f;
    private long rawTapDownMs = 0L;
    private long lastMapSelectionCallbackMs = 0L;

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
            case "setTouchableRects":
                setTouchableRects(args, callbackContext);
                return true;
            case "setCamera":
                setCamera(options, callbackContext);
                return true;
            case "enableUserLocation":
                enableUserLocation(callbackContext);
                return true;
            case "setDeviceHeadingEnabled":
                setDeviceHeadingEnabled(options, callbackContext);
                return true;
            case "setHeadingFollowMode":
                setHeadingFollowMode(options, callbackContext);
                return true;
            case "setUserTrackingEnabled":
                setUserTrackingEnabled(options, callbackContext);
                return true;
            case "setWaypointSelectionEnabled":
                setWaypointSelectionEnabled(options, callbackContext);
                return true;
            case "registerWaypointSelectedCallback":
                registerWaypointSelectedCallback(callbackContext);
                return true;
            case "registerMarkerClickCallback":
                registerMarkerClickCallback(callbackContext);
                return true;
            case "getCamera":
                getCamera(callbackContext);
                return true;
            case "addMarker":
                addMarker(options, callbackContext);
                return true;
            case "loadMarkers":
                loadMarkers(options, callbackContext);
                return true;
            case "removeMarker":
                removeMarker(options, callbackContext);
                return true;
            case "clearMarkers":
                clearMarkers(callbackContext);
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

                boolean behindWebView = options.optBoolean("behindWebView", false);

                rootView = new FrameLayout(cordova.getActivity());
                rootView.setBackgroundColor(behindWebView ? Color.TRANSPARENT : Color.WHITE);
                rootView.setLayoutParams(layoutParamsFromOptions(options));

                mapView = new MapView(cordova.getActivity());
                mapView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                ));

                rootView.addView(mapView);
                mapView.onStart();
                installMapClickListener();

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

                if (behindWebView) {
                    addBehindWebView(rootView);
                } else {
                    ViewGroup decor = (ViewGroup) cordova.getActivity().getWindow().getDecorView();
                    decor.addView(rootView);
                }

                mapView.getMapboxMap().setCamera(new CameraOptions.Builder()
                    .center(Point.fromLngLat(longitude, latitude))
                    .zoom(zoom)
                    .build());

                mapView.getMapboxMap().loadStyle(styleUrl);

                JSONObject result = new JSONObject();
                result.put("status", "initialized");
                callback.success(result);
            } catch (Throwable e) {
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

    private void setTouchableRects(JSONArray args, CallbackContext callback) {
        cordova.getActivity().runOnUiThread(() -> {
            touchableRects.clear();

            JSONArray rects = args.optJSONArray(0);
            if (rects == null) {
                callback.success();
                return;
            }

            for (int i = 0; i < rects.length(); i++) {
                JSONObject rect = rects.optJSONObject(i);
                if (rect == null) {
                    continue;
                }

                touchableRects.add(new TouchRect(
                    rect.optDouble("x", 0.0),
                    rect.optDouble("y", 0.0),
                    rect.optDouble("width", 0.0),
                    rect.optDouble("height", 0.0)
                ));
            }

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

    private void enableUserLocation(CallbackContext callback) {
        cordova.getActivity().runOnUiThread(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized.");
                return;
            }

            boolean hasFineLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION);
            boolean hasCoarseLocation = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION);

            if (!hasFineLocation && !hasCoarseLocation) {
                callback.error("Location permission is not granted.");
                return;
            }

            LocationComponentPlugin location =
                mapView.getPlugin(Plugin.MAPBOX_LOCATION_COMPONENT_PLUGIN_ID);

            if (location == null) {
                callback.error("Location component is not available.");
                return;
            }

            location.setEnabled(true);
            location.setPuckBearingEnabled(true);

            callback.success();
        });
    }

    private boolean hasPermission(String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        return cordova.getActivity().checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationPermission() {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private void setDeviceHeadingEnabled(JSONObject options, CallbackContext callback) {
        cordova.getActivity().runOnUiThread(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized.");
                return;
            }

            boolean hasFineLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION);
            boolean hasCoarseLocation = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION);

            if (!hasFineLocation && !hasCoarseLocation) {
                callback.error("Location permission is not granted.");
                return;
            }

            LocationComponentPlugin location =
                mapView.getPlugin(Plugin.MAPBOX_LOCATION_COMPONENT_PLUGIN_ID);

            if (location == null) {
                callback.error("Location component is not available.");
                return;
            }

            boolean enabled = options.optBoolean("enabled", true);
            location.setEnabled(true);
            location.setPuckBearing(PuckBearing.HEADING);
            location.setPuckBearingEnabled(enabled);

            callback.success();
        });
    }

    private void setHeadingFollowMode(JSONObject options, CallbackContext callback) {
        cordova.getActivity().runOnUiThread(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized.");
                return;
            }

            boolean enabled = options.optBoolean("enabled", true);

            if (!enabled) {
                stopHeadingFollowMode();
                callback.success();
                return;
            }

            startHeadingFollowMode(callback);
        });
    }

    private void startHeadingFollowMode(CallbackContext callback) {
        stopHeadingFollowMode();

        sensorManager = (SensorManager) cordova.getActivity().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) {
            callback.error("Device sensor manager is not available.");
            return;
        }

        Sensor rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rotationVectorSensor == null) {
            callback.error("Device rotation sensor is not available.");
            return;
        }

        headingSensorListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (mapView == null || event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) {
                    return;
                }

                SensorManager.getRotationMatrixFromVector(headingRotationMatrix, event.values);
                SensorManager.getOrientation(headingRotationMatrix, headingOrientationValues);

                double bearing = Math.toDegrees(headingOrientationValues[0]);
                if (bearing < 0) {
                    bearing += 360.0;
                }

                long now = System.currentTimeMillis();
                if (now - lastHeadingUpdateMs < 80L) {
                    return;
                }

                if (lastHeadingBearing >= 0.0) {
                    double diff = Math.abs(bearing - lastHeadingBearing);
                    diff = Math.min(diff, 360.0 - diff);

                    if (diff < 1.5) {
                        return;
                    }

                    bearing = lastHeadingBearing + shortestBearingDelta(lastHeadingBearing, bearing) * 0.25;
                    if (bearing < 0.0) {
                        bearing += 360.0;
                    } else if (bearing >= 360.0) {
                        bearing -= 360.0;
                    }
                }

                lastHeadingBearing = bearing;
                lastHeadingUpdateMs = now;

                final double cameraBearing = bearing;
                cordova.getActivity().runOnUiThread(() -> {
                    if (mapView != null) {
                        mapView.getMapboxMap().setCamera(new CameraOptions.Builder()
                            .bearing(cameraBearing)
                            .build());
                    }
                });
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        sensorManager.registerListener(
            headingSensorListener,
            rotationVectorSensor,
            SensorManager.SENSOR_DELAY_UI
        );

        callback.success();
    }

    private void stopHeadingFollowMode() {
        if (sensorManager != null && headingSensorListener != null) {
            sensorManager.unregisterListener(headingSensorListener);
        }

        headingSensorListener = null;
        lastHeadingBearing = -1.0;
        lastHeadingUpdateMs = 0L;
    }

    private double shortestBearingDelta(double from, double to) {
        return (to - from + 540.0) % 360.0 - 180.0;
    }

    private void setUserTrackingEnabled(JSONObject options, CallbackContext callback) {
        cordova.getActivity().runOnUiThread(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized.");
                return;
            }

            boolean enabled = options.optBoolean("enabled", true);
            if (!enabled) {
                stopUserTracking();
                callback.success();
                return;
            }

            startUserTracking(callback);
        });
    }

    private void startUserTracking(CallbackContext callback) {
        if (!hasLocationPermission()) {
            callback.error("Location permission is not granted.");
            return;
        }

        stopUserTracking();

        locationManager = (LocationManager) cordova.getActivity().getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            callback.error("Device location manager is not available.");
            return;
        }

        userTrackingListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (mapView == null || location == null) {
                    return;
                }

                long now = System.currentTimeMillis();
                if (now - lastUserTrackingUpdateMs < 500L) {
                    return;
                }
                lastUserTrackingUpdateMs = now;

                final double latitude = location.getLatitude();
                final double longitude = location.getLongitude();
                cordova.getActivity().runOnUiThread(() -> {
                    if (mapView != null) {
                        mapView.getMapboxMap().setCamera(new CameraOptions.Builder()
                            .center(Point.fromLngLat(longitude, latitude))
                            .build());
                    }
                });
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };

        try {
            boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (gpsEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    500L,
                    1.0f,
                    userTrackingListener
                );
            }

            if (networkEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    500L,
                    1.0f,
                    userTrackingListener
                );
            }

            if (!gpsEnabled && !networkEnabled) {
                stopUserTracking();
                callback.error("Location provider is not enabled.");
                return;
            }

            callback.success();
        } catch (SecurityException e) {
            stopUserTracking();
            callback.error("Location permission is not granted.");
        }
    }

    private void stopUserTracking() {
        if (locationManager != null && userTrackingListener != null) {
            try {
                locationManager.removeUpdates(userTrackingListener);
            } catch (SecurityException ignored) {
            }
        }

        userTrackingListener = null;
        lastUserTrackingUpdateMs = 0L;
    }

    private void setWaypointSelectionEnabled(JSONObject options, CallbackContext callback) {
        installMapClickListener();
        waypointSelectionEnabled = options.optBoolean("enabled", true);
        autoAddWaypointMarker = options.optBoolean("autoAddMarker", false);
        callback.success();
    }

    private void registerWaypointSelectedCallback(CallbackContext callback) {
        waypointSelectedCallback = callback;
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        callback.sendPluginResult(result);
    }

    private void registerMarkerClickCallback(CallbackContext callback) {
        markerClickCallback = callback;
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        callback.sendPluginResult(result);
    }

    private void installMapClickListener() {
        if (mapView == null || mapClickListener != null) {
            return;
        }

        GesturesPlugin gestures = mapView.getPlugin(Plugin.MAPBOX_GESTURES_PLUGIN_ID);
        if (gestures == null) {
            return;
        }

        mapClickListener = point -> {
            return handleMapPointSelected(point);
        };

        gestures.addOnMapClickListener(mapClickListener);
    }

    private boolean handleMapPointSelected(Point point) {
        long now = System.currentTimeMillis();
        if (now - lastMapSelectionCallbackMs < 400L) {
            return true;
        }

        if (sendMarkerClickIfNear(point)) {
            lastMapSelectionCallbackMs = now;
            return true;
        }

        if (!waypointSelectionEnabled) {
            return false;
        }

        String id = "";
        if (autoAddWaypointMarker) {
            id = String.valueOf(System.currentTimeMillis());
            addMarkerInternal(id, point.latitude(), point.longitude());
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("type", "waypointSelected");
            payload.put("id", id);
            payload.put("latitude", point.latitude());
            payload.put("longitude", point.longitude());
            sendKeepCallback(waypointSelectedCallback, payload);
            lastMapSelectionCallbackMs = now;
        } catch (Exception ignored) {
        }

        return true;
    }

    private void handleRawTouchForTap(MotionEvent event) {
        if (mapView == null) {
            return;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            rawTapDownX = event.getX();
            rawTapDownY = event.getY();
            rawTapDownMs = System.currentTimeMillis();
            return;
        }

        if (event.getAction() != MotionEvent.ACTION_UP) {
            return;
        }

        float dx = Math.abs(event.getX() - rawTapDownX);
        float dy = Math.abs(event.getY() - rawTapDownY);
        long duration = System.currentTimeMillis() - rawTapDownMs;

        if (dx > 24.0f || dy > 24.0f || duration > 600L) {
            return;
        }

        Point point = mapView.getMapboxMap().coordinateForPixel(
            new ScreenCoordinate(event.getX(), event.getY())
        );
        handleMapPointSelected(point);
    }

    private boolean ensurePointAnnotationManager() {
        if (mapView == null) {
            return false;
        }

        if (pointAnnotationManager != null) {
            return true;
        }

        AnnotationPlugin annotationPlugin = mapView.getPlugin(Plugin.MAPBOX_ANNOTATION_PLUGIN_ID);
        if (annotationPlugin == null) {
            return false;
        }

        pointAnnotationManager = PointAnnotationManagerKt.createPointAnnotationManager(annotationPlugin, null);
        pointAnnotationManager.addClickListener(annotation -> {
            String recordId = markerRecordIds.get(annotation.getId());
            if (recordId == null) {
                recordId = "";
            }

            try {
                JSONObject payload = new JSONObject();
                payload.put("type", "markerClicked");
                payload.put("id", recordId);
                payload.put("latitude", annotation.getPoint().latitude());
                payload.put("longitude", annotation.getPoint().longitude());
                sendKeepCallback(markerClickCallback, payload);
            } catch (Exception ignored) {
            }

            return true;
        });

        return true;
    }

    private boolean sendMarkerClickIfNear(Point point) {
        if (markerPointsByRecordId.isEmpty()) {
            return false;
        }

        String nearestId = "";
        Point nearestPoint = null;
        float nearestDistance = Float.MAX_VALUE;
        float[] distance = new float[1];

        for (Map.Entry<String, Point> entry : markerPointsByRecordId.entrySet()) {
            Point markerPoint = entry.getValue();
            Location.distanceBetween(
                point.latitude(),
                point.longitude(),
                markerPoint.latitude(),
                markerPoint.longitude(),
                distance
            );

            if (distance[0] < nearestDistance) {
                nearestDistance = distance[0];
                nearestId = entry.getKey();
                nearestPoint = markerPoint;
            }
        }

        if (nearestPoint == null || nearestDistance > 150.0f) {
            return false;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("type", "markerClicked");
            payload.put("id", nearestId);
            payload.put("latitude", nearestPoint.latitude());
            payload.put("longitude", nearestPoint.longitude());
            sendKeepCallback(markerClickCallback, payload);
        } catch (Exception ignored) {
        }

        return true;
    }

    private void addMarker(JSONObject options, CallbackContext callback) {
        cordova.getActivity().runOnUiThread(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized.");
                return;
            }

            String id = options.optString("id", String.valueOf(System.currentTimeMillis()));
            double latitude = options.optDouble("latitude", 0.0);
            double longitude = options.optDouble("longitude", 0.0);

            if (!addMarkerInternal(id, latitude, longitude)) {
                callback.error("Marker manager is not available.");
                return;
            }

            try {
                JSONObject result = new JSONObject();
                result.put("id", id);
                callback.success(result);
            } catch (Exception e) {
                callback.error(e.getMessage());
            }
        });
    }

    private void loadMarkers(JSONObject options, CallbackContext callback) {
        cordova.getActivity().runOnUiThread(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized.");
                return;
            }

            if (!ensurePointAnnotationManager()) {
                callback.error("Marker manager is not available.");
                return;
            }

            if (options.optBoolean("replace", true)) {
                clearMarkersInternal();
            }

            JSONArray markers = options.optJSONArray("markers");
            if (markers == null) {
                callback.success();
                return;
            }

            for (int i = 0; i < markers.length(); i++) {
                JSONObject marker = markers.optJSONObject(i);
                if (marker == null) {
                    continue;
                }

                addMarkerInternal(
                    marker.optString("id", String.valueOf(i)),
                    marker.optDouble("latitude", 0.0),
                    marker.optDouble("longitude", 0.0)
                );
            }

            callback.success();
        });
    }

    private boolean addMarkerInternal(String id, double latitude, double longitude) {
        if (!ensurePointAnnotationManager()) {
            return false;
        }

        removeMarkerInternal(id);

        PointAnnotationOptions markerOptions = new PointAnnotationOptions()
            .withPoint(Point.fromLngLat(longitude, latitude))
            .withIconImage(createWaypointMarkerBitmap())
            .withIconAnchor(IconAnchor.BOTTOM)
            .withIconSize(1.0);

        PointAnnotation annotation = pointAnnotationManager.create(markerOptions);
        markerRecordIds.put(annotation.getId(), id);
        markerAnnotationsByRecordId.put(id, annotation);
        markerPointsByRecordId.put(id, Point.fromLngLat(longitude, latitude));
        return true;
    }

    private void removeMarker(JSONObject options, CallbackContext callback) {
        cordova.getActivity().runOnUiThread(() -> {
            removeMarkerInternal(options.optString("id", ""));
            callback.success();
        });
    }

    private void removeMarkerInternal(String id) {
        if (pointAnnotationManager == null || id == null || id.isEmpty()) {
            return;
        }

        PointAnnotation annotation = markerAnnotationsByRecordId.remove(id);
        if (annotation != null) {
            pointAnnotationManager.delete(annotation);
            markerRecordIds.remove(annotation.getId());
        }
        markerPointsByRecordId.remove(id);
    }

    private void clearMarkers(CallbackContext callback) {
        cordova.getActivity().runOnUiThread(() -> {
            clearMarkersInternal();
            callback.success();
        });
    }

    private void clearMarkersInternal() {
        if (pointAnnotationManager != null) {
            List<PointAnnotation> annotations = new ArrayList<>(pointAnnotationManager.getAnnotations());
            for (PointAnnotation annotation : annotations) {
                pointAnnotationManager.delete(annotation);
            }
        }

        markerRecordIds.clear();
        markerAnnotationsByRecordId.clear();
        markerPointsByRecordId.clear();
    }

    private Bitmap createWaypointMarkerBitmap() {
        int width = 72;
        int height = 96;
        float centerX = width / 2.0f;
        float circleRadius = 24.0f;
        float circleCenterY = 30.0f;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.argb(70, 0, 0, 0));
        canvas.drawOval(centerX - 18.0f, height - 14.0f, centerX + 18.0f, height - 6.0f, shadowPaint);

        Path pinPath = new Path();
        pinPath.addCircle(centerX, circleCenterY, circleRadius, Path.Direction.CW);
        pinPath.moveTo(centerX - 15.0f, circleCenterY + 18.0f);
        pinPath.lineTo(centerX, height - 12.0f);
        pinPath.lineTo(centerX + 15.0f, circleCenterY + 18.0f);
        pinPath.close();

        Paint pinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pinPaint.setColor(Color.rgb(225, 29, 72));
        canvas.drawPath(pinPath, pinPaint);

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(4.0f);
        strokePaint.setColor(Color.WHITE);
        canvas.drawPath(pinPath, strokePaint);

        Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setColor(Color.WHITE);
        canvas.drawCircle(centerX, circleCenterY, 9.0f, centerPaint);

        return bitmap;
    }

    private void sendKeepCallback(CallbackContext callback, JSONObject payload) {
        if (callback == null) {
            return;
        }

        PluginResult result = new PluginResult(PluginResult.Status.OK, payload);
        result.setKeepCallback(true);
        callback.sendPluginResult(result);
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
        stopHeadingFollowMode();
        stopUserTracking();

        if (mapView != null) {
            try {
                mapView.onStop();
                mapView.onDestroy();
            } catch (Throwable ignored) {
                // MapView may already be partially torn down after a failed initialization.
            }
        }

        if (rootView != null && rootView.getParent() instanceof ViewGroup) {
            ((ViewGroup) rootView.getParent()).removeView(rootView);
        }

        mapView = null;
        rootView = null;
        pointAnnotationManager = null;
        markerRecordIds.clear();
        markerAnnotationsByRecordId.clear();
        markerPointsByRecordId.clear();
        waypointSelectedCallback = null;
        markerClickCallback = null;
        waypointSelectionEnabled = false;
        autoAddWaypointMarker = false;
        mapClickListener = null;
        touchableRects.clear();
    }

    private void addBehindWebView(View nativeView) {
        View webViewView = webView.getView();
        webViewView.setBackgroundColor(Color.TRANSPARENT);
        installTouchRouter(webViewView);

        if (webViewView.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) webViewView.getParent();
            int webViewIndex = parent.indexOfChild(webViewView);
            parent.addView(nativeView, Math.max(0, webViewIndex));
            return;
        }

        ViewGroup decor = (ViewGroup) cordova.getActivity().getWindow().getDecorView();
        decor.addView(nativeView, 0);
    }

    private void installTouchRouter(View webViewView) {
        webViewView.setOnTouchListener((view, event) -> {
            if (mapView == null || isInsideTouchableRect(event.getX(), event.getY())) {
                return false;
            }

            handleRawTouchForTap(event);

            MotionEvent mapEvent = MotionEvent.obtain(event);
            mapView.dispatchTouchEvent(mapEvent);
            mapEvent.recycle();
            return true;
        });
    }

    private boolean isInsideTouchableRect(float x, float y) {
        for (TouchRect rect : touchableRects) {
            if (rect.contains(x, y)) {
                return true;
            }
        }

        return false;
    }

    private FrameLayout.LayoutParams layoutParamsFromOptions(JSONObject options) {
        if (!options.optBoolean("inline", false)) {
            return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            );
        }

        int screenWidth = cordova.getActivity().getResources().getDisplayMetrics().widthPixels;
        int screenHeight = cordova.getActivity().getResources().getDisplayMetrics().heightPixels;

        int x = clamp((int) options.optDouble("x", 0.0), 0, screenWidth - 1);
        int y = clamp((int) options.optDouble("y", 0.0), 0, screenHeight - 1);
        int width = clamp((int) options.optDouble("width", 1.0), 1, screenWidth - x);
        int height = clamp((int) options.optDouble("height", 1.0), 1, screenHeight - y);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = x;
        params.topMargin = y;
        return params;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, Math.max(min, max)));
    }

    private static class TouchRect {
        private final double x;
        private final double y;
        private final double width;
        private final double height;

        private TouchRect(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
        }
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
