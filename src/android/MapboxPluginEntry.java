package com.outsystems.mapbox;

import android.Manifest;
import android.animation.Animator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import com.mapbox.bindgen.Value;

import com.mapbox.common.MapboxOptions;
import com.mapbox.common.Cancelable;
import com.mapbox.common.TileRegionLoadOptions;
import com.mapbox.common.TileStore;
import com.mapbox.common.TilesetDescriptor;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.CameraBoundsOptions;
import com.mapbox.maps.GlyphsRasterizationMode;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.OfflineManager;
import com.mapbox.maps.ScreenCoordinate;
import com.mapbox.maps.Style;
import com.mapbox.maps.StyleObjectInfo;
import com.mapbox.maps.StylePackLoadOptions;
import com.mapbox.maps.TilesetDescriptorOptions;
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor;
import com.mapbox.maps.extension.style.layers.properties.PropertyValue;
import com.mapbox.maps.plugin.PuckBearing;
import com.mapbox.maps.plugin.Plugin;
import com.mapbox.maps.plugin.annotation.AnnotationPlugin;
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotation;
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManagerKt;
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation;
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationClickListener;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManagerKt;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions;
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotation;
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationManagerKt;
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationOptions;
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation;
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManagerKt;
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions;
import com.mapbox.maps.plugin.animation.CameraAnimationsPlugin;
import com.mapbox.maps.plugin.animation.MapAnimationOptions;
import com.mapbox.maps.plugin.compass.CompassPlugin;
import com.mapbox.maps.plugin.gestures.GesturesPlugin;
import com.mapbox.maps.plugin.gestures.OnMapClickListener;
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin;
import com.mapbox.maps.plugin.scalebar.ScaleBarPlugin;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MapboxPluginEntry extends CordovaPlugin {
    private static final int MAX_MARKERS = 10000;
    private static final String MARKER_IMAGE_NOT_ALLOWED =
        "Marker imageUrl must be an HTTPS URL on an allowed host or a base64 image data URI up to 1 MiB.";
    private static final int MAX_BOUNDARIES = 1000;
    private static final double MAX_OFFLINE_RADIUS_KM = 50.0;
    private static final double MIN_OFFLINE_ZOOM = 2.0;
    private static final double MAX_OFFLINE_ZOOM = 18.0;
    private static final long MAX_LOCATION_AGE_MS = 30_000L;
    private static final float VERY_ACCURATE_METERS = 5f;
    private static final float ACCURATE_METERS = 15f;
    private static final float MODERATE_METERS = 30f;
    private static final float LOW_METERS = 100f;

private static final float MAX_ACCEPTABLE_ACCURACY_METERS = 25.0f;
    private static final float MAX_REASONABLE_SPEED_MPS = 15.0f;

    private static final long CAMERA_FOLLOW_INTERVAL_MS = 250L;
    private static final long CAMERA_FOLLOW_DURATION_MS = 200L;
    private static final long RECENTER_ANIMATION_DURATION_MS = 1500L;

    private static final int MOVEMENT_STATE_STATIONARY = 0;
    private static final int MOVEMENT_STATE_SLOW = 1;
    private static final int MOVEMENT_STATE_WALKING = 2;

    private static final float MOVEMENT_V_STATIONARY_MPS = 0.35f;
    private static final float MOVEMENT_V_SLOW_MPS = 1.2f;
    private static final float MOVEMENT_CLUSTER_RADIUS_STATIONARY_METERS = 3.0f;
    private static final int MOVEMENT_HISTORY_SIZE = 8;
    private static final long MOVEMENT_HISTORY_MAX_AGE_MS = 15000L;
    private static final int MOVEMENT_STATE_PERSIST_FIXES = 3;

    private static final float MOVEMENT_SPEED_CONSISTENCY_MPS = 1.5f;
    private static final float MOVEMENT_ACCURACY_SUSPECT_METERS = 12.0f;

    private static final int MOVEMENT_STATIONARY_ENTER_FIXES = 4;
    private static final float MOVEMENT_STATIONARY_ENTER_ACCURACY_METERS = 10.0f;
    private static final float MOVEMENT_STATIONARY_ENTER_DISPLACEMENT_METERS = 1.5f;
    private static final int MOVEMENT_STATIONARY_EXIT_FIXES_SLOW = 2;
    private static final int MOVEMENT_STATIONARY_EXIT_FIXES_WALKING = 1;

    private static final double SMOOTHING_ALPHA_STATIONARY = 0.15;
    private static final double SMOOTHING_ALPHA_SLOW = 0.5;
    private static final double SMOOTHING_ALPHA_WALKING = 0.95;

    private volatile long sessionGeneration = 0;
    private MapView mapView;

    protected void cancelPendingLocationActions() { }

    private void runForSession(Runnable action) {
        runForGeneration(sessionGeneration, action);
    }

    private void runForGeneration(long generation, Runnable action) {
        cordova.getActivity().runOnUiThread(() -> {
            if (generation == sessionGeneration) action.run();
        });
    }

    private final List<CallbackContext> sessionCallbacks = new ArrayList<>();
    private CallbackContext closeCallback;
    private FrameLayout touchRouter;
    private View routedWebView;
    private ViewGroup.LayoutParams originalWebViewLayout;
    private android.graphics.drawable.Drawable originalWebViewBackground;
    private int originalWebViewBackgroundColor;
    private android.graphics.drawable.Drawable mapWebViewBackground;
    private com.mapbox.maps.plugin.gestures.OnMoveListener moveGestureListener;
    private com.mapbox.maps.plugin.gestures.OnScaleListener scaleGestureListener;

    private void releaseCallback(CallbackContext callback) {
        if (callback == null || callback.isFinished()) return;
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(false);
        callback.sendPluginResult(result);
    }

    private FrameLayout rootView;
    private final List<TouchRect> touchableRects = new ArrayList<>();
    private boolean currentGestureTargetsWebView = false;
    private SensorManager sensorManager;
    private SensorEventListener headingSensorListener;
    private final float[] headingRotationMatrix = new float[9];
    private final float[] headingOrientationValues = new float[3];
    private double lastHeadingBearing = -1.0;
    private long lastHeadingUpdateMs = 0L;
    private LocationManager locationManager;
    private long lastUserTrackingUpdateMs = 0L;
    private Location lastAcceptedTrackingLocation = null;
    private Location latestRecenterLocation = null;
    private Point smoothedTrackingPoint = null;
    private int movementState = MOVEMENT_STATE_STATIONARY;
    private int movementStateAgreement = 0;
    private final List<Location> movementHistory = new ArrayList<>();
    private final Handler cameraFollowHandler =
        new Handler(Looper.getMainLooper());
    private Runnable cameraFollowRunnable;
    private Point lastCameraFollowTarget = null;
    private SmoothedLocationProvider smoothedLocationProvider;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback fusedLocationCallback;
    private CallbackContext moveToCurrentLocationCallback = null;
    private static final long LOCATION_WAIT_MS = 15_000L;
    private final Handler locationWaitHandler = new Handler(Looper.getMainLooper());
    private Runnable recenterTimeout;
    private Runnable subscriptionTimeout;
    private boolean locationSubscriptionReady;
    private long trackingRequestGeneration;
    private interface LocationStartCallback {
        void ready();
        void failed(String message);
    }
    private final List<LocationStartCallback> locationStartCallbacks = new ArrayList<>();
    private double moveToCurrentLocationZoom = 0.0;
    private boolean moveToCurrentLocationZoomSet = false;
    private float currentLocationAccuracy = -1f;
    private String currentLocationAccuracyLabel = "Unknown";
    private PointAnnotationManager pointAnnotationManager;
    private OnPointAnnotationClickListener markerTapListener;
    private PolygonAnnotationManager boundaryAnnotationManager;
    private final List<PolygonAnnotationOptions> boundaryAnnotationOptions = new ArrayList<>();
    private final List<PolygonAnnotation> boundaryAnnotations = new ArrayList<>();
    // Stores polygons that have already been saved.
    private final List<PolygonAnnotation> completedBoundaryDrawingPolygons =
        new ArrayList<>();
    private boolean isBoundaryDrawing = false;
    private final List<Point> boundaryDrawingPoints = new ArrayList<>();
    private PolylineAnnotationManager boundaryDrawingLineManager;
    private PolylineAnnotation boundaryDrawingLine;
    private CircleAnnotationManager boundaryDrawingPointManager;
    private final List<CircleAnnotation> boundaryDrawingPointAnnotations = new ArrayList<>();
    private PolygonAnnotationManager boundaryDrawingPolygonManager;
    private PolygonAnnotation boundaryDrawingPolygon;
    private static final int DEFAULT_BOUNDARY_DRAWING_COLOR = Color.RED;
    private static final double DEFAULT_BOUNDARY_LINE_WIDTH = 2.0;
    private static final double DEFAULT_BOUNDARY_FILL_OPACITY = 0.30;
    private final Map<String, String> markerRecordIds = new HashMap<>();
    private final Map<String, PointAnnotation> markerAnnotationsByRecordId = new HashMap<>();
    private final Map<String, Point> markerPointsByRecordId = new HashMap<>();
    private final Map<String, MarkerStyle> markerStylesByRecordId = new HashMap<>();
    private final Map<String, List<String>> pendingMarkerImages = new HashMap<>();
    private final MarkerIcons markerIcons = new MarkerIcons();

    private PolylineAnnotationManager lineAnnotationManager;
    private final List<PolylineAnnotation> pathAnnotations = new ArrayList<>();
    private PolylineAnnotation currentSegmentAnnotation = null;
    private final List<Point> pathPoints = new ArrayList<>();
    private boolean isPathTrackingActive = false;
    private long pathTrackingStartTimeMs = 0L;
    private long pathPausedTotalMs = 0L;
    private long pathPauseStartMs = 0L;
    private boolean isPathVisible = true;
    private static final int DEFAULT_PATH_COLOR = 0xFFFF0000;
    private static final double DEFAULT_PATH_WIDTH = 3.0;
    private static final double MAX_PATH_WIDTH = 30.0;
    private static final int MAX_PATH_SEGMENTS = 1000;
    private int pathLineColor = DEFAULT_PATH_COLOR;
    private float pathLineWidth = 3.0f;
    private float pathLineOpacity = 1.0f;
    private boolean isPathTrackingPaused = false;
    private final List<List<Point>> pathSegments = new ArrayList<>();
    private List<Point> currentSegment = null;
    private CallbackContext waypointSelectedCallback;
    private CallbackContext markerClickCallback;
    private CallbackContext mapTapCallback;
    private CallbackContext boundaryAreaCallback;
    private CallbackContext offlineDownloadProgressCallback;
    private OfflineManager activeOfflineManager;
    private TileStore activeOfflineTileStore;
    private Cancelable activeStylePackDownload;
    private Cancelable activeTileRegionDownload;
    private boolean isOfflineDownloading = false;
    private boolean waypointSelectionEnabled = false;
    private boolean autoAddWaypointMarker = false;
    private OnMapClickListener mapClickListener;
    private float rawTapDownX = 0.0f;
    private float rawTapDownY = 0.0f;
    private long rawTapDownMs = 0L;
    private long lastMapSelectionCallbackMs = 0L;
    private long lastKeepCallbackOfflineMs = 0L;
    private long lastKeepCallbackWaypointMs = 0L;
    private long lastKeepCallbackMarkerMs = 0L;
    private static final long CALLBACK_RATE_LIMIT_MS = 100L;
    private boolean boundaryVisible = true;
    private boolean isUserLocationEnabled = false;
    private boolean isUserTrackingEnabled = false;
    private boolean isDeviceHeadingEnabled = false;
    private boolean isHeadingFollowModeEnabled = false;
    private CallbackContext trackingStatusCallback;
    private CallbackContext locationAccuracyCallback;
    private FusedLocationProviderClient accuracyFusedLocationClient;
    private LocationCallback accuracyFusedLocationCallback;
    private Cancelable compassCameraSubscription;
    private CallbackContext compassCallback;

    private double lastCompassBearing = Double.NaN;
    private boolean lastCompassVisible = false;
    private long lastLocationAccuracyUpdateMs = 0L;
    private static final long LOCATION_ACCURACY_CALLBACK_INTERVAL_MS = 500L;
    private boolean isCameraFollowingUser = true;
    private boolean isRecenterAnimating = false;
    private int recenterAnimationGeneration = 0;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            if (!executeOnUiThread(action, args, callbackContext)) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            }
        });
        return true;
    }

    private boolean executeOnUiThread(String action, JSONArray args, CallbackContext callbackContext) {
        sessionCallbacks.removeIf(CallbackContext::isFinished);
        if (!"close".equals(action) && !"initialize".equals(action)) sessionCallbacks.add(callbackContext);
        if (args.toString().length() > MapboxSecurity.MAX_INPUT_CHARS) {
            callbackContext.error("Map input exceeds the 4 MiB limit.");
            return true;
        }
        JSONObject options = args.optJSONObject(0);
        if (options == null) {
            options = new JSONObject();
        }

        switch (action) {
            case "registerCloseCallback":
                releaseCallback(closeCallback);
                closeCallback = callbackContext;
                if (mapView == null) { closeCallback.success(); closeCallback = null; }
                return true;
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
            case "getMapboxVersion":
            {
                int versionResId = cordova.getActivity()
                    .getResources()
                    .getIdentifier(
                        "mapbox_version",
                        "string",
                        cordova.getActivity().getPackageName()
                    );

                String mapboxVersion = versionResId != 0
                    ? cordova.getActivity().getString(versionResId)
                    : "unknown";

                callbackContext.success(mapboxVersion);
                return true;
            }
            case "initialize":
                initialize(options, callbackContext);
                return true;
            case "setViewport":
                setViewport(options, callbackContext);
                return true;
            case "resizeMap":
                resizeMap(options, callbackContext);
                return true;
            case "setTouchableRects":
                setTouchableRects(args, callbackContext);
                return true;
            case "setCamera":
                setCamera(options, callbackContext);
                return true;
            case "flyTo":
                flyTo(options, callbackContext);
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
            case "moveToCurrentLocation":
                moveToCurrentLocation(options, callbackContext);
                return true;
            case "downloadOfflineRegion":
                downloadOfflineRegion(options, callbackContext);
                return true;
            case "downloadOfflineRegionForRect":
                downloadOfflineRegionForRect(options, callbackContext);
                return true;
            case "showOfflineRegion":
                showOfflineRegion(options, callbackContext);
                return true;
            case "deleteOfflineRegion":
                deleteOfflineRegion(options, callbackContext);
                return true;
            case "registerOfflineDownloadProgressCallback":
                registerOfflineDownloadProgressCallback(callbackContext);
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
            case "registerMapTapCallback":
                registerMapTapCallback(callbackContext);
                return true;
            case "registerBoundaryAreaCallback":
                registerBoundaryAreaCallback(callbackContext);
                return true;
            case "registerTrackingStatusCallback":
                registerTrackingStatusCallback(callbackContext);
                return true;
            case "registerLocationAccuracyCallback":
                registerLocationAccuracyCallback(callbackContext);
                return true;
            case "registerCompassCallback":
                registerCompassCallback(callbackContext);
                return true;
            case "resetMapBearing":
                resetMapBearing(callbackContext);
                return true;
            case "getCamera":
                getCamera(callbackContext);
                return true;
            case "getCurrentLocationAccuracy":
                getCurrentLocationAccuracy(callbackContext);
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
            case "loadBoundaries":
                loadBoundaries(options, callbackContext);
                return true;
            case "setBoundaryVisibility":
                setBoundaryVisibility(options, callbackContext);
                return true;
            case "setLayerVisibility":
                setLayerVisibility(options, callbackContext);
                return true;
            case "getLayerIds":
                getLayerIds(callbackContext);
                return true;
            case "clearBoundaries":
                clearBoundaries(callbackContext);
                return true;
            case "startBoundaryDrawing":
                startBoundaryDrawing(options, callbackContext);
                return true;
            case "undoBoundaryPoint":
                undoBoundaryPoint(callbackContext);
                return true;
            case "saveBoundaryDrawing":
                saveBoundaryDrawing(options, callbackContext);
                return true;
            case "cancelBoundaryDrawing":
                cancelBoundaryDrawing(callbackContext);
                return true;
            case "startPathTracking":
                startPathTracking(options, callbackContext);
                return true;
            case "stopPathTracking":
                stopPathTracking(callbackContext);
                return true;
            case "pausePathTracking":
                pausePathTracking(callbackContext);
                return true;
            case "continuePathTracking":
                continuePathTracking(callbackContext);
                return true;
            case "loadPath":
                loadPath(options, callbackContext);
                return true;
            case "clearPaths":
                clearPaths(callbackContext);
                return true;
            case "setPathVisibility":
                setPathVisibility(options, callbackContext);
                return true;
            case "close":
                close(callbackContext);
                return true;
            case "setMapStyle":
                setMapStyle(options, callbackContext);
                return true;
            default:
                return false;
        }
    }

    private static Double optionalCameraZoom(JSONObject options, String key) {
        if (options.isNull(key)) return null;
        Object value = options.opt(key);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(key + " must be a finite number between 0 and 25.5.");
        }
        double zoom = ((Number) value).doubleValue();
        if (Double.isNaN(zoom) || Double.isInfinite(zoom) || zoom < 0 || zoom > 25.5) {
            throw new IllegalArgumentException(key + " must be a finite number between 0 and 25.5.");
        }
        return zoom;
    }

    private void initialize(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            final Double minZoom;
            final Double maxZoom;
            try {
                minZoom = optionalCameraZoom(options, "minZoom");
                maxZoom = optionalCameraZoom(options, "maxZoom");
                if (minZoom != null && maxZoom != null && minZoom > maxZoom) {
                    throw new IllegalArgumentException("minZoom must be less than or equal to maxZoom.");
                }
            } catch (IllegalArgumentException error) {
                callback.error(error.getMessage());
                return;
            }
            try {
                String token = getAccessToken();
                if (token.isEmpty()) {
                    callback.error("A public pk.* Mapbox token is required. Configure MAPBOX_ACCESS_TOKEN in OutSystems Extensibility Configuration.");
                    return;
                }

                MapboxOptions.setAccessToken(token);

                String styleUrl = options.optString("styleUrl", Style.MAPBOX_STREETS);
            if (!styleAllowed(styleUrl)) {
                callback.error("Style URL is not allowed. Use a Mapbox style or an approved HTTPS host.");
                return;
            }
                double latitude = options.optDouble("latitude", 0.0);
                double longitude = options.optDouble("longitude", 0.0);
                double zoom = options.optDouble("zoom", 12.0);
                double bearing = options.optDouble("bearing", 0.0);
                double pitch = options.optDouble("pitch", 0.0);

                if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) {
                    callback.error("Invalid coordinates: latitude must be in [-90, 90], longitude in [-180, 180].");
                    return;
                }

                closeInternal();
                sessionCallbacks.add(callback);

                boolean behindWebView = options.optBoolean("behindWebView", false);

                rootView = new FrameLayout(cordova.getActivity());
                rootView.setBackgroundColor(behindWebView ? Color.TRANSPARENT : Color.WHITE);
                rootView.setLayoutParams(layoutParamsFromOptions(options));

                mapView = new MapView(cordova.getActivity());

                if (minZoom != null || maxZoom != null) {
                    CameraBoundsOptions.Builder bounds = new CameraBoundsOptions.Builder();
                    if (minZoom != null) bounds.minZoom(minZoom);
                    if (maxZoom != null) bounds.maxZoom(maxZoom);
                    if (mapView.getMapboxMap().setBounds(bounds.build()).isError()) {
                        closeInternal();
                        callback.error("Camera zoom bounds conflict with the map's default bounds.");
                        return;
                    }
                }

                CompassPlugin compassPlugin =
                    mapView.getPlugin(Plugin.MAPBOX_COMPASS_PLUGIN_ID);

                if (compassPlugin != null) {
                    compassPlugin.setEnabled(false);
                }

                ScaleBarPlugin scaleBarPlugin =
                    mapView.getPlugin(Plugin.MAPBOX_SCALEBAR_PLUGIN_ID);

                if (scaleBarPlugin != null) {
                    scaleBarPlugin.setEnabled(false);
                }

                mapView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                ));

                rootView.addView(mapView);
                mapView.onStart();
                installMapClickListener();
                GesturesPlugin gestures =
    mapView.getPlugin(Plugin.MAPBOX_GESTURES_PLUGIN_ID);

if (gestures != null) {
    final long gestureGeneration = sessionGeneration;

    moveGestureListener = new com.mapbox.maps.plugin.gestures.OnMoveListener() {
        @Override
        public void onMoveBegin(
                com.mapbox.android.gestures.MoveGestureDetector detector) {
            if (gestureGeneration == sessionGeneration) isCameraFollowingUser = false;
        }

        @Override
        public boolean onMove(
                com.mapbox.android.gestures.MoveGestureDetector detector) {
            return false;
        }

        @Override
        public void onMoveEnd(
                com.mapbox.android.gestures.MoveGestureDetector detector) {
        }
    };
    gestures.addOnMoveListener(moveGestureListener);

            scaleGestureListener = new com.mapbox.maps.plugin.gestures.OnScaleListener() {
                    @Override
                    public void onScaleBegin(
                            com.mapbox.android.gestures.StandardScaleGestureDetector detector) {
                        if (gestureGeneration == sessionGeneration) isCameraFollowingUser = false;
                    }
            
                    @Override
                    public void onScale(
                            com.mapbox.android.gestures.StandardScaleGestureDetector detector) {
                        // No additional action needed.
                    }
            
                    @Override
                    public void onScaleEnd(
                            com.mapbox.android.gestures.StandardScaleGestureDetector detector) {
                    }
                };
            gestures.addOnScaleListener(scaleGestureListener);
}    

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
                    .bearing(bearing)
                    .pitch(pitch)
                    .build());

                loadReadyStyle(styleUrl, "initialized", callback);
            } catch (Throwable e) {
                callback.error(sanitizeError("Failed to initialize Mapbox map.", e));
                closeInternal();
            }
        });
    }

    private String getAccessToken() {
        // Public runtime tokens are already bundled in config.xml. Read the
        // authoritative configuration on every initialization; never prefer an old cache.
        String token = preferences.getString("MAPBOX_ACCESS_TOKEN", "").trim();
        // Remove values written by releases that cached credentials.
        cordova.getActivity().getSharedPreferences("mapbox_secure_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply();
        return MapboxSecurity.publicToken(token) ? token : "";
    }

    private boolean styleAllowed(String style) {
        return MapboxSecurity.styleAllowed(style,
            preferences.getString("MAPBOX_ALLOWED_STYLE_HOSTS", "api.mapbox.com"));
    }

    private void setViewport(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            if (rootView == null) {
                callback.error("Map is not initialized in setviewport.");
                return;
            }

            rootView.setLayoutParams(layoutParamsFromOptions(options));
            rootView.requestLayout();
            callback.success();
        });
    }

    private void resizeMap(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            if (rootView == null || mapView == null) {
                callback.error("Map is not initialized in resizemap.");
                return;
            }

            int x = options.optInt("x", 0);
            int y = options.optInt("y", 0);
            int width = options.optInt("width", 1);
            int height = options.optInt("height", 1);

            int[] webViewLocation = new int[2];
            webView.getView().getLocationInWindow(webViewLocation);

            int webViewTop = webViewLocation[1];
            int webViewLeft = webViewLocation[0];

            int nativeX = x + webViewLeft;
            int nativeY = y + webViewTop;

            FrameLayout.LayoutParams params;
            ViewGroup.LayoutParams currentParams = rootView.getLayoutParams();

            if (currentParams instanceof FrameLayout.LayoutParams) {
                params = (FrameLayout.LayoutParams) currentParams;
            } else {
                params = new FrameLayout.LayoutParams(width, height);
            }

            params.width = width;
            params.height = height;
            params.leftMargin = nativeX;
            params.topMargin = nativeY;
            rootView.setLayoutParams(params);

            FrameLayout.LayoutParams mapParams =
                new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                );
            mapView.setLayoutParams(mapParams);

            rootView.requestLayout();
            mapView.requestLayout();
            callback.success();
        });
    }

   private void setTouchableRects(JSONArray args, CallbackContext callback) {
    runForSession(() -> {
        touchableRects.clear();

        JSONArray rects = args.optJSONArray(0);

        if (rects == null || rects.length() == 0) {
            callback.success();
            return;
        }

        int maxRects = 50;

        if (rects.length() > maxRects) {
            callback.error(
                "Too many touchable rects. Maximum is " + maxRects + "."
            );
            return;
        }

        for (int i = 0; i < rects.length(); i++) {
            JSONObject rect = rects.optJSONObject(i);

            if (rect == null) {
                continue;
            }

            double x = rect.optDouble("x", 0.0);
            double y = rect.optDouble("y", 0.0);
            double width = rect.optDouble("width", 0.0);
            double height = rect.optDouble("height", 0.0);

            if (width <= 0 || height <= 0) {
                continue;
            }
            
            // Rectangles are WebView-local native pixels (CSS viewport coordinates * DPR).
            touchableRects.add(
                new TouchRect(x, y, width, height)
            );
        }

        callback.success();
    });
}
    private void setCamera(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized in setcamera.");
                return;
            }

            Point currentCenter = mapView.getMapboxMap().getCameraState().getCenter();
            double setCamLat = options.optDouble("latitude", currentCenter.latitude());
            double setCamLng = options.optDouble("longitude", currentCenter.longitude());
            if (!isValidLatitude(setCamLat) || !isValidLongitude(setCamLng)) {
                callback.error("Invalid coordinates: latitude must be in [-90, 90], longitude in [-180, 180].");
                return;
            }

            mapView.getMapboxMap().setCamera(new CameraOptions.Builder()
                .center(Point.fromLngLat(setCamLng, setCamLat))
                .zoom(options.optDouble("zoom", mapView.getMapboxMap().getCameraState().getZoom()))
                .bearing(options.optDouble("bearing", mapView.getMapboxMap().getCameraState().getBearing()))
                .pitch(options.optDouble("pitch", mapView.getMapboxMap().getCameraState().getPitch()))
                .build());

            callback.success();
        });
    }

    private void flyTo(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized in flyto.");
                return;
            }

            Point currentCenter = mapView.getMapboxMap().getCameraState().getCenter();
            double flyLat = options.optDouble("latitude", currentCenter.latitude());
            double flyLng = options.optDouble("longitude", currentCenter.longitude());
            if (!isValidLatitude(flyLat) || !isValidLongitude(flyLng)) {
                callback.error("Invalid coordinates: latitude must be in [-90, 90], longitude in [-180, 180].");
                return;
            }

            CameraAnimationsPlugin cameraAnimations = mapView.getPlugin(Plugin.MAPBOX_CAMERA_PLUGIN_ID);
            if (cameraAnimations == null) {
                callback.error("Camera animations plugin is not available.");
                return;
            }

            CameraOptions cameraOptions = new CameraOptions.Builder()
                .center(Point.fromLngLat(flyLng, flyLat))
                .zoom(options.optDouble("zoom", mapView.getMapboxMap().getCameraState().getZoom()))
                .bearing(options.optDouble("bearing", mapView.getMapboxMap().getCameraState().getBearing()))
                .pitch(options.optDouble("pitch", mapView.getMapboxMap().getCameraState().getPitch()))
                .build();

            MapAnimationOptions.Builder animationOptions = new MapAnimationOptions.Builder();
            if (options.has("duration")) {
                animationOptions.duration(options.optLong("duration", 2000L));
            }

            cameraAnimations.flyTo(cameraOptions, animationOptions.build(), null);
            callback.success();
        });
    }

    private void enableUserLocation(CallbackContext callback) {
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized in enableuserlocation.");
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

            if (smoothedLocationProvider == null) {
                smoothedLocationProvider =
                    new SmoothedLocationProvider();
            }

            location.setLocationProvider(smoothedLocationProvider);
            location.setPuckBearing(PuckBearing.HEADING);
            location.setPuckBearingEnabled(true);
            location.setEnabled(true);

            isUserLocationEnabled = true;
            ensureLocationUpdates(new LocationStartCallback() {
                public void ready() {
                    updateInitialUserLocation(latestRecenterLocation);
                    fireTrackingStatusChanged();
                    callback.success();
                }
                public void failed(String message) { callback.error(message); }
            });
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
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized setDeviceHeadingEnabled.");
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

            isDeviceHeadingEnabled = enabled;
            fireTrackingStatusChanged();

            callback.success();
        });
    }

    private void setHeadingFollowMode(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized setHeadingFollowMode.");
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

        final long headingGeneration = sessionGeneration;
        headingSensorListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (headingGeneration != sessionGeneration || this != headingSensorListener) return;
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
                runForSession(() -> {
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

        isHeadingFollowModeEnabled = true;
        fireTrackingStatusChanged();

        callback.success();
    }

    private void stopHeadingFollowMode() {
        if (sensorManager != null && headingSensorListener != null) {
            sensorManager.unregisterListener(headingSensorListener);
        }

        headingSensorListener = null;
        lastHeadingBearing = -1.0;
        lastHeadingUpdateMs = 0L;
        isHeadingFollowModeEnabled = false;
        fireTrackingStatusChanged();
    }

    private double shortestBearingDelta(double from, double to) {
        return (to - from + 540.0) % 360.0 - 180.0;
    }

    private double calculateDistanceMeters(
        double lat1, double lon1,
        double lat2, double lon2
    ) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
    }

    private void setUserTrackingEnabled(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized setUserTrackingEnabled.");
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

    private void updateMovementHistory(Location location) {
        movementHistory.add(new Location(location));
        while (movementHistory.size() > MOVEMENT_HISTORY_SIZE) {
            movementHistory.remove(0);
        }
        long newestTime =
            movementHistory.get(movementHistory.size() - 1).getTime();
        while (movementHistory.size() > 1
                && newestTime - movementHistory.get(0).getTime()
                    > MOVEMENT_HISTORY_MAX_AGE_MS) {
            movementHistory.remove(0);
        }
    }

    private float computeWindowDisplacementRateMps() {
        if (movementHistory.size() < 2) {
            return 0.0f;
        }
        Location oldest = movementHistory.get(0);
        Location newest =
            movementHistory.get(movementHistory.size() - 1);
        double distance = calculateDistanceMeters(
            oldest.getLatitude(),
            oldest.getLongitude(),
            newest.getLatitude(),
            newest.getLongitude()
        );
        long elapsedMs = newest.getTime() - oldest.getTime();
        if (elapsedMs <= 0) {
            return 0.0f;
        }
        return (float) (distance / (elapsedMs / 1000.0));
    }

    private float computeClusterRadiusMeters() {
        if (movementHistory.isEmpty()) {
            return 0.0f;
        }
        double meanLat = 0.0;
        double meanLon = 0.0;
        for (Location fix : movementHistory) {
            meanLat += fix.getLatitude();
            meanLon += fix.getLongitude();
        }
        meanLat /= movementHistory.size();
        meanLon /= movementHistory.size();

        float maxRadius = 0.0f;
        for (Location fix : movementHistory) {
            float radius = (float) calculateDistanceMeters(
                meanLat,
                meanLon,
                fix.getLatitude(),
                fix.getLongitude()
            );
            if (radius > maxRadius) {
                maxRadius = radius;
            }
        }
        return maxRadius;
    }

    private String movementStateName(int state) {
        switch (state) {
            case MOVEMENT_STATE_SLOW:
                return "SLOW";
            case MOVEMENT_STATE_WALKING:
                return "WALKING";
            default:
                return "STATIONARY";
        }
    }

    private int classifyMovementState(
        Location location,
        double displacementMeters,
        long fixTimeDiffMs
    ) {
        boolean hasReportedSpeed = location.hasSpeed();
        float reportedSpeed =
            hasReportedSpeed ? location.getSpeed() : 0.0f;

        float calculatedSpeed = 0.0f;
        if (lastAcceptedTrackingLocation != null && fixTimeDiffMs > 0) {
            calculatedSpeed = (float) (displacementMeters
                / (fixTimeDiffMs / 1000.0));
        }

        float effectiveMotion;
        if (lastAcceptedTrackingLocation == null) {
            effectiveMotion = hasReportedSpeed
                ? reportedSpeed
                : computeWindowDisplacementRateMps();
        } else if (hasReportedSpeed) {
            if (Math.abs(calculatedSpeed - reportedSpeed)
                    > MOVEMENT_SPEED_CONSISTENCY_MPS) {
                // Signals disagree: trust doppler-based reported speed
                effectiveMotion = reportedSpeed;
            } else {
                effectiveMotion = Math.max(
                    reportedSpeed,
                    calculatedSpeed
                );
            }
        } else if (fixTimeDiffMs > 0) {
            effectiveMotion = calculatedSpeed;
        } else {
            effectiveMotion = computeWindowDisplacementRateMps();
        }

        // Poor accuracy: do not let position-derived speed reach WALKING
        if (location.hasAccuracy()
                && location.getAccuracy()
                    > MOVEMENT_ACCURACY_SUSPECT_METERS
                && effectiveMotion >= MOVEMENT_V_SLOW_MPS) {
            effectiveMotion = MOVEMENT_V_SLOW_MPS;
        }

        if (effectiveMotion < MOVEMENT_V_STATIONARY_MPS
                && (location.hasSpeed()
                    || computeClusterRadiusMeters()
                        < MOVEMENT_CLUSTER_RADIUS_STATIONARY_METERS)) {
            return MOVEMENT_STATE_STATIONARY;
        }
        if (effectiveMotion < MOVEMENT_V_SLOW_MPS) {
            return MOVEMENT_STATE_SLOW;
        }
        return MOVEMENT_STATE_WALKING;
    }

    private boolean updateMovementState(
        Location location,
        double displacementMeters,
        long fixTimeDiffMs
    ) {
        int candidate =
            classifyMovementState(
                location,
                displacementMeters,
                fixTimeDiffMs
            );
        if (candidate == movementState) {
            movementStateAgreement = 0;
            return false;
        }

        if (candidate == MOVEMENT_STATE_STATIONARY
                && !meetsStationaryEntryPreconditions(
                    location,
                    displacementMeters
                )) {
            movementStateAgreement = 0;
            return false;
        }

        int requiredFixes = confirmationFixesFor(candidate);

        movementStateAgreement++;
        if (movementStateAgreement < requiredFixes) {
            return false;
        }

        int previousState = movementState;
        movementState = candidate;
        int usedFixes = movementStateAgreement;
        movementStateAgreement = 0;

        boolean releasedFromHold =
            previousState == MOVEMENT_STATE_STATIONARY
            && candidate != MOVEMENT_STATE_STATIONARY;

        float calculatedSpeed = 0.0f;
        if (lastAcceptedTrackingLocation != null && fixTimeDiffMs > 0) {
            calculatedSpeed = (float) (displacementMeters
                / (fixTimeDiffMs / 1000.0));
        }

        Log.d(
            "MapboxPlugin",
            "movement state " + movementStateName(previousState)
                + " -> " + movementStateName(movementState)
                + " (confirm=" + requiredFixes
                + " fixes=" + usedFixes
                + " release=" + releasedFromHold
                + " speed=" + String.format(
                    java.util.Locale.US,
                    "%.2f",
                    location.hasSpeed()
                        ? location.getSpeed()
                        : 0.0f
                ) + " m/s, calc=" + String.format(
                    java.util.Locale.US,
                    "%.2f",
                    calculatedSpeed
                ) + " m/s, dt=" + fixTimeDiffMs
                + " ms, disp=" + String.format(
                    java.util.Locale.US,
                    "%.2f",
                    displacementMeters
                ) + " m, acc=" + String.format(
                    java.util.Locale.US,
                    "%.2f",
                    location.hasAccuracy()
                        ? location.getAccuracy()
                        : -1.0f
                ) + " m, cluster=" + String.format(
                    java.util.Locale.US,
                    "%.2f",
                    computeClusterRadiusMeters()
                ) + " m)"
        );

        return releasedFromHold;
    }

    private int confirmationFixesFor(int candidate) {
        if (movementState == MOVEMENT_STATE_STATIONARY) {
            return candidate == MOVEMENT_STATE_WALKING
                ? MOVEMENT_STATIONARY_EXIT_FIXES_WALKING
                : MOVEMENT_STATIONARY_EXIT_FIXES_SLOW;
        }
        if (candidate == MOVEMENT_STATE_STATIONARY) {
            return MOVEMENT_STATIONARY_ENTER_FIXES;
        }
        return MOVEMENT_STATE_PERSIST_FIXES;
    }

    private boolean meetsStationaryEntryPreconditions(
        Location location,
        double displacementMeters
    ) {
        float accuracy =
            location.hasAccuracy()
                ? location.getAccuracy()
                : Float.MAX_VALUE;
        return accuracy
                <= MOVEMENT_STATIONARY_ENTER_ACCURACY_METERS
            && displacementMeters
                <= MOVEMENT_STATIONARY_ENTER_DISPLACEMENT_METERS;
    }

    private double smoothingAlphaForState() {
        switch (movementState) {
            case MOVEMENT_STATE_SLOW:
                return SMOOTHING_ALPHA_SLOW;
            case MOVEMENT_STATE_WALKING:
                return SMOOTHING_ALPHA_WALKING;
            default:
                return SMOOTHING_ALPHA_STATIONARY;
        }
    }

    private void startUserTracking(CallbackContext callback) {
        final long request = ++trackingRequestGeneration;
        ensureLocationUpdates(new LocationStartCallback() {
            public void ready() {
                if (request != trackingRequestGeneration) {
                    callback.error("Location request was cancelled.");
                    return;
                }
                isUserTrackingEnabled = true;
                isCameraFollowingUser = true;
                startCameraFollow();
                fireTrackingStatusChanged();
                callback.success();
            }
            public void failed(String message) { callback.error(message); }
        });
    }

    private String locationServiceProblem() {
        if (!hasLocationPermission()) return "Location permission is not granted.";
        LocationManager manager = (LocationManager) cordova.getActivity().getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) return "Device location manager is not available.";
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                && !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return "Location provider is not enabled.";
        }
        return null;
    }

    private void ensureLocationUpdates(LocationStartCallback callback) {
        String problem = locationServiceProblem();
        if (problem != null) { callback.failed(problem); return; }
        if (locationSubscriptionReady) { callback.ready(); return; }
        locationStartCallbacks.add(callback);
        if (fusedLocationCallback != null) return;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(cordova.getActivity());
        final long trackingGeneration = sessionGeneration;
        fusedLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null
                        || locationResult.getLocations() == null
                        || locationResult.getLocations().isEmpty()) {
                    return;
                }

                if (trackingGeneration != sessionGeneration || fusedLocationCallback != this) return;

                for (Location location : locationResult.getLocations()) {
                    if (mapView == null || location == null) {
                        continue;
                    }

                    sendLocationAccuracyUpdate(location);

                    long now = System.currentTimeMillis();

                    // One stream supplies display, recenter and recording. Keep fresh
                    // approximate fixes for display without admitting them to the path.
                    if (!isUsableRecenterLocation(location)) continue;
                    if (latestRecenterLocation != null && location.getElapsedRealtimeNanos()
                            <= latestRecenterLocation.getElapsedRealtimeNanos()) continue;
                    latestRecenterLocation = new Location(location);
                    updateInitialUserLocation(location);
                    if (moveToCurrentLocationCallback != null) {
                        completeMoveToCurrentLocation(new Location(location));
                    }
                    if (!isUserTrackingEnabled
                            || location.getAccuracy() > MAX_ACCEPTABLE_ACCURACY_METERS) continue;

                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();

                    // 2. Validate coordinates
                    if (!isValidLatitude(latitude)
                            || !isValidLongitude(longitude)) {
                        continue;
                    }

                    // 3. Reject jumps, maintain history, classify movement
                    double displacementMeters = 0.0;
                    long fixTimeDiffMs = 0L;
                    if (lastAcceptedTrackingLocation != null) {
                        displacementMeters = calculateDistanceMeters(
                            lastAcceptedTrackingLocation.getLatitude(),
                            lastAcceptedTrackingLocation.getLongitude(),
                            latitude,
                            longitude
                        );
                        fixTimeDiffMs =
                            location.getTime()
                            - lastAcceptedTrackingLocation.getTime();

                        // Reject unrealistic jumps independently of classification
                        if (Math.abs(fixTimeDiffMs) > 0) {
                            float speed = (float) (displacementMeters
                                / (Math.abs(fixTimeDiffMs) / 1000.0));
                            if (location.hasSpeed()
                                    && location.getSpeed() > speed) {
                                speed = location.getSpeed();
                            }
                            if (speed > MAX_REASONABLE_SPEED_MPS) {
                                continue;
                            }
                        }
                    }

                    updateMovementHistory(location);

                    boolean releasedFromHold =
                        updateMovementState(
                            location,
                            displacementMeters,
                            fixTimeDiffMs
                        );

                    if (releasedFromHold) {
                        movementHistory.clear();
                        movementHistory.add(new Location(location));
                    }

                    // Keep a fresh raw fix even while the displayed puck is pinned.
                    latestRecenterLocation = new Location(location);

                    // Pin the puck while the classifier says stationary,
                    // but always let the first fix through so the dot
                    // renders immediately on tracking start
                    if (movementState == MOVEMENT_STATE_STATIONARY
                            && smoothedTrackingPoint != null) {
                        continue;
                    }

                    // Accept this location
                    lastAcceptedTrackingLocation = new Location(location);
                    lastUserTrackingUpdateMs = now;

                    // 4. Apply weighted location smoothing
                    Point rawPoint =
                        Point.fromLngLat(longitude, latitude);

                    if (releasedFromHold) {
                        smoothedTrackingPoint = rawPoint;
                    } else if (smoothedTrackingPoint == null) {
                        smoothedTrackingPoint = rawPoint;
                    } else {
                        double smoothingFactor =
                            smoothingAlphaForState();
                        double smoothedLongitude =
                            smoothedTrackingPoint.longitude()
                            + (
                                rawPoint.longitude()
                                - smoothedTrackingPoint.longitude()
                            ) * smoothingFactor;
                        double smoothedLatitude =
                            smoothedTrackingPoint.latitude()
                            + (
                                rawPoint.latitude()
                                - smoothedTrackingPoint.latitude()
                            ) * smoothingFactor;
                        smoothedTrackingPoint =
                            Point.fromLngLat(
                                smoothedLongitude,
                                smoothedLatitude
                            );
                    }

                    final Point cameraPoint = smoothedTrackingPoint;

                    // 5. Create filtered location for Mapbox puck
                    final Location filteredLocation =
                        new Location(location);
                    filteredLocation.setLatitude(
                        cameraPoint.latitude()
                    );
                    filteredLocation.setLongitude(
                        cameraPoint.longitude()
                    );
                    filteredLocation.setTime(location.getTime());
                    if (location.hasAccuracy()) {
                        filteredLocation.setAccuracy(
                            location.getAccuracy()
                        );
                    }
                    if (location.hasAltitude()) {
                        filteredLocation.setAltitude(
                            location.getAltitude()
                        );
                    }
                    if (location.hasBearing()) {
                        filteredLocation.setBearing(
                            location.getBearing()
                        );
                    }
                    if (location.hasSpeed()) {
                        filteredLocation.setSpeed(location.getSpeed());
                    }

                    // 6. Update Mapbox puck (camera is driven by the follow ticker)
                    runForSession(() -> {
                        if (mapView == null) {
                            return;
                        }

                        if (smoothedLocationProvider != null) {
                            smoothedLocationProvider.updateLocation(
                                filteredLocation
                            );
                        }
                    });

                    // 7. Collect path point if path tracking is active and not paused
                    if (isPathTrackingActive && !isPathTrackingPaused) {
                        if (pathPoints.size() >= MapboxSecurity.MAX_POINTS) {
                            stopUserTracking();
                            continue; // Keep the recording available to stopPathTracking().
                        }
                        pathPoints.add(cameraPoint);
                        if (currentSegment != null) {
                            currentSegment.add(cameraPoint);
                        }
                        runForSession(() -> {
                            if (mapView != null) {
                                updatePathAnnotation();
                            }
                        });
                    }
                }
            }
        };

        final LocationCallback subscription = fusedLocationCallback;
        subscriptionTimeout = () -> {
            if (trackingGeneration == sessionGeneration && fusedLocationCallback == subscription
                    && !locationSubscriptionReady) {
                stopSharedLocationUpdates("Location subscription did not start within 15 seconds.");
            }
        };
        locationWaitHandler.postDelayed(subscriptionTimeout, LOCATION_WAIT_MS);
        try {
            LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L).build();
            fusedLocationClient.requestLocationUpdates(request, subscription, Looper.getMainLooper())
                .addOnSuccessListener(ignored -> {
                    if (trackingGeneration != sessionGeneration || fusedLocationCallback != subscription) {
                        // Registration may finish after teardown. Remove that exact listener.
                        LocationServices.getFusedLocationProviderClient(cordova.getActivity())
                            .removeLocationUpdates(subscription);
                        return;
                    }
                    locationSubscriptionReady = true;
                    locationWaitHandler.removeCallbacks(subscriptionTimeout);
                    subscriptionTimeout = null;
                    List<LocationStartCallback> waiting = new ArrayList<>(locationStartCallbacks);
                    locationStartCallbacks.clear();
                    for (LocationStartCallback pending : waiting) pending.ready();
                })
                .addOnFailureListener(error -> {
                    if (trackingGeneration != sessionGeneration || fusedLocationCallback != subscription) return;
                    stopSharedLocationUpdates("Location subscription failed: " + error.getClass().getSimpleName()
                        + (error.getMessage() == null ? "" : ": " + error.getMessage()));
                });
        } catch (SecurityException error) {
            stopSharedLocationUpdates("Location permission is not granted.");
        }
    }

    private void stopSharedLocationUpdates(String reason) {
        LocationCallback old = fusedLocationCallback;
        fusedLocationCallback = null;
        locationSubscriptionReady = false;
        if (subscriptionTimeout != null) locationWaitHandler.removeCallbacks(subscriptionTimeout);
        subscriptionTimeout = null;
        if (old != null && fusedLocationClient != null) {
            try { fusedLocationClient.removeLocationUpdates(old); }
            catch (SecurityException ignored) { }
        }
        latestRecenterLocation = null;
        List<LocationStartCallback> waiting = new ArrayList<>(locationStartCallbacks);
        locationStartCallbacks.clear();
        for (LocationStartCallback pending : waiting) pending.failed(reason);
        if (moveToCurrentLocationCallback != null) {
            CallbackContext pending = moveToCurrentLocationCallback;
            cancelMoveToCurrentLocation();
            pending.error(reason);
        }
    }

    private void stopUserTracking() {
        stopCameraFollow();
        trackingRequestGeneration++;
        // Stopping camera/path tracking must not stop the visible puck's stream.
        lastUserTrackingUpdateMs = 0L;
        lastAcceptedTrackingLocation = null;
        smoothedTrackingPoint = null;
        movementHistory.clear();
        movementState = MOVEMENT_STATE_STATIONARY;
        movementStateAgreement = 0;
        isUserTrackingEnabled = false;
        fireTrackingStatusChanged();
    }

    private void startCameraFollow() {
        cameraFollowHandler.removeCallbacks(cameraFollowRunnable);
        cameraFollowRunnable = new Runnable() {
            @Override
            public void run() {
                if (mapView != null
                        && isUserTrackingEnabled
                        && isCameraFollowingUser
                        && !isRecenterAnimating
                        && smoothedTrackingPoint != null) {
                    Point target = smoothedTrackingPoint;
                    boolean moved = lastCameraFollowTarget == null
                        || Math.abs(
                            target.latitude()
                            - lastCameraFollowTarget.latitude()) > 1e-9
                        || Math.abs(
                            target.longitude()
                            - lastCameraFollowTarget.longitude()) > 1e-9;
                    if (moved) {
                        CameraOptions cameraOptions =
                            new CameraOptions.Builder()
                                .center(target)
                                .build();
                        CameraAnimationsPlugin cameraAnimations =
                            mapView.getPlugin(
                                Plugin.MAPBOX_CAMERA_PLUGIN_ID
                            );
                        if (cameraAnimations != null) {
                            cameraAnimations.easeTo(
                                cameraOptions,
                                new MapAnimationOptions.Builder()
                                    .duration(CAMERA_FOLLOW_DURATION_MS)
                                    .build(),
                                null
                            );
                        } else {
                            mapView.getMapboxMap()
                                .setCamera(cameraOptions);
                        }
                        lastCameraFollowTarget = target;
                    }
                }
                if (isUserTrackingEnabled) {
                    cameraFollowHandler.postDelayed(
                        this,
                        CAMERA_FOLLOW_INTERVAL_MS
                    );
                }
            }
        };
        cameraFollowHandler.postDelayed(cameraFollowRunnable, 0L);
    }

    /**
     * Runs a recenter flight and holds the camera-follow ticker at bay until it
     * settles. Mapbox cancels any in-flight high-level animation when a new one
     * starts, so the ticker would otherwise interrupt the flight a quarter of
     * the way in. A superseded flight fires onAnimationCancel on its own
     * listener, so the generation check keeps that stale callback from
     * releasing the guard while the newer flight is still running.
     */
    private void runRecenterAnimation(
        CameraOptions cameraOptions,
        MapAnimationOptions animationOptions
    ) {
        final int generation = ++recenterAnimationGeneration;
        isRecenterAnimating = true;

        CameraAnimationsPlugin cameraAnimations = mapView.getPlugin(
            Plugin.MAPBOX_CAMERA_PLUGIN_ID
        );
        if (cameraAnimations == null) {
            mapView.getMapboxMap().setCamera(cameraOptions);
            isRecenterAnimating = false;
            return;
        }

        cameraAnimations.flyTo(
            cameraOptions,
            animationOptions,
            new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (generation == recenterAnimationGeneration) {
                        isRecenterAnimating = false;
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (generation == recenterAnimationGeneration) {
                        isRecenterAnimating = false;
                    }
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            }
        );
    }

    private void stopCameraFollow() {
        cameraFollowHandler.removeCallbacksAndMessages(null);
        lastCameraFollowTarget = null;
        isRecenterAnimating = false;
    }

    private void moveToCurrentLocation(JSONObject options, CallbackContext callback) {
        isCameraFollowingUser = true;    
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized  moveToCurrentLocation.");
                return;
            }

            moveToCurrentLocationZoomSet = options.has("zoom");
            moveToCurrentLocationZoom = options.optDouble("zoom", 0.0);

            if (!hasLocationPermission()) {
                callback.error("Location permission is not granted.");
                return;
            }

            startMoveToCurrentLocation(callback);
        });
    }

    private String recenterLocationProblem(Location location) {
        if (location == null) return "No location fix was returned.";
        if (!isValidLatitude(location.getLatitude()) || !isValidLongitude(location.getLongitude())) {
            return "Location coordinates are invalid.";
        }
        if (!location.hasAccuracy()) return "Location accuracy is unavailable.";
        if (!Float.isFinite(location.getAccuracy()) || location.getAccuracy() < 0) {
            return "Location accuracy is invalid.";
        }
        long fixNanos = location.getElapsedRealtimeNanos();
        if (fixNanos <= 0) return "Location timestamp is unavailable.";
        long ageNanos = android.os.SystemClock.elapsedRealtimeNanos() - fixNanos;
        if (ageNanos < 0) return "Location timestamp is in the future.";
        if (ageNanos > MAX_LOCATION_AGE_MS * 1_000_000L) {
            return "Location fix is older than " + (MAX_LOCATION_AGE_MS / 1000L) + " seconds.";
        }
        return null;
    }

    private boolean isUsableRecenterLocation(Location location) {
        return recenterLocationProblem(location) == null;
    }

    private void updateInitialUserLocation(Location location) {
        if (isUserLocationEnabled && smoothedTrackingPoint == null
                && smoothedLocationProvider != null && isUsableRecenterLocation(location)) {
            // Preserve the reported accuracy; an approximate position is not a precise fix.
            currentLocationAccuracy = location.getAccuracy();
            currentLocationAccuracyLabel = getAccuracyLabel(currentLocationAccuracy);
            smoothedLocationProvider.updateLocation(location);
        }
    }

    private void startMoveToCurrentLocation(CallbackContext callback) {
        if (moveToCurrentLocationCallback != null) moveToCurrentLocationCallback.error("Location request superseded.");
        unregisterMoveToCurrentLocation();
        String problem = locationServiceProblem();
        if (problem != null) { cancelMoveToCurrentLocation(); callback.error(problem); return; }
        moveToCurrentLocationCallback = callback;
        final long generation = sessionGeneration;
        recenterTimeout = () -> {
            if (generation != sessionGeneration || moveToCurrentLocationCallback != callback) return;
            String currentProblem = locationServiceProblem();
            cancelMoveToCurrentLocation();
            callback.error(currentProblem != null ? currentProblem
                : "No valid location update received within 15 seconds.");
        };
        locationWaitHandler.postDelayed(recenterTimeout, LOCATION_WAIT_MS);
        ensureLocationUpdates(new LocationStartCallback() {
            public void ready() {
                if (generation != sessionGeneration || moveToCurrentLocationCallback != callback) return;
                if (isUsableRecenterLocation(latestRecenterLocation)) {
                    completeMoveToCurrentLocation(new Location(latestRecenterLocation));
                }
                // Otherwise the shared stream completes this request on its next valid fix.
            }
            public void failed(String message) {
                if (generation != sessionGeneration || moveToCurrentLocationCallback != callback) return;
                cancelMoveToCurrentLocation();
                callback.error(message);
            }
        });
    }

    private void completeMoveToCurrentLocation(Location location) {
        updateInitialUserLocation(location);
        currentLocationAccuracy =
            location.hasAccuracy() ? location.getAccuracy() : -1f;
        currentLocationAccuracyLabel =
            getAccuracyLabel(currentLocationAccuracy);

        final double latitude = location.getLatitude();
        final double longitude = location.getLongitude();

        if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) {
            if (moveToCurrentLocationCallback != null) {
                moveToCurrentLocationCallback.error(
                    "Invalid coordinates: latitude must be in [-90, 90], longitude in [-180, 180]."
                );
            }
            cancelMoveToCurrentLocation();
            return;
        }

        if (moveToCurrentLocationCallback == null) {
            return;
        }

        final CallbackContext pendingCallback = moveToCurrentLocationCallback;
        final boolean applyZoom = moveToCurrentLocationZoomSet;
        final double zoom = moveToCurrentLocationZoom;

        moveToCurrentLocationCallback = null;
        cancelMoveToCurrentLocation();

        runForSession(() -> {
            if (mapView != null) {
                CameraOptions.Builder cameraBuilder = new CameraOptions.Builder()
                    .center(Point.fromLngLat(longitude, latitude));
                if (applyZoom) {
                    cameraBuilder.zoom(zoom);
                }
                CameraOptions cameraOptions = cameraBuilder.build();
                runRecenterAnimation(
                    cameraOptions,
                    new MapAnimationOptions.Builder()
                        .duration(RECENTER_ANIMATION_DURATION_MS)
                        .build()
                );
                // Seed the follow target so the first tick after the
                // flight lands does not issue a redundant camera move.
                lastCameraFollowTarget = cameraOptions.getCenter();
            }

            JSONObject result = new JSONObject();
            try {
                result.put("latitude", latitude);
                result.put("longitude", longitude);
                result.put("accuracy", currentLocationAccuracy);
                result.put("accuracyLabel", currentLocationAccuracyLabel);
                pendingCallback.success(result);
            } catch (JSONException e) {
                pendingCallback.error(e.getMessage());
            }
        });
    }

    private String getAccuracyLabel(float accuracy) {
        if (accuracy < 0) {
            return "Unknown";
        } else if (accuracy <= VERY_ACCURATE_METERS) {
            return "Very accurate";
        } else if (accuracy <= ACCURATE_METERS) {
            return "Accurate";
        } else if (accuracy <= MODERATE_METERS) {
            return "Moderately";
        } else if (accuracy <= LOW_METERS) {
            return "Low";
        } else {
            return "Poor";
        }
    }

    private void unregisterMoveToCurrentLocation() {
        if (recenterTimeout != null) locationWaitHandler.removeCallbacks(recenterTimeout);
        recenterTimeout = null;
        moveToCurrentLocationCallback = null;
    }

    private void cancelMoveToCurrentLocation() {
        unregisterMoveToCurrentLocation();
        moveToCurrentLocationZoom = 0.0;
        moveToCurrentLocationZoomSet = false;
    }

    private void downloadOfflineRegion(JSONObject options, CallbackContext callback) {
        sendOfflineProgress("started", 0, 100);
        runForSession(() -> {
            try {
                sendOfflineProgress("native-entered", 0, 100);

                if (mapView == null) {
                    callback.error("Map is not initialized downloadOfflineRegion.");
                    return;
                }

                double latitude = options.optDouble("latitude", 0.0);
                double longitude = options.optDouble("longitude", 0.0);

                if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) {
                    callback.error("Invalid coordinates: latitude must be in [-90, 90], longitude in [-180, 180].");
                    return;
                }

                double radiusKm = options.optDouble("radiusKm", 10.0);
                double minZoom = options.optDouble("minZoom", 10.0);
                double maxZoom = options.optDouble("maxZoom", 16.0);
                String styleUrl = options.optString("styleUrl", Style.MAPBOX_STREETS);
            if (!styleAllowed(styleUrl)) {
                callback.error("Style URL is not allowed. Use a Mapbox style or an approved HTTPS host.");
                return;
            }
                String regionId = options.optString(
                    "regionId",
                    "offline-" + Math.round(latitude * 100000.0) + "-" + Math.round(longitude * 100000.0)
                );

                startOfflineDownload(
                    regionId,
                    latitude,
                    longitude,
                    radiusKm,
                    minZoom,
                    maxZoom,
                    styleUrl,
                    null,
                    callback
                );
            } catch (Throwable e) {
                callback.error(sanitizeError("Failed to start offline download.", e));
            }
        });
    }

    private void downloadOfflineRegionForRect(JSONObject options, CallbackContext callback) {
        sendOfflineProgress("started", 0, 100);
        runForSession(() -> {
            try {
                sendOfflineProgress("native-entered", 0, 100);

                if (mapView == null) {
                    callback.error("Map is not initialized downloadOfflineRegionForRect.");
                    return;
                }

                double x = options.optDouble("x", 0.0);
                double y = options.optDouble("y", 0.0);
                double width = options.optDouble("width", 1.0);
                double height = options.optDouble("height", 1.0);
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width) || !Double.isFinite(height)
                        || Math.abs(x) > 1000000 || Math.abs(y) > 1000000 || width <= 0 || height <= 0
                        || width > 1000000 || height > 1000000) {
                    callback.error("Invalid offline rectangle."); return;
                }
                double minZoom = options.optDouble("minZoom", 10.0);
                double maxZoom = options.optDouble("maxZoom", 16.0);
                String styleUrl = options.optString("styleUrl", Style.MAPBOX_STREETS);
            if (!styleAllowed(styleUrl)) {
                callback.error("Style URL is not allowed. Use a Mapbox style or an approved HTTPS host.");
                return;
            }
                String regionId = options.optString("regionId", "offline-rect-" + System.currentTimeMillis());

                Polygon polygon = createRectPolygon(x, y, width, height);
                Point center = mapView.getMapboxMap().coordinateForPixel(
                    new ScreenCoordinate(x + width / 2.0, y + height / 2.0)
                );

                startOfflineDownload(
                    regionId,
                    center.latitude(),
                    center.longitude(),
                    0.0,
                    minZoom,
                    maxZoom,
                    styleUrl,
                    polygon,
                    callback
                );
            } catch (Throwable e) {
                callback.error(sanitizeError("Failed to start offline region download.", e));
            }
        });
    }

    private void startOfflineDownload(
        String regionId,
        double latitude,
        double longitude,
        double radiusKm,
        double minZoom,
        double maxZoom,
        String styleUrl,
        Polygon geometry,
        CallbackContext callback
    ) {
        if (isOfflineDownloading) {
            callback.error("An offline region download is already in progress.");
            return;
        }
        if (!Double.isFinite(radiusKm) || radiusKm < 0 || radiusKm > MAX_OFFLINE_RADIUS_KM
                || !MapboxSecurity.validZoom(minZoom) || !MapboxSecurity.validZoom(maxZoom)
                || minZoom > maxZoom || regionId.isEmpty() || regionId.length() > 256) {
            callback.error("Invalid offline radius, zoom range, or region id."); return;
        }
        Polygon downloadGeometry = geometry == null ? createCirclePolygon(longitude, latitude, radiusKm) : geometry;
        double south = 90, west = 180, north = -90, east = -180;
        for (List<Point> ring : downloadGeometry.coordinates()) {
            for (Point point : ring) {
                south = Math.min(south, point.latitude()); north = Math.max(north, point.latitude());
                west = Math.min(west, point.longitude()); east = Math.max(east, point.longitude());
            }
        }
        if (!MapboxSecurity.offlineBounds(south, west, north, east, maxZoom)) {
            callback.error("Offline region exceeds the geographic or 50000-tile budget."); return;
        }
        final long downloadGeneration = sessionGeneration;
        final double clampedRadiusKm = clamp(radiusKm, 0.0, MAX_OFFLINE_RADIUS_KM);
        final double clampedMinZoom = clamp(minZoom, MIN_OFFLINE_ZOOM, MAX_OFFLINE_ZOOM);
        final double clampedMaxZoom = clamp(maxZoom, clampedMinZoom, MAX_OFFLINE_ZOOM);
        isOfflineDownloading = true;
        cancelCurrentDownload();
        activeOfflineManager = new OfflineManager();
        sendOfflineProgress("style-start", 0, 100);

        StylePackLoadOptions stylePackOptions = new StylePackLoadOptions.Builder()
            .glyphsRasterizationMode(GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY)
            .metadata(metadataValue(regionId))
            .acceptExpired(false)
            .build();

        activeStylePackDownload = activeOfflineManager.loadStylePack(
            styleUrl,
            stylePackOptions,
            progress -> runForGeneration(downloadGeneration, () -> sendOfflineProgress("style", progress.getCompletedResourceCount(), progress.getRequiredResourceCount())),
            expectedStylePack -> runForGeneration(downloadGeneration, () -> expectedStylePack.fold(
                error -> {
                    if (downloadGeneration != sessionGeneration) return null;
                    isOfflineDownloading = false;
                    callback.error(sanitizeError("Failed to download style pack.", error));
                    return null;
                },
                stylePack -> {
                    if (downloadGeneration != sessionGeneration) return null;
                    sendOfflineProgress("tiles-start", 0, 100);
                    downloadOfflineTiles(
                        activeOfflineManager,
                        downloadGeneration,
                        regionId,
                        latitude,
                        longitude,
                        clampedRadiusKm,
                        clampedMinZoom,
                        clampedMaxZoom,
                        styleUrl,
                        geometry,
                        callback
                    );
                    return null;
                }
            ))
        );
    }

    private void downloadOfflineTiles(
        OfflineManager offlineManager,
        long downloadGeneration,
        String regionId,
        double latitude,
        double longitude,
        double radiusKm,
        double minZoom,
        double maxZoom,
        String styleUrl,
        Polygon geometry,
        CallbackContext callback
    ) {
        runForSession(() -> {
            try {
                if (downloadGeneration != sessionGeneration) return;
                TilesetDescriptorOptions descriptorOptions = new TilesetDescriptorOptions.Builder()
                    .styleURI(styleUrl)
                    .pixelRatio(cordova.getActivity().getResources().getDisplayMetrics().density)
                    .minZoom(toZoomByte(minZoom))
                    .maxZoom(toZoomByte(maxZoom))
                    .build();

                TilesetDescriptor descriptor = offlineManager.createTilesetDescriptor(descriptorOptions);

                TileRegionLoadOptions tileRegionOptions = new TileRegionLoadOptions.Builder()
                    .geometry(geometry == null ? createCirclePolygon(longitude, latitude, radiusKm) : geometry)
                    .descriptors(Collections.singletonList(descriptor))
                    .metadata(metadataValue(regionId))
                    .acceptExpired(false)
                    .build();

                activeOfflineTileStore = TileStore.create();
                activeTileRegionDownload = activeOfflineTileStore.loadTileRegion(
                    regionId,
                    tileRegionOptions,
                    progress -> runForGeneration(downloadGeneration, () -> sendOfflineProgress("tiles", progress.getCompletedResourceCount(), progress.getRequiredResourceCount())),
                    expectedTileRegion -> runForGeneration(downloadGeneration, () -> expectedTileRegion.fold(
                        error -> {
                            if (downloadGeneration != sessionGeneration) return null;
                            isOfflineDownloading = false;
                            callback.error(sanitizeError("Failed to download tile region.", error));
                            return null;
                        },
                        tileRegion -> {
                            if (downloadGeneration != sessionGeneration) return null;
                            isOfflineDownloading = false;
                            try {
                                JSONObject result = new JSONObject();
                                result.put("regionId", regionId);
                                result.put("latitude", latitude);
                                result.put("longitude", longitude);
                                result.put("radiusKm", radiusKm);
                                callback.success(result);
                            } catch (Exception e) {
                                callback.error(sanitizeError("An internal error occurred.", e));
                            }
                            return null;
                        }
                    ))
                );
            } catch (Throwable e) {
                isOfflineDownloading = false;
                callback.error(sanitizeError("Failed to download offline tiles.", e));
            }
        });
    }

    private Polygon createRectPolygon(double x, double y, double width, double height) {
        Point topLeft = mapView.getMapboxMap().coordinateForPixel(new ScreenCoordinate(x, y));
        Point topRight = mapView.getMapboxMap().coordinateForPixel(new ScreenCoordinate(x + width, y));
        Point bottomRight = mapView.getMapboxMap().coordinateForPixel(new ScreenCoordinate(x + width, y + height));
        Point bottomLeft = mapView.getMapboxMap().coordinateForPixel(new ScreenCoordinate(x, y + height));

        List<Point> points = new ArrayList<>();
        points.add(topLeft);
        points.add(topRight);
        points.add(bottomRight);
        points.add(bottomLeft);
        points.add(topLeft);

        return Polygon.fromLngLats(Collections.singletonList(points));
    }

    private Polygon createCirclePolygon(double longitude, double latitude, double radiusKm) {
        List<Point> points = new ArrayList<>();
        double earthRadiusKm = 6371.0088;
        double angularDistance = radiusKm / earthRadiusKm;
        double latitudeRad = Math.toRadians(latitude);
        double longitudeRad = Math.toRadians(longitude);

        for (int i = 0; i <= 64; i++) {
            double bearing = 2.0 * Math.PI * i / 64.0;
            double pointLatitude = Math.asin(
                Math.sin(latitudeRad) * Math.cos(angularDistance)
                    + Math.cos(latitudeRad) * Math.sin(angularDistance) * Math.cos(bearing)
            );
            double pointLongitude = longitudeRad + Math.atan2(
                Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(latitudeRad),
                Math.cos(angularDistance) - Math.sin(latitudeRad) * Math.sin(pointLatitude)
            );

            points.add(Point.fromLngLat(Math.toDegrees(pointLongitude), Math.toDegrees(pointLatitude)));
        }

        return Polygon.fromLngLats(Collections.singletonList(points));
    }

    private Value metadataValue(String regionId) {
        HashMap<String, Value> metadata = new HashMap<>();
        metadata.put("regionId", Value.valueOf(regionId));
        return Value.valueOf(metadata);
    }

    private void showOfflineRegion(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized showOfflineRegion.");
                return;
            }

            double latitude = options.optDouble("latitude", 0.0);
            double longitude = options.optDouble("longitude", 0.0);

            if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) {
                callback.error("Invalid coordinates: latitude must be in [-90, 90], longitude in [-180, 180].");
                return;
            }

            double zoom = options.optDouble("zoom", 13.0);
            String styleUrl = options.optString("styleUrl", Style.MAPBOX_STREETS);
            if (!styleAllowed(styleUrl)) {
                callback.error("Style URL is not allowed. Use a Mapbox style or an approved HTTPS host.");
                return;
            }


            mapView.getMapboxMap().setCamera(new CameraOptions.Builder()
                .center(Point.fromLngLat(longitude, latitude))
                .zoom(zoom)
                .build());

            loadReadyStyle(styleUrl, null, callback);
        });
    }

    private void deleteOfflineRegion(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            try {
                String regionId = options.optString("regionId", "");
                String styleUrl = options.optString("styleUrl", Style.MAPBOX_STREETS);
            if (!styleAllowed(styleUrl)) {
                callback.error("Style URL is not allowed. Use a Mapbox style or an approved HTTPS host.");
                return;
            }
                boolean deleteStylePack = options.optBoolean("deleteStylePack", true);

                if (regionId.trim().isEmpty()) {
                    callback.error("regionId is required.");
                    return;
                }

                TileStore tileStore = TileStore.create();
                tileStore.removeTileRegion(regionId);

                if (deleteStylePack) {
                    OfflineManager offlineManager = new OfflineManager();
                    offlineManager.removeStylePack(styleUrl);
                }

                callback.success();
            } catch (Throwable e) {
                callback.error(sanitizeError("Failed to delete offline region.", e));
            }
        });
    }

    private void registerOfflineDownloadProgressCallback(CallbackContext callback) {
        releaseCallback(offlineDownloadProgressCallback);
        offlineDownloadProgressCallback = callback;
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        callback.sendPluginResult(result);
    }

    private void sendOfflineProgress(String phase, long completed, long required) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("type", "offlineDownloadProgress");
            payload.put("phase", phase);
            payload.put("completed", completed);
            payload.put("required", required);
            payload.put("percent", required > 0 ? Math.round((completed * 100.0) / required) : 0);
            long now = System.currentTimeMillis();
            if (now - lastKeepCallbackOfflineMs >= CALLBACK_RATE_LIMIT_MS) {
                lastKeepCallbackOfflineMs = now;
                sendKeepCallback(offlineDownloadProgressCallback, payload);
            }
        } catch (Exception ignored) {
        }
    }

    private void setWaypointSelectionEnabled(JSONObject options, CallbackContext callback) {
        installMapClickListener();
        waypointSelectionEnabled = options.optBoolean("enabled", true);
        autoAddWaypointMarker = options.optBoolean("autoAddMarker", false);
        callback.success();
    }

    private void registerWaypointSelectedCallback(CallbackContext callback) {
        releaseCallback(waypointSelectedCallback);
        waypointSelectedCallback = callback;
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        callback.sendPluginResult(result);
    }

    private void registerMarkerClickCallback(CallbackContext callback) {
        releaseCallback(markerClickCallback);
        markerClickCallback = callback;
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        callback.sendPluginResult(result);
    }

    private void registerMapTapCallback(CallbackContext callback) {
        releaseCallback(mapTapCallback);
        mapTapCallback = callback;
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        callback.sendPluginResult(result);
    }

    private void registerBoundaryAreaCallback(CallbackContext callback) {
        releaseCallback(boundaryAreaCallback);
        boundaryAreaCallback = callback;
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        callback.sendPluginResult(result);
    }

    private void registerTrackingStatusCallback(CallbackContext callback) {
        releaseCallback(trackingStatusCallback);
        trackingStatusCallback = callback;
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        callback.sendPluginResult(result);
        fireTrackingStatusChanged();
    }

    private void registerLocationAccuracyCallback(CallbackContext callback) {
        runForSession(() -> {
            if (!hasLocationPermission()) {
                callback.error("Location permission is not granted.");
                return;
            }

            stopLocationAccuracyMonitoring();
            releaseCallback(locationAccuracyCallback);
        locationAccuracyCallback = callback;
            lastLocationAccuracyUpdateMs = 0L;

            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callback.sendPluginResult(result);

            accuracyFusedLocationClient = LocationServices
                .getFusedLocationProviderClient(cordova.getActivity());
            final long accuracyGeneration = sessionGeneration;
            accuracyFusedLocationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (accuracyGeneration != sessionGeneration || this != accuracyFusedLocationCallback) return;
                    if (locationResult == null || locationResult.getLastLocation() == null) {
                        return;
                    }
                    sendLocationAccuracyUpdate(locationResult.getLastLocation());
                }
            };

            LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000L
            ).setMinUpdateIntervalMillis(500L).build();

            try {
                accuracyFusedLocationClient.requestLocationUpdates(
                    request,
                    accuracyFusedLocationCallback,
                    Looper.getMainLooper()
                );
            } catch (SecurityException e) {
                stopLocationAccuracyMonitoring();
                callback.error("Location permission is not granted.");
            }
        });
    }

    private void stopLocationAccuracyMonitoring() {
        if (accuracyFusedLocationClient != null && accuracyFusedLocationCallback != null) {
            try { accuracyFusedLocationClient.removeLocationUpdates(accuracyFusedLocationCallback); }
            catch (SecurityException ignored) { }
        }
        accuracyFusedLocationClient = null;
        accuracyFusedLocationCallback = null;
    }

    private void registerCompassCallback(CallbackContext callback) {
        releaseCallback(compassCallback);
        compassCallback = callback;

        PluginResult result =
            new PluginResult(PluginResult.Status.NO_RESULT);

        result.setKeepCallback(true);
        callback.sendPluginResult(result);

        startCompassCameraListener();
    }

    private void startCompassCameraListener() {
        if (mapView == null) {
            return;
        }

        if (compassCameraSubscription != null) {
            compassCameraSubscription.cancel();
            compassCameraSubscription = null;
        }

        lastCompassBearing = Double.NaN;
        lastCompassVisible = false;

        final long compassGeneration = sessionGeneration;
        compassCameraSubscription =
            mapView.getMapboxMap().subscribeCameraChanged(cameraChanged -> {

                if (compassGeneration != sessionGeneration || compassCallback == null || mapView == null) {
                    return;
                }

                double bearing =
                    mapView.getMapboxMap()
                        .getCameraState()
                        .getBearing();

                bearing = ((bearing % 360.0) + 360.0) % 360.0;

                double distanceFromNorth =
                    Math.min(bearing, 360.0 - bearing);

                boolean visible = distanceFromNorth > 1.0;

                if (!Double.isNaN(lastCompassBearing)
                        && Math.abs(bearing - lastCompassBearing) < 0.5
                        && visible == lastCompassVisible) {
                    return;
                }

                lastCompassBearing = bearing;
                lastCompassVisible = visible;

                try {
                    JSONObject payload = new JSONObject();

                    payload.put("bearing", bearing);
                    payload.put("visible", visible);

                    sendKeepCallback(compassCallback, payload);

                } catch (Exception ignored) {
                }
            });
    }

    private void resetMapBearing(CallbackContext callback) {
        cordova.getActivity().runOnUiThread(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized resetMapBearing.");
                return;
            }

            CameraAnimationsPlugin cameraAnimations =
                mapView.getPlugin(Plugin.MAPBOX_CAMERA_PLUGIN_ID);

            if (cameraAnimations == null) {
                callback.error("Camera animations plugin is not available.");
                return;
            }

            CameraOptions cameraOptions = new CameraOptions.Builder()
                .bearing(0.0)
                .build();

            MapAnimationOptions animationOptions =
                new MapAnimationOptions.Builder()
                    .duration(400L)
                    .build();

            cameraAnimations.easeTo(
                cameraOptions,
                animationOptions,
                null
            );

            callback.success();
        });
    }

    private void sendLocationAccuracyUpdate(Location location) {
        if (locationAccuracyCallback == null || location == null) {
            return;
        }

        if (!location.hasAccuracy()) {
            return;
        }

        long now = System.currentTimeMillis();

        if (now - lastLocationAccuracyUpdateMs
                < LOCATION_ACCURACY_CALLBACK_INTERVAL_MS) {
            return;
        }

        lastLocationAccuracyUpdateMs = now;

        int accuracy = Math.round(location.getAccuracy());

        try {
            JSONObject payload = new JSONObject();

            payload.put("type", "locationAccuracy");
            payload.put("accuracy", accuracy);
            payload.put("latitude", location.getLatitude());
            payload.put("longitude", location.getLongitude());
            payload.put("timestamp", now);

            sendKeepCallback(locationAccuracyCallback, payload);

        } catch (Exception ignored) {
        }
    }

    private void fireTrackingStatusChanged() {
        if (trackingStatusCallback == null) {
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("userLocation", isUserLocationEnabled);
            payload.put("userTracking", isUserTrackingEnabled);
            payload.put("deviceHeading", isDeviceHeadingEnabled);
            payload.put("headingFollowMode", isHeadingFollowModeEnabled);
            sendKeepCallback(trackingStatusCallback, payload);
        } catch (Exception ignored) {
        }
    }

    private void installMapClickListener() {
        if (mapView == null) {
            return;
        }

        if (mapClickListener != null) {
            GesturesPlugin oldGestures = mapView.getPlugin(Plugin.MAPBOX_GESTURES_PLUGIN_ID);
            if (oldGestures != null) {
                oldGestures.removeOnMapClickListener(mapClickListener);
            }
        }

        GesturesPlugin gestures = mapView.getPlugin(Plugin.MAPBOX_GESTURES_PLUGIN_ID);
        if (gestures == null) {
            return;
        }

        final long generation = sessionGeneration;
        mapClickListener = point -> {
            if (generation != sessionGeneration || mapView == null) return false;
            return handleMapPointSelected(point);
        };

        gestures.addOnMapClickListener(mapClickListener);
    }

    private boolean handleMapPointSelected(Point point) {
        long now = System.currentTimeMillis();
        if (isBoundaryDrawing) {
            addBoundaryDrawingPoint(point);
            lastMapSelectionCallbackMs = now;
            return true;
        }

        if (now - lastMapSelectionCallbackMs < 400L) {
            return true;
        }

        if (sendMarkerClickIfNear(point)) {
            lastMapSelectionCallbackMs = now;
            return true;
        }

        if (mapTapCallback != null) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("type", "mapTapped");
                payload.put("latitude", point.latitude());
                payload.put("longitude", point.longitude());
                sendKeepCallback(mapTapCallback, payload);
                lastMapSelectionCallbackMs = now;
            } catch (Exception e) {
                Log.e("MapboxPlugin", "Map tap callback failed", e);
            }
        }

        if (!waypointSelectionEnabled) {
            return mapTapCallback != null;
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
            if (now - lastKeepCallbackWaypointMs >= CALLBACK_RATE_LIMIT_MS) {
                lastKeepCallbackWaypointMs = now;
                sendKeepCallback(waypointSelectedCallback, payload);
            }
            lastMapSelectionCallbackMs = now;
        } catch (Exception ignored) {
        }

        return true;
    }

    private void handleRawTouchForTap(MotionEvent event) {
        if (mapView == null) {
            return;
        }

        // During boundary drawing, let Mapbox's gesture listener
        // handle the tap. Avoid processing the same tap twice.
        if (isBoundaryDrawing) {
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
        final long generation = sessionGeneration;
        markerTapListener = annotation -> {
            if (generation != sessionGeneration || mapView == null) return true;
            String markerId = markerRecordIds.get(annotation.getId());
            if (markerId == null || markerClickCallback == null) {
                return true;
            }

            try {
                MarkerStyle style = markerStylesByRecordId.get(markerId);
                String type = (style != null && style.isFind) ? "Find" : "Waypoint";

                JSONObject payload = new JSONObject();
                payload.put("id", markerId);
                payload.put("type", type);
                payload.put("isOnline", style == null || style.isOnline);

                long now = System.currentTimeMillis();
                if (now - lastKeepCallbackMarkerMs >= CALLBACK_RATE_LIMIT_MS) {
                    lastKeepCallbackMarkerMs = now;
                    sendKeepCallback(markerClickCallback, payload);
                }
            } catch (Exception e) {
                Log.e("MapboxPlugin", "Failed to handle marker click", e);
            }

            return true;
        };
        pointAnnotationManager.addClickListener(markerTapListener);

        return true;
    }

    private boolean ensureBoundaryAnnotationManager() {
        if (mapView == null) {
            return false;
        }

        if (boundaryAnnotationManager != null) {
            return true;
        }

        AnnotationPlugin annotationPlugin = mapView.getPlugin(Plugin.MAPBOX_ANNOTATION_PLUGIN_ID);
        if (annotationPlugin == null) {
            return false;
        }

        boundaryAnnotationManager = PolygonAnnotationManagerKt.createPolygonAnnotationManager(annotationPlugin, null);
        return true;
    }

    private void startBoundaryDrawing(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized.");
                return;
            }

            if (!ensureBoundaryDrawingLineManager()
                    || !ensureBoundaryDrawingPointManager()
                    || !ensureBoundaryAnnotationManager()) {
                callback.error("Unable to initialize drawing managers.");
                return;
            }

            clearBoundaryDrawingInternal();

            isBoundaryDrawing = true;

            callback.success();
        });
    }

    private void addBoundaryDrawingPoint(Point point) {
        if (!isBoundaryDrawing || point == null) return;

        boundaryDrawingPoints.add(point);

        redrawBoundaryDrawing();
        notifyBoundaryAreaChanged();
    }

    private void undoBoundaryPoint(CallbackContext callback) {
        runForSession(() -> {
            if (!isBoundaryDrawing) {
                callback.error("Boundary drawing is not active.");
                return;
            }

            if (boundaryDrawingPoints.isEmpty()) {
                callback.error("No points available to undo.");
                return;
            }

            // Remove only the most recently added vertex.
            int lastIndex = boundaryDrawingPoints.size() - 1;
            boundaryDrawingPoints.remove(lastIndex);

            // Rebuild the drawing using the remaining vertices.
            redrawBoundaryDrawing();

            callback.success();
        });
    }

    private void saveBoundaryDrawing(
        JSONObject options,
        CallbackContext callback
    ) {
        runForSession(() -> {
            if (!isBoundaryDrawing) {
                callback.error("Boundary drawing is not active.");
                return;
            }

            if (boundaryDrawingPoints.size() < 3) {
                callback.error("At least 3 points are required.");
                return;
            }

            try {
                JSONArray coordinates = new JSONArray();

                for (Point point : boundaryDrawingPoints) {
                    JSONObject coordinate = new JSONObject();

                    coordinate.put("lat", point.latitude());
                    coordinate.put("lon", point.longitude());

                    coordinates.put(coordinate);
                }

                JSONObject result = new JSONObject();

                result.put("name", options.optString("name", ""));
                result.put("geometry", coordinates);
                result.put("count", boundaryDrawingPoints.size());
                result.put(
                    "areaAcres",
                    calculateBoundaryAreaAcres(boundaryDrawingPoints)
                );

                // Preserve the completed polygon on the map.
                if (boundaryDrawingPolygon != null) {
                    completedBoundaryDrawingPolygons.add(
                        boundaryDrawingPolygon
                    );

                    // Detach it from the active drawing session.
                    boundaryDrawingPolygon = null;
                }

                // Remove only the active drawing line.
                if (boundaryDrawingLine != null
                        && boundaryDrawingLineManager != null) {
                    boundaryDrawingLineManager.delete(boundaryDrawingLine);
                    boundaryDrawingLine = null;
                }

                // Remove only the active vertex markers.
                if (boundaryDrawingPointManager != null) {
                    for (CircleAnnotation annotation :
                            new ArrayList<>(boundaryDrawingPointAnnotations)) {
                        boundaryDrawingPointManager.delete(annotation);
                    }
                }

                boundaryDrawingPointAnnotations.clear();

                // Reset active drawing state.
                boundaryDrawingPoints.clear();
                isBoundaryDrawing = false;

                callback.success(result);
            } catch (Exception e) {
                callback.error("Failed to save boundary coordinates.");
            }
        });
    }

    private double calculateBoundaryAreaAcres(List<Point> points) {
        if (points == null || points.size() < 3) {
            return 0.0;
        }

        final double earthRadius = 6378137.0;
        double total = 0.0;

        for (int i = 0; i < points.size(); i++) {
            Point current = points.get(i);
            Point next = points.get((i + 1) % points.size());

            double lat1 = Math.toRadians(current.latitude());
            double lat2 = Math.toRadians(next.latitude());

            double lon1 = Math.toRadians(current.longitude());
            double lon2 = Math.toRadians(next.longitude());

            total += (lon2 - lon1) *
                (2.0 + Math.sin(lat1) + Math.sin(lat2));
        }

        double areaSquareMeters =
            Math.abs(total * earthRadius * earthRadius / 2.0);

        return areaSquareMeters / 4046.8564224;
    }

    private void notifyBoundaryAreaChanged() {
        if (boundaryAreaCallback == null) {
            return;
        }

        try {
            JSONObject result = new JSONObject();

            double areaAcres =
                calculateBoundaryAreaAcres(boundaryDrawingPoints);

            result.put("areaAcres", areaAcres);
            result.put("pointCount", boundaryDrawingPoints.size());

            sendKeepCallback(boundaryAreaCallback, result);
        } catch (Exception e) {
            Log.e("MapboxPlugin", "Boundary area callback failed", e);
        }
    }

    private void cancelBoundaryDrawing(CallbackContext callback) {
        runForSession(() -> {
            clearBoundaryDrawingInternal();
            callback.success();
        });
    }

    private void clearBoundaryDrawingInternal() {
        isBoundaryDrawing = false;

        boundaryDrawingPoints.clear();

        if (boundaryDrawingLine != null
                && boundaryDrawingLineManager != null) {
            boundaryDrawingLineManager.delete(boundaryDrawingLine);
            boundaryDrawingLine = null;
        }

        if (boundaryDrawingPointManager != null) {
            for (CircleAnnotation annotation :
                    new ArrayList<>(boundaryDrawingPointAnnotations)) {
                boundaryDrawingPointManager.delete(annotation);
            }
        }

        boundaryDrawingPointAnnotations.clear();

        if (boundaryDrawingPolygon != null
                && boundaryDrawingPolygonManager != null) {
            boundaryDrawingPolygonManager.delete(boundaryDrawingPolygon);
            boundaryDrawingPolygon = null;
        }
    }

    private void redrawBoundaryDrawing() {
        if (boundaryDrawingLineManager == null
                || boundaryDrawingPointManager == null) {
            return;
        }

        if (boundaryDrawingLine != null) {
            boundaryDrawingLineManager.delete(boundaryDrawingLine);
            boundaryDrawingLine = null;
        }

        for (CircleAnnotation annotation :
                new ArrayList<>(boundaryDrawingPointAnnotations)) {
            boundaryDrawingPointManager.delete(annotation);
        }

        boundaryDrawingPointAnnotations.clear();

        if (boundaryDrawingPolygon != null
                && boundaryDrawingPolygonManager != null) {
            boundaryDrawingPolygonManager.delete(boundaryDrawingPolygon);
            boundaryDrawingPolygon = null;
        }

        if (boundaryDrawingPoints.isEmpty()) return;

        if (boundaryDrawingPoints.size() >= 2) {
            boundaryDrawingLine =
                boundaryDrawingLineManager.create(
                    new PolylineAnnotationOptions()
                        .withPoints(new ArrayList<>(boundaryDrawingPoints))
                        .withLineColor("#FF0000")
                        .withLineWidth(DEFAULT_BOUNDARY_LINE_WIDTH)
                );
        }

        for (Point point : boundaryDrawingPoints) {
            CircleAnnotationOptions pointOptions =
                new CircleAnnotationOptions()
                    .withPoint(point)
                    .withCircleColor("#FF0000")
                    .withCircleRadius(6.0)
                    .withCircleStrokeColor("#FFFFFF")
                    .withCircleStrokeWidth(2.0);

            boundaryDrawingPointAnnotations.add(
                boundaryDrawingPointManager.create(pointOptions)
            );
        }

        if (boundaryDrawingPoints.size() >= 3) {
            List<Point> closedRing =
                new ArrayList<>(boundaryDrawingPoints);

            closedRing.add(boundaryDrawingPoints.get(0));

            if (ensureBoundaryAnnotationManager()) {
                boundaryDrawingPolygonManager = boundaryAnnotationManager;
                boundaryDrawingPolygon =
                    boundaryDrawingPolygonManager.create(
                        new PolygonAnnotationOptions()
                            .withPoints(Collections.singletonList(closedRing))
                            .withFillColor("#FF0000")
                            .withFillOpacity(DEFAULT_BOUNDARY_FILL_OPACITY)
                            .withFillOutlineColor("#FF0000")
                    );
            }
        }
    }

    private boolean ensureBoundaryDrawingLineManager() {
        if (mapView == null) return false;

        if (boundaryDrawingLineManager != null) return true;

        AnnotationPlugin annotationPlugin =
            mapView.getPlugin(Plugin.MAPBOX_ANNOTATION_PLUGIN_ID);

        if (annotationPlugin == null) return false;

        boundaryDrawingLineManager =
            PolylineAnnotationManagerKt.createPolylineAnnotationManager(
                annotationPlugin,
                null
            );

        return true;
    }

    private boolean ensureBoundaryDrawingPointManager() {
        if (mapView == null) return false;

        if (boundaryDrawingPointManager != null) return true;

        AnnotationPlugin annotationPlugin =
            mapView.getPlugin(Plugin.MAPBOX_ANNOTATION_PLUGIN_ID);

        if (annotationPlugin == null) return false;

        boundaryDrawingPointManager =
            CircleAnnotationManagerKt.createCircleAnnotationManager(
                annotationPlugin,
                null
            );

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
            MarkerStyle style = markerStylesByRecordId.get(nearestId);
            String type = (style != null && style.isFind) ? "Find" : "Waypoint";

            JSONObject payload = new JSONObject();
            payload.put("id", nearestId);
            payload.put("type", type);
            payload.put("isOnline", style == null || style.isOnline);
            long now = System.currentTimeMillis();
            if (now - lastKeepCallbackMarkerMs >= CALLBACK_RATE_LIMIT_MS) {
                lastKeepCallbackMarkerMs = now;
                sendKeepCallback(markerClickCallback, payload);
            }
        } catch (Exception ignored) {
        }

        return true;
    }

    private void addMarker(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized addMarker.");
                return;
            }

            String id = options.optString("id", String.valueOf(System.currentTimeMillis()));
            double latitude = options.optDouble("latitude", 0.0);
            double longitude = options.optDouble("longitude", 0.0);
            MarkerStyle style = markerStyle(
                options.optBoolean("isFind", false),
                options.optString("pinColor", null),
                options.optString("imageUrl", ""),
                "photo".equals(options.optString("pinStyle", "")),
                options.optBoolean("isOnline", true)
            );

            if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) {
                callback.error("Invalid coordinates: latitude must be in [-90, 90], longitude in [-180, 180].");
                return;
            }

            if (style == null) {
                callback.error(MARKER_IMAGE_NOT_ALLOWED);
                return;
            }

            if (!addMarkerInternal(id, latitude, longitude, style)) {
                callback.error("Marker unavailable or marker/id limit exceeded.");
                return;
            }

            try {
                JSONObject result = new JSONObject();
                result.put("id", id);
                callback.success(result);
            } catch (Exception e) {
                callback.error(sanitizeError("An internal error occurred.", e));
            }
        });
    }

    private void loadMarkers(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized loadMarkers.");
                return;
            }

            if (!ensurePointAnnotationManager()) {
                callback.error("Marker unavailable or marker/id limit exceeded.");
                return;
            }

            JSONArray markers = options.optJSONArray("markers");
            if (markers == null) { callback.success(); return; }
            java.util.Set<String> ids = new java.util.HashSet<>();
            if (!options.optBoolean("replace", true)) ids.addAll(markerAnnotationsByRecordId.keySet());
            MarkerStyle[] styles = new MarkerStyle[markers.length()];
            for (int i = 0; i < markers.length(); i++) {
                JSONObject marker = markers.optJSONObject(i);
                if (marker == null) continue;
                String id = marker.optString("Id", String.valueOf(i));
                if (id.isEmpty() || id.length() > 256) { callback.error("Invalid marker id."); return; }
                styles[i] = markerStyle(
                    marker.optBoolean("IsFind", false),
                    marker.optString("PinColor", null),
                    marker.optString("ImageUrl", ""),
                    "photo".equals(marker.optString("PinStyle", "")),
                    marker.optBoolean("IsOnline", true)
                );
                if (styles[i] == null) { callback.error(MARKER_IMAGE_NOT_ALLOWED); return; }
                ids.add(id);
            }
            if (markers.length() > MAX_MARKERS || ids.size() > MAX_MARKERS) {
                callback.error("Too many markers: maximum total is " + MAX_MARKERS + ".");
                return;
            }
            if (options.optBoolean("replace", true)) clearMarkersInternal();

            for (int i = 0; i < markers.length(); i++) {
                JSONObject marker = markers.optJSONObject(i);
                if (marker == null) {
                    continue;
                }

                double markerLat = marker.optDouble("Latitude", 0.0);
                double markerLng = marker.optDouble("Longitude", 0.0);
                if (!isValidLatitude(markerLat) || !isValidLongitude(markerLng)) {
                    continue;
                }

                addMarkerInternal(
                    marker.optString("Id", String.valueOf(i)),
                    markerLat,
                    markerLng,
                    styles[i]
                );
            }

            callback.success();
        });
    }

private boolean addMarkerInternal(
        String id,
        double latitude,
        double longitude) {

    return addMarkerInternal(id, latitude, longitude, MarkerStyle.DEFAULT);
}

private boolean addMarkerInternal(
        String id,
        double latitude,
        double longitude,
        MarkerStyle style) {

    if (!ensurePointAnnotationManager()) {
        return false;
    }

    if (id == null || id.isEmpty() || id.length() > 256
            || (!markerAnnotationsByRecordId.containsKey(id)
                && markerAnnotationsByRecordId.size() >= MAX_MARKERS)) {
        return false;
    }

    removeMarkerInternal(id);

    PointAnnotationOptions markerOptions = new PointAnnotationOptions()
        .withPoint(Point.fromLngLat(longitude, latitude))
        .withIconImage(style.photo
            ? markerIcons.photoPin(style.color, style.imageUrl,
                cordova.getActivity().getResources().getDisplayMetrics().density)
            : markerIcons.pin(style.color, style.isFind, style.imageUrl,
                cordova.getActivity().getResources().getDisplayMetrics().density))
        .withIconAnchor(IconAnchor.BOTTOM)
        .withIconSize(style.photo ? 0.27 : 0.5);

    PointAnnotation annotation =
        pointAnnotationManager.create(markerOptions);

    markerRecordIds.put(annotation.getId(), id);
    markerAnnotationsByRecordId.put(id, annotation);
    markerPointsByRecordId.put(
        id,
        Point.fromLngLat(longitude, latitude)
    );
    markerStylesByRecordId.put(id, style);

    if (style.imageUrl != null && !markerIcons.hasImage(style.imageUrl)) {
        requestMarkerImage(id, style.imageUrl);
    }

    return true;
}

    /** Returns null when imageUrl is not an allowed image source. */
    private MarkerStyle markerStyle(boolean isFind, String pinColor, String rawImageUrl) {
        return markerStyle(isFind, pinColor, rawImageUrl, false);
    }

    private MarkerStyle markerStyle(boolean isFind, String pinColor, String rawImageUrl, boolean photo) {
        return markerStyle(isFind, pinColor, rawImageUrl, photo, true);
    }

    private MarkerStyle markerStyle(boolean isFind, String pinColor, String rawImageUrl, boolean photo, boolean isOnline) {
        int defaultColor = isFind ? MarkerIcons.FIND_PIN_COLOR
            : photo ? MarkerIcons.PHOTO_PIN_COLOR : MarkerIcons.DEFAULT_PIN_COLOR;
        int color = MarkerIcons.parseColor(pinColor, defaultColor);
        String imageUrl = rawImageUrl == null ? "" : rawImageUrl.trim();
        if (imageUrl.isEmpty()) {
            return new MarkerStyle(color, isFind, null, photo, isOnline);
        }
        if (!MapboxSecurity.markerImageAllowed(imageUrl, allowedMarkerImageHosts())) {
            return null;
        }
        return new MarkerStyle(color, isFind, imageUrl, photo, isOnline);
    }

    private String allowedMarkerImageHosts() {
        return preferences.getString("MAPBOX_ALLOWED_MARKER_IMAGE_HOSTS", "");
    }

    // Markers show a plain pin until their image arrives; one download serves every marker using it.
    private void requestMarkerImage(String id, String imageUrl) {
        List<String> waiting = pendingMarkerImages.get(imageUrl);
        if (waiting != null) {
            waiting.add(id);
            return;
        }

        waiting = new ArrayList<>();
        waiting.add(id);
        pendingMarkerImages.put(imageUrl, waiting);

        long generation = sessionGeneration;
        markerIcons.load(imageUrl, allowedMarkerImageHosts(), image ->
            runForGeneration(generation, () -> applyMarkerImage(imageUrl, image)));
    }

    private void applyMarkerImage(String imageUrl, Bitmap image) {
        List<String> waiting = pendingMarkerImages.remove(imageUrl);
        if (waiting == null || image == null || pointAnnotationManager == null) {
            return;
        }

        List<PointAnnotation> changed = new ArrayList<>();
        for (String id : waiting) {
            PointAnnotation annotation = markerAnnotationsByRecordId.get(id);
            MarkerStyle style = markerStylesByRecordId.get(id);
            if (annotation == null || style == null || !imageUrl.equals(style.imageUrl)) {
                continue;
            }
            float density = cordova.getActivity().getResources().getDisplayMetrics().density;
            annotation.setIconImageBitmap(style.photo
                ? markerIcons.photoPin(style.color, imageUrl, image, density)
                : markerIcons.pin(style.color, style.isFind, imageUrl, image, density));
            changed.add(annotation);
        }

        if (!changed.isEmpty()) {
            pointAnnotationManager.update(changed);
        }
    }

    private static final class MarkerStyle {
        static final MarkerStyle DEFAULT = new MarkerStyle(MarkerIcons.DEFAULT_PIN_COLOR, false, null);

        final int color;
        final boolean isFind;
        final String imageUrl;
        final boolean photo;
        /** False for a marker created on the device that has not been synced to the backend yet. */
        final boolean isOnline;

        MarkerStyle(int color, boolean isFind, String imageUrl) {
            this(color, isFind, imageUrl, false);
        }

        MarkerStyle(int color, boolean isFind, String imageUrl, boolean photo) {
            this(color, isFind, imageUrl, photo, true);
        }

        MarkerStyle(int color, boolean isFind, String imageUrl, boolean photo, boolean isOnline) {
            this.color = color;
            this.isFind = isFind;
            this.imageUrl = imageUrl;
            this.photo = photo;
            this.isOnline = isOnline;
        }
    }

    private void removeMarker(JSONObject options, CallbackContext callback) {
        Object value = options.opt("id");
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            callback.error("markerId must be a non-empty string.");
            return;
        }
        String id = (String) value;
        runForSession(() -> {
            try {
                removeMarkerInternal(id);
                callback.success();
            } catch (Exception e) {
                callback.error("Failed to remove marker: " + e.getMessage());
            }
        });
    }

    private void removeMarkerInternal(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }

        PointAnnotation annotation = markerAnnotationsByRecordId.get(id);
        if (annotation != null) {
            if (pointAnnotationManager != null) {
                pointAnnotationManager.delete(annotation);
            }
            markerRecordIds.remove(annotation.getId());
        }
        markerAnnotationsByRecordId.remove(id);
        markerPointsByRecordId.remove(id);
        markerStylesByRecordId.remove(id);
        // Keep in-flight downloads for other markers, but forget this subscriber.
        for (List<String> waiting : pendingMarkerImages.values()) {
            waiting.removeIf(id::equals);
        }
    }

    private void clearMarkers(CallbackContext callback) {
        runForSession(() -> {
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
        markerStylesByRecordId.clear();
    }

    private void loadBoundaries(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized.");
                return;
            }

            JSONArray boundaries = options.optJSONArray("boundaries");
            int vertices = 0;
            if (boundaries != null) {
                if (boundaries.length() > MAX_BOUNDARIES) {
                    callback.error(
                        "Too many boundaries: maximum allowed is "
                            + MAX_BOUNDARIES + "."
                    );
                    return;
                }

                for (int i = 0; i < boundaries.length(); i++) {
                    JSONObject boundary = boundaries.optJSONObject(i);
                    JSONArray ring =
                        boundary == null
                            ? null
                            : boundary.optJSONArray("geometry");
                    vertices += ring == null ? 0 : ring.length();
                    if (vertices > MapboxSecurity.MAX_POINTS) {
                        callback.error("Too many boundary vertices.");
                        return;
                    }
                }
            }

            boundaryVisible = options.optBoolean("visible", true);
            boundaryAnnotationOptions.clear();
            clearBoundaryAnnotations();

            if (boundaries != null) {
                String fillColor = options.optString(
                    "fillColor",
                    "#2E7D32"
                );
                double fillOpacity = options.optDouble(
                    "fillOpacity",
                    0.18
                );
                String lineColor = options.optString(
                    "lineColor",
                    "#1B5E20"
                );

                for (int i = 0; i < boundaries.length(); i++) {
                    JSONObject boundary = boundaries.optJSONObject(i);
                    PolygonAnnotationOptions boundaryOptions =
                        boundaryOptionsFromJson(
                            boundary,
                            fillColor,
                            fillOpacity,
                            lineColor
                        );
                    if (boundaryOptions != null) {
                        boundaryAnnotationOptions.add(boundaryOptions);
                    }
                }
            }

            applyBoundaryVisibility();

            try {
                JSONObject result = new JSONObject();
                result.put(
                    "count",
                    boundaryAnnotationOptions.size()
                );
                callback.success(result);
            } catch (Exception e) {
                callback.error(sanitizeError("An internal error occurred.", e));
            }
        });
    }

    private void setBoundaryVisibility(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            boundaryVisible = options.optBoolean("visible", true);
            applyBoundaryVisibility();
            callback.success();
        });
    }

    private void setLayerVisibility(
        JSONObject options,
        CallbackContext callback
    ) {
        final String layerId = options.optString("layerId", "").trim();
        final boolean visible = options.optBoolean("visible", true);

        if (layerId.isEmpty()) {
            callback.error("layerId is required");
            return;
        }

        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized setLayerVisibility.");
                return;
            }

            Style style = mapView.getMapboxMap().getStyle();

            if (style == null) {
                callback.error("Style is not loaded yet.");
                return;
            }

            try {
                if (!style.styleLayerExists(layerId)) {
                    callback.error("Layer not found in current style: " + layerId);
                    return;
                }

                style.setStyleLayerProperty(
                    layerId,
                    "visibility",
                    Value.valueOf(visible ? "visible" : "none")
                );

                callback.success();
            } catch (Throwable e) {
                callback.error(
                    "Failed to change layer visibility: " +
                    (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                );
            }
        });
    }

    private void getLayerIds(CallbackContext callback) {
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized getLayerIds.");
                return;
            }

            Style style = mapView.getMapboxMap().getStyle();

            if (style == null) {
                callback.error("Style is not loaded yet.");
                return;
            }

            try {
                JSONArray ids = new JSONArray();
                for (StyleObjectInfo layer : style.getStyleLayers()) {
                    ids.put(layer.getId());
                }
                callback.success(ids);
            } catch (Throwable e) {
                callback.error(
                    "Failed to list layers: " +
                    (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                );
            }
        });
    }

    private void clearBoundaries(CallbackContext callback) {
        runForSession(() -> {
            clearBoundariesInternal();
            callback.success();
        });
    }

    private PolygonAnnotationOptions boundaryOptionsFromJson(
        JSONObject boundary,
        String fillColor,
        double fillOpacity,
        String lineColor
    ) {
        if (boundary == null) {
            return null;
        }

        JSONArray geometry = boundary.optJSONArray("geometry");
        if (geometry == null) {
            return null;
        }

        List<Point> ring = new ArrayList<>();
        for (int i = 0; i < geometry.length(); i++) {
            JSONObject coordinate = geometry.optJSONObject(i);
            if (coordinate == null) {
                continue;
            }

            double latitude = readFiniteDouble(
                coordinate,
                "lat",
                "latitude"
            );
            double longitude = readFiniteDouble(
                coordinate,
                "lon",
                "lng",
                "longitude"
            );

            if (!Double.isNaN(latitude)
                    && !Double.isNaN(longitude)
                    && isValidLatitude(latitude)
                    && isValidLongitude(longitude)) {
                ring.add(
                    Point.fromLngLat(longitude, latitude)
                );
            }
        }

        if (ring.size() < 3) {
            return null;
        }

        // Close polygon ring.
        Point firstPoint = ring.get(0);
        Point lastPoint = ring.get(ring.size() - 1);
        if (firstPoint.latitude() != lastPoint.latitude()
                || firstPoint.longitude() != lastPoint.longitude()) {
            ring.add(firstPoint);
        }

        return new PolygonAnnotationOptions()
            .withPoints(Collections.singletonList(ring))
            .withFillColor(fillColor)
            .withFillOpacity(fillOpacity)
            .withFillOutlineColor(lineColor);
    }

    private static boolean isValidLatitude(double lat) {
        return !Double.isNaN(lat) && !Double.isInfinite(lat) && lat >= -90.0 && lat <= 90.0;
    }

    private static boolean isValidLongitude(double lon) {
        return !Double.isNaN(lon) && !Double.isInfinite(lon) && lon >= -180.0 && lon <= 180.0;
    }

    private double readFiniteDouble(JSONObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key)) {
                continue;
            }

            double value = object.optDouble(key, Double.NaN);
            if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                return value;
            }
        }

        return Double.NaN;
    }

    /** Path line width in pixels: numeric or numeric string, > 0, capped; otherwise the default. */
    private double readLineWidth(JSONObject options) {
        double width = readFiniteDouble(options, "lineWidth");
        if (Double.isNaN(width) || width <= 0) {
            return DEFAULT_PATH_WIDTH;
        }
        return Math.min(width, MAX_PATH_WIDTH);
    }

    /** Path line opacity clamped to 0..1; missing or invalid falls back to fully opaque. */
    private double readLineOpacity(JSONObject options) {
        double opacity = readFiniteDouble(options, "lineOpacity");
        if (Double.isNaN(opacity)) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, opacity));
    }

    private void applyBoundaryVisibility() {

        if (!boundaryVisible) {
            clearBoundaryAnnotations();
            return;
        }

        if (!ensureBoundaryAnnotationManager()) {
            return;
        }

        clearBoundaryAnnotations();

        for (PolygonAnnotationOptions options : boundaryAnnotationOptions) {

            boundaryAnnotations.add(
                boundaryAnnotationManager.create(options)
            );
        }
    }

    private void clearBoundaryAnnotations() {

        if (boundaryAnnotationManager != null) {

            for (PolygonAnnotation annotation :
                    new ArrayList<>(boundaryAnnotations)) {

                boundaryAnnotationManager.delete(annotation);
            }
        }

        boundaryAnnotations.clear();
    }

    private void clearBoundariesInternal() {

        boundaryAnnotationOptions.clear();

        clearBoundaryAnnotations();

        boundaryVisible = true;
    }

    // --- Path Tracking Methods ---

    private boolean ensureLineAnnotationManager() {
        if (mapView == null) {
            return false;
        }

        if (lineAnnotationManager != null) {
            return true;
        }

        AnnotationPlugin annotationPlugin = mapView.getPlugin(Plugin.MAPBOX_ANNOTATION_PLUGIN_ID);
        if (annotationPlugin == null) {
            return false;
        }

        lineAnnotationManager = PolylineAnnotationManagerKt.createPolylineAnnotationManager(annotationPlugin, null);
        return true;
    }

    /** Length in metres of one segment; segments are never joined to each other. */
    private double segmentDistance(List<Point> segment) {
        double total = 0.0;
        for (int i = 1; i < segment.size(); i++) {
            float[] results = new float[1];
            Location.distanceBetween(
                segment.get(i - 1).latitude(),
                segment.get(i - 1).longitude(),
                segment.get(i).latitude(),
                segment.get(i).longitude(),
                results
            );
            total += results[0];
        }
        return total;
    }

    /** Draws one polyline per finished segment. A segment needs two points to be a line. */
    private void drawSegmentAnnotations() {
        if (!ensureLineAnnotationManager()) {
            return;
        }
        for (List<Point> segment : pathSegments) {
            if (segment.size() < 2) {
                continue;
            }
            pathAnnotations.add(lineAnnotationManager.create(new PolylineAnnotationOptions()
                .withGeometry(LineString.fromLngLats(segment))
                .withLineColor(pathLineColor)
                .withLineWidth(pathLineWidth)
                .withLineOpacity(pathLineOpacity)));
        }
    }

    private void updatePathAnnotation() {
        if (currentSegment == null || currentSegment.size() < 2) {
            return;
        }

        if (!ensureLineAnnotationManager()) {
            return;
        }

        // Delete the old annotation for the current segment
        if (currentSegmentAnnotation != null) {
            lineAnnotationManager.delete(currentSegmentAnnotation);
            currentSegmentAnnotation = null;
        }

        if (!isPathVisible) {
            return;
        }

        LineString lineString = LineString.fromLngLats(currentSegment);
        PolylineAnnotationOptions options = new PolylineAnnotationOptions()
            .withGeometry(lineString)
            .withLineColor(pathLineColor)
            .withLineWidth(pathLineWidth)
            .withLineOpacity(pathLineOpacity);

        currentSegmentAnnotation = lineAnnotationManager.create(options);
    }

    private void startPathTracking(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized startPathTracking.");
                return;
            }

            if (isPathTrackingActive) {
                callback.error("Path tracking is already active. Call stopPathTracking first.");
                return;
            }

            isPathTrackingActive = true;
            isPathTrackingPaused = false;
            isPathVisible = true;
            pathTrackingStartTimeMs = System.currentTimeMillis();
            pathPausedTotalMs = 0L;
            pathPauseStartMs = 0L;
            pathPoints.clear();
            pathAnnotations.clear();
            currentSegmentAnnotation = null;
            pathSegments.clear();
            currentSegment = new ArrayList<>();

            pathLineColor = MarkerIcons.parseColor(options.optString("lineColor", null), DEFAULT_PATH_COLOR);
            pathLineWidth = (float) readLineWidth(options);
            pathLineOpacity = (float) readLineOpacity(options);

            String trackCamera = options.optString("trackCamera", "true");
            if ("true".equals(trackCamera)) {
                startUserTracking(callback);
            } else {
                try {
                    JSONObject result = new JSONObject();
                    result.put("status", "started");
                    callback.success(result);
                } catch (Exception e) {
                    callback.error(sanitizeError("An internal error occurred.", e));
                }
            }
        });
    }

    private void stopPathTracking(CallbackContext callback) {
        runForSession(() -> {
            if (!isPathTrackingActive) {
                callback.error("Path tracking is not active.");
                return;
            }

            boolean wasPaused = isPathTrackingPaused;
            isPathTrackingActive = false;
            isPathTrackingPaused = false;
            long nowMs = System.currentTimeMillis();
            if (wasPaused) {
                pathPausedTotalMs += nowMs - pathPauseStartMs;
            }
            // Moving time: wall-clock time minus every pause.
            long durationMs = Math.max(0L, nowMs - pathTrackingStartTimeMs - pathPausedTotalMs);

            // Freeze current segment annotation into the permanent list
            if (currentSegmentAnnotation != null) {
                pathAnnotations.add(currentSegmentAnnotation);
                currentSegmentAnnotation = null;
            }

            // Finish current segment if it has points
            if (currentSegment != null && !currentSegment.isEmpty()) {
                pathSegments.add(currentSegment);
                currentSegment = null;
            }

            // Measured inside each segment so the jump across a pause adds nothing.
            double totalDistance = 0.0;
            for (List<Point> segment : pathSegments) {
                totalDistance += segmentDistance(segment);
            }

            try {
                JSONObject result = new JSONObject();
                JSONArray pointsArray = new JSONArray();
                for (Point point : pathPoints) {
                    JSONObject pointObj = new JSONObject();
                    pointObj.put("lat", point.latitude());
                    pointObj.put("lon", point.longitude());
                    pointsArray.put(pointObj);
                }
                result.put("points", pointsArray);
                result.put("distance", Math.round(totalDistance * 100.0) / 100.0);
                result.put("duration", durationMs);

                // Return segment boundaries
                JSONArray segmentsArray = new JSONArray();
                int startIndex = 0;
                for (List<Point> segment : pathSegments) {
                    JSONObject segmentObj = new JSONObject();
                    segmentObj.put("startIndex", startIndex);
                    segmentObj.put("endIndex", startIndex + segment.size() - 1);
                    segmentsArray.put(segmentObj);
                    startIndex += segment.size();
                }
                result.put("segments", segmentsArray);
                result.put("segmentCount", pathSegments.size());

                callback.success(result);
            } catch (Exception e) {
                callback.error(sanitizeError("An internal error occurred.", e));
            }
        });
    }

    private void pausePathTracking(CallbackContext callback) {
        runForSession(() -> {
            if (!isPathTrackingActive) {
                callback.error("Path tracking is not active.");
                return;
            }

            if (isPathTrackingPaused) {
                callback.error("Path tracking is already paused.");
                return;
            }

            isPathTrackingPaused = true;
            pathPauseStartMs = System.currentTimeMillis();

            // Freeze current segment annotation into the permanent list
            if (currentSegmentAnnotation != null) {
                pathAnnotations.add(currentSegmentAnnotation);
                currentSegmentAnnotation = null;
            }

            // Finish current segment
            if (currentSegment != null && !currentSegment.isEmpty()) {
                pathSegments.add(currentSegment);
                currentSegment = null;
            }

            try {
                JSONObject result = new JSONObject();
                result.put("status", "paused");
                result.put("segmentCount", pathSegments.size());
                callback.success(result);
            } catch (Exception e) {
                callback.error(sanitizeError("An internal error occurred.", e));
            }
        });
    }

    private void continuePathTracking(CallbackContext callback) {
        runForSession(() -> {
            if (!isPathTrackingActive) {
                callback.error("Path tracking is not active.");
                return;
            }

            if (!isPathTrackingPaused) {
                callback.error("Path tracking is not paused. Call pausePathTracking first.");
                return;
            }

            isPathTrackingPaused = false;
            pathPausedTotalMs += System.currentTimeMillis() - pathPauseStartMs;
            pathPauseStartMs = 0L;
            currentSegment = new ArrayList<>();

            try {
                JSONObject result = new JSONObject();
                result.put("status", "continued");
                result.put("segmentCount", pathSegments.size() + 1);
                callback.success(result);
            } catch (Exception e) {
                callback.error(sanitizeError("An internal error occurred.", e));
            }
        });
    }

    private void loadPath(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized loadPath.");
                return;
            }

            if (isPathTrackingActive) {
                callback.error("Cannot load a path while path tracking is active. Stop tracking first.");
                return;
            }

            JSONArray pointsArray = options.optJSONArray("points");
            if (pointsArray == null || pointsArray.length() < 2 || pointsArray.length() > MapboxSecurity.MAX_POINTS) {
                callback.error("A path requires between 2 and 20000 points.");
                return;
            }

            int lineColor = MarkerIcons.parseColor(options.optString("lineColor", null), DEFAULT_PATH_COLOR);
            double lineWidth = readLineWidth(options);
            double lineOpacity = readLineOpacity(options);

            pathLineColor = lineColor;
            pathLineWidth = (float) lineWidth;
            pathLineOpacity = (float) lineOpacity;
            isPathVisible = true;

            // Keep each valid point's original index so segment ranges still line up
            // when invalid points are skipped.
            List<Point> validPoints = new ArrayList<>();
            List<Integer> validIndexes = new ArrayList<>();
            for (int i = 0; i < pointsArray.length(); i++) {
                JSONObject pointObj = pointsArray.optJSONObject(i);
                if (pointObj == null) continue;
                double lat = readFiniteDouble(pointObj, "lat", "latitude");
                double lon = readFiniteDouble(pointObj, "lon", "lng", "longitude");
                if (!Double.isNaN(lat) && !Double.isNaN(lon)
                        && isValidLatitude(lat) && isValidLongitude(lon)) {
                    validPoints.add(Point.fromLngLat(lon, lat));
                    validIndexes.add(i);
                }
            }

            if (validPoints.size() < 2) {
                callback.error("A path requires at least 2 valid points.");
                return;
            }

            pathPoints.clear();
            pathPoints.addAll(validPoints);

            // Optional segments keep the pause gaps; without them the path is one line.
            pathSegments.clear();
            JSONArray segmentsArray = options.optJSONArray("segments");
            if (segmentsArray != null) {
                int segmentLimit = Math.min(segmentsArray.length(), MAX_PATH_SEGMENTS);
                for (int s = 0; s < segmentLimit; s++) {
                    JSONObject segmentObj = segmentsArray.optJSONObject(s);
                    if (segmentObj == null) continue;
                    int startIndex = segmentObj.optInt("startIndex", -1);
                    int endIndex = segmentObj.optInt("endIndex", -1);
                    if (startIndex < 0 || endIndex < startIndex) continue;
                    List<Point> part = new ArrayList<>();
                    for (int k = 0; k < validPoints.size(); k++) {
                        int original = validIndexes.get(k);
                        if (original >= startIndex && original <= endIndex) {
                            part.add(validPoints.get(k));
                        }
                    }
                    if (!part.isEmpty()) {
                        pathSegments.add(part);
                    }
                }
            }
            if (pathSegments.isEmpty()) {
                pathSegments.add(new ArrayList<>(validPoints));
            }

            if (!ensureLineAnnotationManager()) {
                callback.error("Polyline annotation manager is not available.");
                return;
            }

            // Clear any existing path annotations
            for (PolylineAnnotation ann : pathAnnotations) {
                lineAnnotationManager.delete(ann);
            }
            pathAnnotations.clear();

            drawSegmentAnnotations();

            try {
                JSONObject result = new JSONObject();
                result.put("status", "loaded");
                result.put("pointCount", pathPoints.size());
                result.put("segmentCount", pathSegments.size());
                callback.success(result);
            } catch (Exception e) {
                callback.error(sanitizeError("An internal error occurred.", e));
            }
        });
    }

    private void clearPaths(CallbackContext callback) {
        runForSession(() -> {
            if (lineAnnotationManager != null) {
                for (PolylineAnnotation ann : pathAnnotations) {
                    lineAnnotationManager.delete(ann);
                }
                if (currentSegmentAnnotation != null) {
                    lineAnnotationManager.delete(currentSegmentAnnotation);
                    currentSegmentAnnotation = null;
                }
            }
            pathAnnotations.clear();
            pathPoints.clear();
            pathSegments.clear();
            callback.success();
        });
    }

    private void setPathVisibility(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized setPathVisibility.");
                return;
            }

            boolean visible = options.optBoolean("visible", true);
            boolean wasVisible = isPathVisible;

            isPathVisible = visible;

            if (!visible) {
                // Hide: delete all annotations from map
                if (lineAnnotationManager != null) {
                    for (PolylineAnnotation ann : pathAnnotations) {
                        lineAnnotationManager.delete(ann);
                    }
                    if (currentSegmentAnnotation != null) {
                        lineAnnotationManager.delete(currentSegmentAnnotation);
                        currentSegmentAnnotation = null;
                    }
                }
                pathAnnotations.clear();
            } else {
                // Show: bring back the finished segments (only if they were hidden, so a
                // repeated call does not stack duplicates), then the live segment.
                if (!wasVisible) {
                    drawSegmentAnnotations();
                }
                updatePathAnnotation();
            }

            callback.success();
        });
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
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized getCamera.");
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
                callback.error(sanitizeError("An internal error occurred.", e));
            }
        });
    }

    private void getCurrentLocationAccuracy(final CallbackContext callbackContext) {
        runForSession(() -> {
            if (!hasLocationPermission()) {
                Log.d("MapboxPlugin", "getCurrentLocationAccuracy: location permission not granted");
                deliverAccuracy(callbackContext, -1f, "Unknown");
                return;
            }

            if (fusedLocationClient == null) {
                fusedLocationClient = LocationServices
                    .getFusedLocationProviderClient(cordova.getActivity());
            }

            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(last -> {
                    if (last != null
                            && last.hasAccuracy()
                            && System.currentTimeMillis() - last.getTime()
                            <= MAX_LOCATION_AGE_MS) {
                        deliverAccuracy(
                            callbackContext,
                            last.getAccuracy(),
                            getAccuracyLabel(last.getAccuracy())
                        );
                        return;
                    }
                    useLocationManagerCache(callbackContext);
                })
                .addOnFailureListener(e -> useLocationManagerCache(callbackContext));
        });
    }

    private void useLocationManagerCache(final CallbackContext callbackContext) {
        runForSession(() -> {
            if (callbackContext == null) {
                return;
            }

            LocationManager locationManager =
                (LocationManager) cordova.getActivity()
                    .getSystemService(Context.LOCATION_SERVICE);

            if (locationManager == null) {
                Log.d("MapboxPlugin", "getCurrentLocationAccuracy: LocationManager unavailable");
                deliverAccuracy(callbackContext, -1f, "Unknown");
                return;
            }

            Location bestLocation = getBestAvailableLocation(locationManager);

            if (bestLocation == null || !bestLocation.hasAccuracy()) {
                Log.d("MapboxPlugin", "getCurrentLocationAccuracy: no recent location with accuracy available");
                deliverAccuracy(callbackContext, -1f, "Unknown");
                return;
            }

            float accuracy = bestLocation.getAccuracy();
            deliverAccuracy(callbackContext, accuracy, getAccuracyLabel(accuracy));
        });
    }

    private void deliverAccuracy(
        final CallbackContext callbackContext,
        final float accuracy,
        final String label
    ) {
        runForSession(() -> {
            if (callbackContext != null) {
                callbackContext.success(buildAccuracyResult(accuracy, label));
            }
        });
    }

    /**
     * Builds the result payload for getCurrentLocationAccuracy, mirroring the
     * {accuracy, accuracyLabel} shape already returned by moveToCurrentLocation
     * so callers get the raw value in addition to the bucketed label.
     */
    private JSONObject buildAccuracyResult(float accuracy, String label) {
        JSONObject result = new JSONObject();
        try {
            result.put("accuracy", accuracy);
            result.put("accuracyLabel", label);
        } catch (JSONException e) {
            // Unreachable: keys/values above are simple literals that cannot fail to serialize.
        }
        return result;
    }

    private Location getBestAvailableLocation(LocationManager locationManager) {
        Location gpsLocation = null;
        Location networkLocation = null;

        try {
            gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } catch (SecurityException e) {
            // Permission checked by the caller, but it can be revoked between the check
            // and this call. Log it rather than swallowing silently, to aid diagnosis.
            Log.w("MapboxPlugin", "getBestAvailableLocation: lost permission reading GPS provider", e);
        }

        try {
            networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (SecurityException e) {
            Log.w("MapboxPlugin", "getBestAvailableLocation: lost permission reading network provider", e);
        }

        long now = System.currentTimeMillis();

        boolean gpsValid =
            gpsLocation != null &&
            gpsLocation.hasAccuracy() &&
            now - gpsLocation.getTime() <= MAX_LOCATION_AGE_MS;

        boolean networkValid =
            networkLocation != null &&
            networkLocation.hasAccuracy() &&
            now - networkLocation.getTime() <= MAX_LOCATION_AGE_MS;

        if (gpsValid && networkValid) {
            return gpsLocation.getAccuracy() <= networkLocation.getAccuracy()
                ? gpsLocation
                : networkLocation;
        }

        if (gpsValid) {
            return gpsLocation;
        }

        if (networkValid) {
            return networkLocation;
        }

        return null;
    }

    private void close(CallbackContext callback) {
        cordova.getActivity().runOnUiThread(() -> {
            closeInternal();
            callback.success();
        });
    }

    private void setMapStyle(JSONObject options, CallbackContext callback) {
        runForSession(() -> {
            if (mapView == null) {
                callback.error("Map is not initialized setMapStyle.");
                return;
            }

            String styleUrl = options.optString("styleUrl", "");
            if (!styleAllowed(styleUrl)) {
                callback.error("Style URL is not allowed. Use a Mapbox style or an approved HTTPS host.");
                return;
            }
            if (styleUrl.isEmpty()) {
                callback.error("styleUrl is required");
                return;
            }

            loadReadyStyle(styleUrl, "styleChanged", callback);
        });
    }

    // All style replacements use this gate. Never resolve readiness on a timer.
    private final Handler styleHandler = new Handler(Looper.getMainLooper());
    private StyleRequest pendingStyle;

    private static final class StyleRequest {
        final MapView view;
        final CallbackContext callback;
        final String status;
        Cancelable errors;
        Runnable timeout;
        StyleRequest(MapView view, CallbackContext callback, String status) {
            this.view = view; this.callback = callback; this.status = status;
        }
    }

    private void cancelPendingStyle() {
        StyleRequest request = pendingStyle;
        pendingStyle = null;
        if (request == null) return;
        if (request.timeout != null) styleHandler.removeCallbacks(request.timeout);
        if (request.errors != null) cleanupStep(() -> request.errors.cancel());
        // closeInternal settles the registered Cordova callback.
    }

    private void finishStyle(StyleRequest request, String error) {
        if (pendingStyle != request || mapView != request.view) return;
        cancelPendingStyle();
        if (request.callback.isFinished()) return;
        if (error != null) { request.callback.error(error); return; }
        if (request.status == null) { request.callback.success(); return; }
        try {
            JSONObject result = new JSONObject();
            result.put("status", request.status);
            request.callback.success(result);
        } catch (JSONException e) {
            request.callback.error("Failed to create style completion result.");
        }
    }

    private void loadReadyStyle(String uri, String status, CallbackContext callback) {
        if (pendingStyle != null) {
            callback.error("A style load is already in progress. Await its completion first.");
            return;
        }
        final StyleRequest request = new StyleRequest(mapView, callback, status);
        pendingStyle = request;
        request.timeout = () -> finishStyle(request, "Style loading timed out after 30 seconds.");
        styleHandler.postDelayed(request.timeout, 30000L);
        try {
            request.errors = request.view.getMapboxMap().subscribeMapLoadingError(event -> {
                if (event.getType() == com.mapbox.maps.MapLoadingErrorType.STYLE) {
                    styleHandler.post(() -> finishStyle(request, "Map style failed to load."));
                }
            });
            request.view.getMapboxMap().loadStyle(uri,
                style -> finishStyle(request, null));
        } catch (RuntimeException error) {
            finishStyle(request, "Map style failed to load.");
        }
    }

    private void cancelCurrentDownload() {
        if (activeStylePackDownload != null) {
            activeStylePackDownload.cancel();
            activeStylePackDownload = null;
        }

        if (activeTileRegionDownload != null) {
            activeTileRegionDownload.cancel();
            activeTileRegionDownload = null;
        }
        activeOfflineManager = null;
        activeOfflineTileStore = null;
    }

    private void cleanupStep(Runnable work) {
        try { work.run(); }
        catch (RuntimeException error) {
            Log.w("MapboxPlugin", "Map cleanup step failed; continuing teardown.", error);
        }
    }

    protected void closeInternal() {
        sessionGeneration++;
        cancelPendingStyle();
        if (closeCallback != null) { closeCallback.success(); closeCallback = null; }
        for (CallbackContext pending : sessionCallbacks) {
            if (!pending.isFinished()) {
                boolean stream = pending == waypointSelectedCallback || pending == markerClickCallback
                    || pending == offlineDownloadProgressCallback || pending == trackingStatusCallback
                    || pending == boundaryAreaCallback
                    || pending == locationAccuracyCallback || pending == compassCallback;
                if (stream) releaseCallback(pending);
                else pending.error("Map closed; operation cancelled.");
            }
        }
        sessionCallbacks.clear();
        cleanupStep(this::cancelPendingLocationActions);
        compassCallback = null;
        if (compassCameraSubscription != null) {
            cleanupStep(() -> compassCameraSubscription.cancel());
            compassCameraSubscription = null;
        }
        cleanupStep(this::cancelCurrentDownload);
        cleanupStep(markerIcons::cancelAll);
        cleanupStep(() -> stopSharedLocationUpdates("Map closed; operation cancelled."));
        recenterAnimationGeneration++;
        isCameraFollowingUser = true;
        if (mapView != null) {
            MotionEvent cancel = MotionEvent.obtain(android.os.SystemClock.uptimeMillis(),
                android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0, 0, 0);
            try { cleanupStep(() -> mapView.dispatchTouchEvent(cancel)); } finally { cancel.recycle(); }
            mapView.setEnabled(false);
        }
        cleanupStep(this::removeTouchRouter);
        // Remove the visible native map before SDK/service cleanup can fail.
        if (rootView != null && rootView.getParent() instanceof ViewGroup) {
            ((ViewGroup) rootView.getParent()).removeView(rootView);
        }
        // Clear callbacks before stopping services so cleanup emits no stale events.
        trackingStatusCallback = null;
        cleanupStep(this::stopLocationAccuracyMonitoring);
        locationAccuracyCallback = null;
        cleanupStep(this::stopHeadingFollowMode);
        cleanupStep(this::stopUserTracking);
        cleanupStep(this::cancelMoveToCurrentLocation);

        if (pointAnnotationManager != null && markerTapListener != null) {
            cleanupStep(() -> pointAnnotationManager.removeClickListener(markerTapListener));
        }
        markerTapListener = null;

        if (mapView != null) {
            cleanupStep(() -> {
                GesturesPlugin gestures = mapView.getPlugin(Plugin.MAPBOX_GESTURES_PLUGIN_ID);
                if (gestures != null) {
                    if (mapClickListener != null) cleanupStep(() -> gestures.removeOnMapClickListener(mapClickListener));
                    if (moveGestureListener != null) cleanupStep(() -> gestures.removeOnMoveListener(moveGestureListener));
                    if (scaleGestureListener != null) cleanupStep(() -> gestures.removeOnScaleListener(scaleGestureListener));
                }
            });
            cleanupStep(() -> {
                CameraAnimationsPlugin camera = mapView.getPlugin(Plugin.MAPBOX_CAMERA_PLUGIN_ID);
                if (camera != null) camera.cancelAllAnimators(Collections.emptyList());
            });
            cleanupStep(() -> mapView.onStop());
            cleanupStep(() -> mapView.onDestroy());
        }

        mapView = null;
        rootView = null;
        pointAnnotationManager = null;
        boundaryAnnotationManager = null;
        boundaryAnnotationOptions.clear();
        boundaryAnnotations.clear();
        boundaryVisible = true;
        lineAnnotationManager = null;
        pathAnnotations.clear();
        currentSegmentAnnotation = null;
        pathPoints.clear();
        isPathTrackingActive = false;
        isPathTrackingPaused = false;
        pathTrackingStartTimeMs = 0L;
        pathPausedTotalMs = 0L;
        pathPauseStartMs = 0L;
        isPathVisible = true;
        pathLineColor = DEFAULT_PATH_COLOR;
        pathLineWidth = 3.0f;
        pathLineOpacity = 1.0f;
        pathSegments.clear();
        currentSegment = null;
        markerRecordIds.clear();
        markerAnnotationsByRecordId.clear();
        markerPointsByRecordId.clear();
        markerStylesByRecordId.clear();
        pendingMarkerImages.clear();
        waypointSelectedCallback = null;
        markerClickCallback = null;
        mapTapCallback = null;
        offlineDownloadProgressCallback = null;
        trackingStatusCallback = null;
        locationAccuracyCallback = null;
        lastLocationAccuracyUpdateMs = 0L;
        cleanupStep(this::cancelCurrentDownload);
        isOfflineDownloading = false;
        waypointSelectionEnabled = false;
        autoAddWaypointMarker = false;
        isUserLocationEnabled = false;
        isUserTrackingEnabled = false;
        stopCameraFollow();
        isDeviceHeadingEnabled = false;
        isHeadingFollowModeEnabled = false;
        currentGestureTargetsWebView = false;
        mapClickListener = null;
        moveGestureListener = null;
        scaleGestureListener = null;
        rawTapDownMs = 0L;
        lastMapSelectionCallbackMs = lastKeepCallbackOfflineMs = lastKeepCallbackWaypointMs = lastKeepCallbackMarkerMs = 0L;
        lastCompassBearing = Double.NaN;
        lastCompassVisible = false;
        fusedLocationClient = null;
        smoothedLocationProvider = null;
        touchableRects.clear();
    }

    private void addBehindWebView(View nativeView) {
        View webViewView = webView.getView();
        installTouchRouter(webViewView);
        originalWebViewBackground = webViewView.getBackground();
        originalWebViewBackgroundColor = originalWebViewBackground instanceof android.graphics.drawable.ColorDrawable
            ? ((android.graphics.drawable.ColorDrawable) originalWebViewBackground).getColor()
            : preferences.getInteger("BackgroundColor", Color.WHITE);
        // Use a new drawable so View.setBackgroundColor cannot mutate the saved one.
        webViewView.setBackground(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        // WebView overrides this API to update Chromium's page background. A
        // transparent View drawable alone still leaves an opaque WebView surface.
        webViewView.setBackgroundColor(Color.TRANSPARENT);
        mapWebViewBackground = webViewView.getBackground();

        View anchor = touchRouter != null ? touchRouter : webViewView;
        if (anchor.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) anchor.getParent();
            int webViewIndex = parent.indexOfChild(anchor);
            parent.addView(nativeView, Math.max(0, webViewIndex));
            return;
        }

        ViewGroup decor = (ViewGroup) cordova.getActivity().getWindow().getDecorView();
        decor.addView(nativeView, 0);
    }

    private void installTouchRouter(View webViewView) {
        if (!(webViewView.getParent() instanceof ViewGroup)) {
            throw new IllegalStateException("WebView must have a parent for Mapbox touch routing.");
        }
        ViewGroup parent = (ViewGroup) webViewView.getParent();
        int index = parent.indexOfChild(webViewView);
        originalWebViewLayout = webViewView.getLayoutParams();
        routedWebView = webViewView;
        touchRouter = new FrameLayout(webViewView.getContext()) {
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                return routeMapTouch(routedWebView, event) || super.dispatchTouchEvent(event);
            }
        };
        parent.removeView(webViewView);
        parent.addView(touchRouter, index, originalWebViewLayout);
        touchRouter.addView(webViewView, new FrameLayout.LayoutParams(-1, -1));
    }

    private void removeTouchRouter() {
        View view = routedWebView;
        FrameLayout router = touchRouter;
        if (view != null && view.getBackground() == mapWebViewBackground) {
            view.setBackgroundColor(originalWebViewBackgroundColor);
            view.setBackground(originalWebViewBackground);
        }
        if (router != null && view != null && view.getParent() == router
                && router.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) router.getParent();
            int index = parent.indexOfChild(router);
            router.removeView(view);
            parent.removeView(router);
            parent.addView(view, index, originalWebViewLayout);
        } else if (router != null && view != null && view.getParent() == router) {
            router.removeView(view);
            view.setLayoutParams(originalWebViewLayout);
        } else if (router != null && router.getParent() instanceof ViewGroup) {
            ((ViewGroup) router.getParent()).removeView(router);
        }
        touchRouter = null;
        routedWebView = null;
        originalWebViewLayout = null;
        originalWebViewBackground = mapWebViewBackground = null;
    }

    private boolean routeMapTouch(View view, MotionEvent event) {
        if (view == null || mapView == null) return false;
        final int[] webViewLocation = new int[2];
        final int[] mapViewLocation = new int[2];
            int action = event.getActionMasked();

            // Choose one target on ACTION_DOWN and keep the entire gesture with it.
            // Re-testing every MOVE can steal a bottom-sheet drag when the pointer
            // leaves its original rectangle or the sheet moves under the pointer.
            if (action == MotionEvent.ACTION_DOWN) {
                currentGestureTargetsWebView =
                    mapView == null || isInsideTouchableRect(event.getX(), event.getY());
                if (!currentGestureTargetsWebView) {
                    view.getLocationInWindow(webViewLocation);
                    mapView.getLocationInWindow(mapViewLocation);
                    float mapX = event.getX() + webViewLocation[0] - mapViewLocation[0];
                    float mapY = event.getY() + webViewLocation[1] - mapViewLocation[1];
                    // The laid-out MapView reflects initialize/setViewport/resizeMap.
                    // Only gestures starting inside it belong to native Mapbox;
                    // OutSystems overlay exclusions above always retain priority.
                    currentGestureTargetsWebView =
                        mapX < 0 || mapY < 0
                        || mapX >= mapView.getWidth() || mapY >= mapView.getHeight();
                }
            }

            if (currentGestureTargetsWebView || mapView == null) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    currentGestureTargetsWebView = false;
                }
                return false;
            }

            view.getLocationInWindow(webViewLocation);
            mapView.getLocationInWindow(mapViewLocation);
            MotionEvent mapEvent = MotionEvent.obtain(event);
            try {
                // Convert only the copy to MapView-local pixels, including current view offsets.
                mapEvent.offsetLocation(
                    webViewLocation[0] - mapViewLocation[0],
                    webViewLocation[1] - mapViewLocation[1]
                );
                handleRawTouchForTap(mapEvent);
                mapView.dispatchTouchEvent(mapEvent);
            } finally {
                mapEvent.recycle();
            }

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                currentGestureTargetsWebView = false;
            }
            return true;
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

    private String sanitizeError(String contextMessage, Throwable t) {
        Log.e("MapboxPlugin", contextMessage);
        return contextMessage;
    }

    private String sanitizeError(String contextMessage, Object t) {
        Log.e("MapboxPlugin", contextMessage);
        return contextMessage;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static byte toZoomByte(double value) {
        return (byte) Math.max(0, Math.min(Math.round(value), 127));
    }

    private static class SmoothedLocationProvider
        implements com.mapbox.maps.plugin.locationcomponent.LocationProvider {

        private com.mapbox.maps.plugin.locationcomponent.LocationConsumer locationConsumer;

        @Override
        public void registerLocationConsumer(
            com.mapbox.maps.plugin.locationcomponent.LocationConsumer consumer
        ) {
            this.locationConsumer = consumer;
        }

        @Override
        public void unRegisterLocationConsumer(
            com.mapbox.maps.plugin.locationcomponent.LocationConsumer consumer
        ) {
            this.locationConsumer = null;
        }

        public void updateLocation(Location androidLocation) {
            if (locationConsumer != null) {
                locationConsumer.onLocationUpdated(
                    new Point[]{
                        Point.fromLngLat(
                            androidLocation.getLongitude(),
                            androidLocation.getLatitude()
                        )
                    },
                    null
                );
            }
        }
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
        if (isUserTrackingEnabled) {
            startCameraFollow();
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        if (mapView != null) {
            mapView.onStop();
        }
        stopCameraFollow();
        super.onPause(multitasking);
    }

    @Override
    public void onDestroy() {
        cordova.getActivity().runOnUiThread(() -> {
            closeInternal();
            markerIcons.shutdown();
        });
        super.onDestroy();
    }

    @Override
    public void onReset() {
        // Invalidate queued work immediately, then release UI-owned resources.
        sessionGeneration++;
        cordova.getActivity().runOnUiThread(this::closeInternal);
        super.onReset();
    }
}
