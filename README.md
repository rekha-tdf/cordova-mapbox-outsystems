# OutSystems Mapbox Native Plugin

Cordova-style native Mapbox plugin for OutSystems mobile apps.

The plugin renders a native Mapbox map and exposes JavaScript actions through:

```javascript
window.MapboxPlugin
```

It supports inline maps, behind-WebView maps, markers, current-location movement, user tracking, offline downloads, waypoint selection, and boundary overlays.

## Actions

- `initialize(options)`
- `setViewport(options)`
- `setTouchableRects(rects)`
- `setCamera(options)`
- `getCamera()`
- `enableUserLocation()`
- `moveToCurrentLocation(options)`
- `setUserTrackingEnabled(options)`
- `setDeviceHeadingEnabled(options)`
- `setHeadingFollowMode(options)`
- `addMarker(options)`
- `loadMarkers(markers, options)`
- `removeMarker(id)`
- `clearMarkers()`
- `loadBoundaries(boundaries, options)`
- `setBoundaryVisibility(options)`
- `clearBoundaries()`
- `downloadOfflineRegion(options)`
- `downloadOfflineRegionForRect(options)`
- `showOfflineRegion(options)`
- `deleteOfflineRegion(options)`
- `onOfflineDownloadProgress(callback, errorCallback)`
- `setWaypointSelectionEnabled(options)`
- `onWaypointSelected(callback, errorCallback)`
- `onMarkerClick(callback, errorCallback)`
- `close()`

## Initialize Map

Call `initialize` once when the map screen opens.

```javascript
var dpr = window.devicePixelRatio || 1;

var topOffset = 90;
var bottomOffset = 55;
var mapHeight = window.innerHeight - topOffset - bottomOffset;

window.MapboxPlugin.close()
  .catch(function () {})
  .then(function () {
    return window.MapboxPlugin.initialize({
      behindWebView: true,
      inline: true,

      x: 0,
      y: Math.round(topOffset * dpr),
      width: Math.round(window.innerWidth * dpr),
      height: Math.round(mapHeight * dpr),

      token: $parameters.Token,
      styleUrl: $parameters.Style,

      latitude: $parameters.Latitude,
      longitude: $parameters.Longitude,
      zoom: $parameters.Zoom,
      bearing: 0,
      pitch: 0
    });
  })
  .then($resolve)
  .catch($reject);
```

For iOS and Android behind-WebView maps, keep the WebView/page background transparent and call `setTouchableRects` for OutSystems controls that must remain clickable.

## Move To Current Location Once

Use this when you want the map to move to the user location once, without continuous tracking.

```javascript
window.MapboxPlugin.enableUserLocation()
  .then(function () {
    return window.MapboxPlugin.moveToCurrentLocation({
      zoom: 15
    });
  })
  .then($resolve)
  .catch($reject);
```

This is different from tracking. The user can still drag the map away after this call.

## User Tracking

Use tracking only when the map should continue following the user.

```javascript
window.MapboxPlugin.setUserTrackingEnabled({
  enabled: true
})
  .then($resolve)
  .catch($reject);
```

Disable tracking when the user should be free to move the map without it snapping back:

```javascript
window.MapboxPlugin.setUserTrackingEnabled({
  enabled: false
})
  .then($resolve)
  .catch($reject);
```

## Boundary Overlays

Load boundaries after `initialize` succeeds. Do not initialize the map again just to show or hide boundaries.

Accepted boundary format:

```json
[
  {
    "Id": 90838863,
    "geometry": [
      { "lat": 17.6807217, "lon": 83.2492601 },
      { "lat": 17.6817917, "lon": 83.2522383 },
      { "lat": 17.6812552, "lon": 83.2549626 }
    ]
  }
]
```

Records without a valid `geometry` array are skipped.

### Load Boundaries

```javascript
var boundaryData = typeof $parameters.BoundaryJson === "string"
  ? JSON.parse($parameters.BoundaryJson)
  : $parameters.BoundaryJson;

window.MapboxPlugin.loadBoundaries(boundaryData, {
  visible: true,
  fillColor: "#2E7D32",
  fillOpacity: 0.18,
  lineColor: "#FF0000"
})
  .then($resolve)
  .catch($reject);
```

### Show Or Hide Boundaries

Call this from your enable/disable button.

```javascript
window.MapboxPlugin.setBoundaryVisibility({
  visible: $parameters.IsVisible
})
  .then($resolve)
  .catch($reject);
```

### Clear Boundaries

```javascript
window.MapboxPlugin.clearBoundaries()
  .then($resolve)
  .catch($reject);
```

## Markers

### Add One Marker

```javascript
window.MapboxPlugin.addMarker({
  id: $parameters.Id,
  latitude: $parameters.Latitude,
  longitude: $parameters.Longitude
})
  .then($resolve)
  .catch($reject);
```

### Load Many Markers

```javascript
window.MapboxPlugin.loadMarkers($parameters.Markers, {
  replace: true
})
  .then($resolve)
  .catch($reject);
```

### Marker Click Callback

```javascript
window.MapboxPlugin.onMarkerClick(function (event) {
  console.log("Marker clicked", event.id, event.latitude, event.longitude);
});
```

## Touch Routing

When the map is behind the WebView, touches are routed to the native map unless they fall inside configured clickable rectangles.

```javascript
window.MapboxPlugin.setTouchableRects([
  {
    x: 0,
    y: 0,
    width: window.innerWidth * dpr,
    height: 90 * dpr
  },
  {
    x: 0,
    y: (window.innerHeight - 55) * dpr,
    width: window.innerWidth * dpr,
    height: 55 * dpr
  }
])
  .then($resolve)
  .catch($reject);
```

## Token Handling

Pass the public runtime token from the OutSystems client:

```javascript
window.MapboxPlugin.initialize({
  token: "pk.your_public_token",
  styleUrl: "mapbox://styles/mapbox/streets-v12",
  latitude: 12.9716,
  longitude: 77.5946,
  zoom: 12
});
```

Or inject a fallback token through OutSystems extensibility configuration:

```json
{
  "plugin": {
    "url": "https://github.com/devnandagopaljb/cordova-mapbox-outsystems.git",
    "variables": [
      {
        "name": "MAPBOX_ACCESS_TOKEN",
        "value": "pk.your_public_runtime_token_here"
      },
      {
        "name": "MAPBOX_DOWNLOADS_TOKEN",
        "value": "sk.your_downloads_token_here_if_required"
      }
    ]
  }
}
```

Do not expose `MAPBOX_DOWNLOADS_TOKEN` to client-side code.

## OutSystems Setup

1. Push this folder to a Git repository.
2. Add the plugin repository URL to the mobile app extensibility configuration.
3. Create OutSystems Client Actions that call the JavaScript examples above.
4. Generate the Android/iOS mobile app.
5. Test on a real device.

## SDK Versions

- Android uses Mapbox Maps `11.20.2` by default.
- iOS uses Mapbox Maps `~> 11.0`.
- iOS runtime token assignment uses `MapboxOptions.accessToken`.

## Android Size Reduction

The Android build filters native libraries to production device ABIs by default:

```text
arm64-v8a,armeabi-v7a
```

Override this in OutSystems only when needed:

```json
{
  "name": "MAPBOX_ANDROID_ABIS",
  "value": "arm64-v8a"
}
```

For emulator testing, include `x86_64`:

```json
{
  "name": "MAPBOX_ANDROID_ABIS",
  "value": "arm64-v8a,armeabi-v7a,x86_64"
}
```

## Notes

- Call `initialize` once per map screen load.
- Call `loadBoundaries` after `initialize`.
- Use `setBoundaryVisibility` for show/hide. Do not reinitialize the map for boundary toggles.
- Use `moveToCurrentLocation` for a one-time move to user location.
- Use `setUserTrackingEnabled` only when the map should keep following the user.
- Background location tracking is not part of the Mapbox map view. Add a background geolocation plugin or native background location feature for that use case.
