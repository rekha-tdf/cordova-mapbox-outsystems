package com.outsystems.mapbox

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import com.mapbox.geojson.Point
import com.mapbox.common.MapboxOptions
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.json.JSONArray
import org.json.JSONObject

open class MapboxPlugin : CordovaPlugin() {
    private var mapView: MapView? = null
    private var rootView: FrameLayout? = null
    private var annotationManager: PointAnnotationManager? = null
    private val markers = mutableMapOf<String, PointAnnotation>()
    private val defaultMarkerBitmap: Bitmap by lazy { createDefaultMarkerBitmap() }

    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        when (action) {
            "initialize" -> initialize(args.optJSONObject(0) ?: JSONObject(), callbackContext)
            "setViewport" -> setViewport(args.optJSONObject(0) ?: JSONObject(), callbackContext)
            "setCamera" -> setCamera(args.optJSONObject(0) ?: JSONObject(), callbackContext)
            "addMarker" -> addMarker(args.optJSONObject(0) ?: JSONObject(), callbackContext)
            "removeMarker" -> removeMarker(args.optJSONObject(0) ?: JSONObject(), callbackContext)
            "clearMarkers" -> clearMarkers(callbackContext)
            "getCamera" -> getCamera(callbackContext)
            "close" -> close(callbackContext)
            else -> return false
        }
        return true
    }

    private fun initialize(options: JSONObject, callback: CallbackContext) {
        cordova.activity.runOnUiThread {
            try {
                val token = options.optString("token")
                    .ifBlank { preferences.getString("MAPBOX_ACCESS_TOKEN", "") }
                    .ifBlank { androidString("mapbox_access_token") }
                    .takeUnless { it == "__MAPBOX_ACCESS_TOKEN_NOT_SET__" }
                    ?: ""

                if (token.isBlank()) {
                    callback.error("Mapbox access token is required.")
                    return@runOnUiThread
                }

                MapboxOptions.accessToken = token

                val styleUrl = options.optString("styleUrl", Style.MAPBOX_STREETS)
                val latitude = options.optDouble("latitude", 0.0)
                val longitude = options.optDouble("longitude", 0.0)

                if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) {
                    callback.error("Invalid coordinates: latitude must be in [-90, 90], longitude in [-180, 180].")
                    return@runOnUiThread
                }

                val zoom = options.optDouble("zoom", 12.0)

                closeInternal()

                val activity = cordova.activity
                val decor = activity.window.decorView as ViewGroup

                rootView = FrameLayout(activity).apply {
                    setBackgroundColor(Color.WHITE)
                    layoutParams = layoutParamsFromOptions(options)
                }

                mapView = MapView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                    )
                }

                rootView!!.addView(mapView)
                if (!options.optBoolean("inline", false)) {
                    rootView!!.addView(Button(activity).apply {
                        text = "Close"
                        setOnClickListener { closeInternal() }
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP or Gravity.END
                        ).apply {
                            topMargin = 48
                            rightMargin = 24
                        }
                    })
                }
                decor.addView(rootView)

                mapView!!.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(longitude, latitude))
                        .zoom(zoom)
                        .build()
                )

                mapView!!.mapboxMap.loadStyle(styleUrl) {
                    annotationManager = mapView!!.annotations.createPointAnnotationManager()
                    callback.success(JSONObject().put("status", "initialized"))
                }
            } catch (e: Exception) {
                callback.error(e.message ?: "Failed to initialize Mapbox map.")
            }
        }
    }

    private fun setViewport(options: JSONObject, callback: CallbackContext) {
        cordova.activity.runOnUiThread {
            val view = rootView
            if (view == null) {
                callback.error("Map is not initialized.")
                return@runOnUiThread
            }

            view.layoutParams = layoutParamsFromOptions(options)
            view.requestLayout()
            callback.success()
        }
    }

    private fun isValidLatitude(lat: Double) = lat.isFinite() && lat in -90.0..90.0

    private fun isValidLongitude(lon: Double) = lon.isFinite() && lon in -180.0..180.0

    private fun setCamera(options: JSONObject, callback: CallbackContext) {
        cordova.activity.runOnUiThread {
            val map = mapView?.mapboxMap
            if (map == null) {
                callback.error("Map is not initialized.")
                return@runOnUiThread
            }

            val camLat = options.optDouble("latitude")
            val camLng = options.optDouble("longitude")
            if (!isValidLatitude(camLat) || !isValidLongitude(camLng)) {
                callback.error("Invalid coordinates: latitude must be in [-90, 90], longitude in [-180, 180].")
                return@runOnUiThread
            }

            val camera = CameraOptions.Builder()
                .center(Point.fromLngLat(camLng, camLat))
                .zoom(options.optDouble("zoom", map.cameraState.zoom))
                .bearing(options.optDouble("bearing", map.cameraState.bearing))
                .pitch(options.optDouble("pitch", map.cameraState.pitch))
                .build()

            map.setCamera(camera)
            callback.success()
        }
    }

    private fun addMarker(options: JSONObject, callback: CallbackContext) {
        cordova.activity.runOnUiThread {
            val manager = annotationManager
            if (manager == null) {
                callback.error("Map is not initialized.")
                return@runOnUiThread
            }

            val id = options.optString("id").ifBlank { System.currentTimeMillis().toString() }
            markers[id]?.let { manager.delete(it) }

            val markerLat = options.optDouble("latitude")
            val markerLng = options.optDouble("longitude")
            if (!isValidLatitude(markerLat) || !isValidLongitude(markerLng)) {
                callback.error("Invalid coordinates: latitude must be in [-90, 90], longitude in [-180, 180].")
                return@runOnUiThread
            }

            val markerOptions = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(markerLng, markerLat))
                .withIconImage(defaultMarkerBitmap)

            val marker = manager.create(markerOptions)
            markers[id] = marker
            callback.success(JSONObject().put("id", id))
        }
    }

    private fun removeMarker(options: JSONObject, callback: CallbackContext) {
        cordova.activity.runOnUiThread {
            val id = options.optString("id")
            val marker = markers.remove(id)
            val manager = annotationManager

            if (marker != null && manager != null) {
                manager.delete(marker)
            }

            callback.success()
        }
    }

    private fun clearMarkers(callback: CallbackContext) {
        cordova.activity.runOnUiThread {
            annotationManager?.deleteAll()
            markers.clear()
            callback.success()
        }
    }

    private fun getCamera(callback: CallbackContext) {
        cordova.activity.runOnUiThread {
            val map = mapView?.mapboxMap
            if (map == null) {
                callback.error("Map is not initialized.")
                return@runOnUiThread
            }

            val state = map.cameraState
            callback.success(
                JSONObject()
                    .put("latitude", state.center.latitude())
                    .put("longitude", state.center.longitude())
                    .put("zoom", state.zoom)
                    .put("bearing", state.bearing)
                    .put("pitch", state.pitch)
            )
        }
    }

    private fun close(callback: CallbackContext) {
        cordova.activity.runOnUiThread {
            closeInternal()
            callback.success()
        }
    }

    private fun closeInternal() {
        annotationManager = null
        markers.clear()
        mapView?.onStop()
        mapView?.onDestroy()
        rootView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        mapView = null
        rootView = null
    }

    override fun onResume(multitasking: Boolean) {
        super.onResume(multitasking)
        mapView?.onStart()
    }

    override fun onPause(multitasking: Boolean) {
        mapView?.onStop()
        super.onPause(multitasking)
    }

    override fun onDestroy() {
        closeInternal()
        super.onDestroy()
    }

    private fun createDefaultMarkerBitmap(): Bitmap {
        val width = 72
        val height = 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val pin = Path().apply {
            moveTo(width / 2f, height - 6f)
            cubicTo(10f, 56f, 8f, 16f, width / 2f, 16f)
            cubicTo(width - 8f, 16f, width - 10f, 56f, width / 2f, height - 6f)
            close()
        }

        paint.color = Color.rgb(220, 38, 38)
        canvas.drawPath(pin, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(width / 2f, 40f, 13f, paint)
        return bitmap
    }

    private fun androidString(name: String): String {
        val activity = cordova.activity
        val resourceId = activity.resources.getIdentifier(name, "string", activity.packageName)
        return if (resourceId == 0) "" else activity.getString(resourceId)
    }

    private fun layoutParamsFromOptions(options: JSONObject): FrameLayout.LayoutParams {
        if (!options.optBoolean("inline", false)) {
            return FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val density = cordova.activity.resources.displayMetrics.density
        val width = (options.optDouble("width") * density).toInt().coerceAtLeast(1)
        val height = (options.optDouble("height") * density).toInt().coerceAtLeast(1)
        val x = (options.optDouble("x") * density).toInt()
        val y = (options.optDouble("y") * density).toInt()

        return FrameLayout.LayoutParams(width, height).apply {
            leftMargin = x
            topMargin = y
        }
    }
}
