# OutSystems Mapbox Native Plugin


## Style readiness and sequential OutSystems actions

`initialize()`, `setMapStyle()` and `showOfflineRegion()` resolve only when the requested native style has loaded on Android and iOS. A fatal style-load error or a 30-second timeout rejects the operation. The timeout never signals readiness. Close cancels the pending action; late callbacks cannot complete a newer map session. Concurrent style requests are rejected: await the current request before replacing the style.

In an OutSystems JavaScript element, enable asynchronous execution and call `$resolve()` only after the plugin Promise succeeds. Call `$reject(error)` on failure so the flow does not proceed to layer operations. Keep initialization, marker registration and layer changes as separate sequential actions; do not launch layer toggles from a parallel aggregate/event while initialization is pending. Your existing initialization snippet already awaits `plugin.initialize(options)` and can remain unchanged with this native update.

After initialization resolves, apply layers and saved visibility settings. After each subsequent `setMapStyle()` resolves, recreate any custom sources/layers and reapply their visibility. A loaded style does not imply a GPS fix is available; location acquisition remains a separate asynchronous operation.

Native changes require rebuilding and installing the mobile application. Updating/publishing JavaScript alone does not replace the installed Android/iOS plugin binaries. Verify slow/failed style loads, immediate layer toggles, close during loading, reopen and later style changes on both devices before UAT acceptance.

## Close cleanup build: 1.0.4-close-cleanup.2

This cleanup release extends the existing teardown on `913103a`, retaining the behind-WebView transparency fix.

`MapboxPlugin.close()` stops the owned DOM observer/animation/retry bridge, releases native Cordova callback registrations, cancels pending operations and downloads, stops GPS/tracking/heading/compass work, and removes native touch routing. Android uses a removable routing container without replacing the WebView's touch listener. iOS removes only the plugin-owned overlay recognizers and gesture targets. Previous WebView appearance is restored when still owned by this plugin.

Await `MapboxPlugin.close()` before initializing the next map. Pending Promise operations reject with a cancellation error when the map closes; handle these rejections in your screen actions. Re-register event callbacks after a successful new `initialize()`. Repeated close calls are supported. Native Close buttons also notify JavaScript to stop its bridge. Completed offline regions remain stored; close cancels active transfers and clears the in-memory map session, not downloaded offline data. Retrieve a recorded path before close if it must be saved.

The package has passed JavaScript regression checks and Java/Swift syntax parsing. It has **not** been compiled with the consuming OutSystems application's native dependencies or tested on devices. Perform Android/iOS builds and the supplied device checks before production deployment.

### OutSystems screen OnDestroy

In the map screen's **OnDestroy** client action, add a JavaScript node with this exact code (no input/output parameters needed):

```javascript
var plugin = window.MapboxPlugin;
if (plugin && typeof plugin.close === "function") {
    window.__mapboxScreenClose = plugin.close().catch(function (error) {
        console.error("Mapbox screen cleanup failed", error);
        throw error;
    });
    // Handle the rejection even when navigation does not wait for OnDestroy.
    window.__mapboxScreenClose.catch(function () {});
}
```

`close()` immediately stops JavaScript listeners/observers and invalidates pending callbacks. Its Promise resolves after the native map and native touch routing are removed. OutSystems navigation need not wait for a Promise started by a JavaScript node. Before initializing a map on the next screen, wait for that close:

```javascript
Promise.resolve(window.__mapboxScreenClose).then(function () {
    return window.MapboxPlugin.initialize(/* your existing options */);
}).catch(function (error) {
    console.error("Mapbox initialization failed", error);
});
```

Keep the existing initialization options in that call. Ensure OnDestroy of the old screen has run before starting the next map; this plugin owns one map at a time. Cancel any application-owned timers or pending screen actions that could call `initialize()` again after exit. Register marker, waypoint, compass, and tracking callbacks only after the new initialization succeeds.

The close path explicitly unregisters Android marker clicks and map/gesture listeners, cancels camera animation, and destroys the map. iOS disables/detaches the map, removes its annotation managers and tap handlers, cancels subscriptions, and removes its touch overlay. Session guards discard delayed taps even after a new map opens. Android SDK cleanup steps are isolated so a runtime exception does not skip later cleanup or retain the visible map.

Legacy scripts using `__mapboxTouchBridge`, `__mapboxTouchHandlers`, `__mapboxTouchObserver`, and `__mapboxResizeObserver` are cleaned even if `stop()` is absent or throws: known frame/retry handles, observer references, and registered DOM handlers are removed. Unrelated DOM listeners are preserved. Remove the obsolete custom bridge from the screen; anonymous listeners, timers, or closure-only state not exposed through those globals cannot be discovered or cancelled by the plugin.

**Location and data caveats:** close stops this plugin's map-owned location, accuracy, heading, and path tracking. It does not stop another plugin's independent background-location service or clear its storage. This plugin does not implement a separate persistent background-location service. Save the path with `stopPathTracking()` before leaving if needed. Active offline downloads are cancelled; completed offline regions remain stored. Repeated close calls are safe. Screen exit does not mean application pause: do not use close for temporary backgrounding if the map session should resume.

**Deploy and verify:** point the existing OutSystems plugin dependency at the updated commit/ref and generate/install new Android and iOS native builds; publishing screen JavaScript alone does not update native code. SDK pins remain Android/iOS Mapbox 11.30.0. CI checks native helpers and Swift syntax, and builds clean Cordova Android/iOS apps against the full SDK. On both physical platforms test: exit during initialization, permission prompts, marker downloads and pinch/pan; tap where the map used to be; reopen repeatedly with the same marker IDs; confirm no old events; confirm independent background tracking continues; and verify the rest of the WebView remains interactive.

The smoke-build baseline uses Cordova CLI 12.0.0, cordova-android 14.0.1 (Java 17 / Android SDK 35), and cordova-ios 8.0.0. The iOS job uses an Xcode 26 toolchain: Mapbox 11.30.0 binary dependencies failed with the Xcode 16.4 compiler. Confirm your OutSystems MABS build stack supports these native dependencies. These clean-app builds do not certify every MABS version or other plugin combination.

Set the consuming iOS application's minimum deployment target explicitly to **14.0 or later**. A plain Cordova app needs `<preference name="deployment-target" value="14.0" />` in its app `config.xml`; the plugin's `default="14.0"` declaration alone did not raise the generated CocoaPods target in the smoke app. This setting is required before installing/resolving the Mapbox pod.

## Security behavior in 1.0.3

- Closing the map (including the native Close button) or resetting the WebView stops location/heading services, cancels downloads and pending permission actions, and clears map-session data and callbacks. Re-register callbacks after initializing a new map session.
- `MAPBOX_ACCESS_TOKEN` must be a public `pk.*` token. Both platform builds reject configured secret tokens. Initialization always reads the current configuration and removes legacy cached values, so token rotation works after app upgrades. Unconfigured build templates may still be prepared, but cannot initialize the map.
- Style URLs must be `mapbox://styles/<owner>/<style>` or HTTPS on an explicitly approved host. `MAPBOX_ALLOWED_STYLE_HOSTS` is a comma-separated build preference, defaulting to `api.mapbox.com`. Configure additional trusted style hosts through OutSystems Extensibility Configuration. Credentials in URLs, non-443 ports, fragments, HTTP and local-file URLs are rejected. Only allow hosts serving trusted styles: this entry-point policy does not inspect redirects or every resource referenced by a style.
- Marker `imageUrl` values must be HTTPS URLs or base64 `data:image/(png|jpeg|webp|gif)` URIs, up to 1 MiB of image data. `MAPBOX_ALLOWED_MARKER_IMAGE_HOSTS` is an optional comma-separated build preference; when set, only those hosts (including redirect targets) are allowed. When empty (the default), any HTTPS host is allowed.
- Native inputs are limited to approximately 4 MiB; at most 10,000 markers may exist at once, with marker IDs limited to 256 characters/bytes. Boundary batches allow 1,000 boundaries and 20,000 total vertices. Imported paths allow 2–20,000 points. Tracking stops location updates at the recording limit; call `stopPathTracking()` to retrieve the retained points. Stopping an empty recording returns an empty path with zero distance.
- Offline zooms must be between 2 and 18, with `minZoom <= maxZoom`; invalid values are rejected. Both circle and rectangle downloads are checked against a conservative bounding-box diagonal of 100 km and an estimated 50,000-tile budget (including lower zooms). This can reject large/high-zoom requests that older releases accepted. Polar and antimeridian-crossing bounds are rejected. The budget is an estimate, not a byte quota; Mapbox's tile-store constraints still apply. The consuming app should manage retained offline regions and its storage budget.
- Style URLs and underlying SDK error details are no longer included in plugin diagnostic logs.

From a development Git checkout, run `npm test` for the bridge and build-hook regressions. The `Native security regression tests` workflow also compiles/runs the Java and Swift input policy, executes the actual iOS empty-path method with a minimal host, and parses the iOS plugin sources. These checks do not replace device testing or a build of your consuming OutSystems application with its exact Cordova and Mapbox dependencies.

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
- `flyTo(options)`
- `getCamera()`
- `getCurrentLocationAccuracy()`
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
- `startPathTracking(options)`
- `stopPathTracking()`
- `pausePathTracking()`
- `continuePathTracking()`
- `loadPath(pathData, options)`
- `clearPaths()`
- `setPathVisibility(options)`
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

Android marker pins use a logical 72x96 canvas rendered at the device density,
then the existing 0.5 icon scale, for a 36x48 dp image footprint (including transparent
padding). Plain pins and downloaded-image pins use the same dimensions. This
keeps markers consistent across Android display densities; it is not tied to an
Android OS version. Older builds used a fixed 72x96 physical-pixel bitmap, which
made pins smaller in logical screen units on high-density devices. This correction
enlarges those pins to the intended logical size. iOS marker sizing is unchanged.

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

`setCamera` and `flyTo` support partial camera updates: omitted latitude, longitude, zoom, bearing, or pitch values preserve the current camera value on both platforms. `initialize` applies its optional bearing and pitch on both platforms.

`initialize` also accepts optional `minZoom` and `maxZoom` camera limits on Android and iOS:

```javascript
window.MapboxPlugin.initialize({
  latitude: 12.97,
  longitude: 77.59,
  zoom: 18,
  minZoom: 18,
  maxZoom: 22,
  inline: true
});
```

Each bound must be a finite JavaScript number in `[0, 25.5]` (decimals are allowed).
Strings, booleans, `NaN`, infinities, and out-of-range values reject the returned Promise.
When both bounds are provided, `minZoom` must be less than or equal to `maxZoom`;
equal values lock the camera zoom. Native entry points enforce the same validation.
Either bound may be supplied alone. `null`, `undefined`, or an omitted bound leaves
that bound at the SDK's previous/default behavior; no replacement default is injected.
If a supplied bound conflicts with the untouched SDK default, initialization rejects
instead of changing the omitted bound. Supply both bounds to request a range beyond
the defaults. Reinitializing without bounds restores the original SDK behavior.

These limits constrain the initial camera, zoom gestures, and later camera updates
(including `setCamera` and `flyTo`); the SDK clamps the requested camera zoom to its
bounds. They do not change offline downloads: download `minZoom`/`maxZoom` still use
the separate 2â€“18 range and existing defaults of 10/16.

Map viewport, resize, touch-region, and offline-selection rectangles use WebView-local native pixels on both platforms. Convert CSS viewport coordinates by multiplying them by `window.devicePixelRatio`; iOS converts those values to UIKit points internally without guessing from the rectangle size.

`trackCamera` accepts either a boolean or the strings `"true"`/`"false"` on both platforms. Marker IDs may be strings or JSON numbers and are returned as strings. `setPathVisibility` is idempotent when no path has been loaded.

Registering `onLocationAccuracyChanged` requests location permission when needed and starts an independent accuracy monitor on both platforms. The monitor stops when the map plugin closes; user tracking and path tracking may be enabled or disabled independently.

For behind-WebView maps, keep the WebView/page background transparent. Android and iOS now discover overlay touch regions automatically. See Touch Routing below.

### Navigation cancellation and location requests

Closing or reinitializing the map cancels unfinished operations from the previous
screen. A pending `moveToCurrentLocation()` can therefore reject after navigating
away even though the map on the next screen opens successfully. Cancellation is
not a rendering failure, and cancelled operations must not report success or
continue their old initialization chain.

The JavaScript bridge identifies lifecycle and superseded-location cancellations
with `error.code === 'MAPBOX_OPERATION_CANCELLED'`, `error.name ===
'MapboxCancellationError'` and `error.action` (the cancelled method).
`MapboxPlugin.isCancellationError(error)` also recognizes the exact legacy native
cancellation strings. It does not classify permission denial, disabled location
services or an unavailable location as cancellation.

In an OutSystems asynchronous JavaScript action, replace the terminal
`.catch($reject)` on the map startup/location chain with:

```javascript
.catch(function (error) {
  if (window.MapboxPlugin.isCancellationError(error)) {
    // The old screen's work is finished. Do not continue its startup chain here.
    $resolve();
    return;
  }
  $reject(error);
});
```

Keep this catch at the end of the chain, after all map setup operations. Resolving
the JavaScript action must not be followed by additional map actions for a screen
that has already been destroyed; guard the surrounding OutSystems flow with the
screen's active state. Call initialize once per screen entry and await it before
requesting location. Call close on screen destruction; avoid delayed cleanup
that can close a newer screen's map. This handler change is required in the
consuming app: the plugin cannot decide which user-facing error handlers to hide.

`Unable to get current location.` is a separate Android failure: the current
implementation requests a completely fresh fix with a five-second deadline and
can receive no location. Do not suppress it as cancellation or treat it as a map
initialization failure. Keep the map usable when location is unavailable and
offer a retry; inspect permissions, enabled location services and device logs
if it persists. Do not repeatedly request location from both OnReady and render
callbacks, which can supersede an in-flight request.

## Fly To

Use this when you want an animated camera transition that evokes flight, instead of the instant jump of `setCamera`. For long distances Mapbox builds the curved globe-style flight path automatically.

```javascript
window.MapboxPlugin.flyTo({
  latitude: $parameters.Latitude,
  longitude: $parameters.Longitude,
  zoom: 15,
  bearing: 0,
  pitch: 0,
  duration: 2000
})
  .then($resolve)
  .catch($reject);
```

`duration` is in milliseconds. When omitted, the SDK computes a suitable flight duration from the distance. The promise resolves when the animation starts, matching `setCamera`.

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

## Current Location Accuracy

`getCurrentLocationAccuracy()` returns the latest valid location accuracy:

```javascript
window.MapboxPlugin.getCurrentLocationAccuracy()
  .then(function (result) {
    // result.accuracy is the estimated horizontal uncertainty in meters.
    // result.accuracyLabel is a human-readable accuracy bucket.
    console.log(result.accuracy, result.accuracyLabel);
  })
  .catch($reject);
```

**Returned value:** `{ accuracy: number, accuracyLabel: string }`

`accuracy` is an estimated radius of uncertainty in **meters**, not a percentage.
The Android implementation uses the best recent GPS or network location (up to 30
seconds old). If permission is missing, no recent location is available, or the
location has no accuracy, it returns `{ accuracy: -1, accuracyLabel: "Unknown" }`.

Labels are assigned as follows:

- `Very accurate`: up to 5 m
- `Accurate`: over 5 m and up to 15 m
- `Moderately`: over 15 m and up to 30 m
- `Low`: over 30 m and up to 100 m
- `Poor`: over 100 m

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

## Path Tracking

Full reference: [docs/path-tracking-api.md](docs/path-tracking-api.md).

Path tracking lets you record the user's GPS movement as a visible polyline on the map. This is useful for hunt tracking, running routes, or any scenario where you need a trail of where the user has been.

### Start Path Tracking

Begins recording GPS points and drawing a live trail on the map. Optionally enables camera tracking at the same time.

```javascript
window.MapboxPlugin.startPathTracking({
  lineColor: "#FF0000",    // optional, default "#FF0000"
  lineWidth: 3.0,          // optional, default 3.0
  lineOpacity: 0.8,        // optional, default 1.0
  trackCamera: "true"      // optional, default "true" â€” also enable camera following
})
  .then($resolve)
  .catch($reject);
```

### Stop Path Tracking

Stops recording and returns all collected points so you can persist them.

```javascript
window.MapboxPlugin.stopPathTracking()
  .then(function (result) {
    // result.points = [{lat: 17.680, lon: 83.249}, ...]
    // result.distance = 1234.5  (meters)
    // result.duration = 45000   (milliseconds of moving time, pauses excluded)
    // result.segments = [{startIndex: 0, endIndex: 50}, {startIndex: 51, endIndex: 100}]
    // result.segmentCount = 2

    // Save to your OutSystems database here
  })
  .catch($reject);
```

### Pause Path Tracking

Temporarily pauses GPS collection and line drawing. The tracking session stays active but no points are collected or drawn while paused.

```javascript
window.MapboxPlugin.pausePathTracking()
  .then(function (result) {
    // result.status = "paused"
    // result.segmentCount = 1 (segments completed so far)
  })
  .catch($reject);
```

### Continue Path Tracking

Resumes GPS collection after a pause. Starts a new line segment from the current position (visual gap between paused sections).

```javascript
window.MapboxPlugin.continuePathTracking()
  .then(function (result) {
    // result.status = "continued"
    // result.segmentCount = 2 (total segments including the new one)
  })
  .catch($reject);
```

**Pause/Continue Flow:**
```
Start â†’ line segment A drawn
  Pause â†’ line stops, no GPS collected
  Continue â†’ new line segment B starts from current position
  Pause â†’ line stops again
  Continue â†’ new line segment C starts
Stop â†’ returns all points + segment boundaries
```

The `segments` array in the stop result tells you where each segment starts/ends in the flat points array:
```json
{
  "points": [{"lat": 1, "lon": 2}, ..., {"lat": 5, "lon": 6}],
  "segments": [
    {"startIndex": 0, "endIndex": 50},
    {"startIndex": 51, "endIndex": 100},
    {"startIndex": 101, "endIndex": 150}
  ],
  "segmentCount": 3
}
```

### Load a Pre-recorded Path

Draws a path from saved coordinates. Use this to display a past hunt track.

```javascript
window.MapboxPlugin.loadPath({
  points: [
    { lat: 17.680, lon: 83.249 },
    { lat: 17.681, lon: 83.250 },
    { lat: 17.682, lon: 83.251 }
  ],
  segments: [              // optional, from stopPathTracking: keeps the pause gaps
    { startIndex: 0, endIndex: 1 },
    { startIndex: 2, endIndex: 2 }
  ],
  lineColor: "#FF0000",    // optional, default "#FF0000"
  lineWidth: 3.0,          // optional, default 3.0
  lineOpacity: 0.8         // optional, default 1.0
})
  .then($resolve)
  .catch($reject);
```

### Clear Paths

Removes all drawn paths from the map.

```javascript
window.MapboxPlugin.clearPaths()
  .then($resolve)
  .catch($reject);
```

### Show Or Hide Paths

Toggle path visibility without removing it.

```javascript
window.MapboxPlugin.setPathVisibility({
  visible: true
})
  .then($resolve)
  .catch($reject);
```

### OutSystems Integration Example

**Start hunt:**
```javascript
window.MapboxPlugin.startPathTracking({ lineColor: "#FF0000" })
```

**Pause hunt (e.g., user takes a break):**
```javascript
window.MapboxPlugin.pausePathTracking()
```

**Continue hunt (resume after break):**
```javascript
window.MapboxPlugin.continuePathTracking()
```

**Stop hunt and save:**
```javascript
window.MapboxPlugin.stopPathTracking()
  .then(function (result) {
    // In a Client Action, call a Server Action to save:
    //   SaveHuntTrack(result.points, result.distance, result.duration, result.segments)
    // The Server Action stores to your Entity/Database
  })
```

**View past hunt:**
```javascript
// Fetch saved points from your database, then:
window.MapboxPlugin.loadPath({
  points: $parameters.SavedPoints,
  segments: $parameters.SavedSegments   // optional; without it the hunt draws as one line
})
```

## Markers

Reference for the marker functions on `window.MapboxPlugin`: adding, bulk loading, customizing, removing, and listening for taps. Every function except `onMarkerClick` returns a Promise.

| Function | Purpose |
|---|---|
| [`addMarker(options)`](#addmarkeroptions) | Add or replace one marker |
| [`loadMarkers(markers, options)`](#loadmarkersmarkers-options) | Add many markers in one call |
| [`removeMarker(id)`](#removemarkerid) | Remove one marker |
| [`clearMarkers()`](#clearmarkers) | Remove all markers |
| [`onMarkerClick(callback, errorCallback)`](#onmarkerclickcallback-errorcallback) | Receive marker tap events |

The map must be initialized before adding markers.

### Marker appearance

`isFind` / `IsFind` picks the marker shape. The image sits on a white card with a thin near-black outline that narrows below it to a rounded tip, anchored at the tip on the marker coordinate:

| Marker | Shape | Image |
|---|---|---|
| Find (`isFind: true`) | Rounded square with a flat chin and tip | `imageUrl` (for example an S3 URL), center-cropped to a rounded square |
| Waypoint (`isFind` false or omitted) | Circle with a teardrop tip | `imageBinary` (converted), or `imageUrl`, center-cropped to a circle |

- Until the image loads, or when there is none: a Find shows a light grey tile with a magnifying glass; a Waypoint shows a red compass crosshair on the white card.
- `pinColor` overrides the outline and tip color (default `#1F1F1F`), for example `"#16A34A"` for a green outline.
- Both shapes use the same 72 × 96 canvas and tip position as the earlier teardrop pin, so markers stay at the same place on the map.
- `pinStyle: "photo"` switches to the larger photo pin: a 52x63 teardrop (default color `#DD001D`) with a large circular photo and no outline or shadow, tip on the marker coordinate. Until the image loads (or if it fails) a light grey disc is shown at the same size, so the marker does not change size. `pinColor` still applies. Any other value, or omitting it, keeps the classic pin. The same size is used on Android and iOS.

### addMarker(options)

Adds one marker. If a marker with the same `id` already exists, it is replaced.

#### Parameters

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `id` | string or number | No | Current time in milliseconds | Unique marker ID, up to 256 characters. Returned as a string. |
| `latitude` | number | Yes | `0` | Between -90 and 90. |
| `longitude` | number | Yes | `0` | Between -180 and 180. |
| `isFind` | boolean | No | `false` | `true`: square Find marker. `false`: circular Waypoint marker. |
| `pinColor` | string | No | Near-black (`#1F1F1F`) | `#RRGGBB` or `#RRGGBBAA` (alpha last). Invalid values fall back to the default. |
| `imageUrl` | string | No | None | Image shown inside the pin. See [Image sources](#marker-image-sources). |
| `imageBinary` | Binary (base64 string), data URI, `ArrayBuffer`, `Uint8Array` or byte array | No | None | **Waypoint markers only.** Image bytes, converted in the WebView. Takes priority over `imageUrl`. Ignored when `isFind` is `true`. See [Waypoint binary images](#waypoint-binary-images). |
| `imageMimeType` | string | No | Detected from the bytes | Optional hint such as `image/png`. The file signature wins when it is recognized. |
| `pinStyle` | string | No | Classic pin | `"photo"` for the larger photo pin. See [Marker appearance](#marker-appearance). |
| `isOnline` | boolean | No | `true` | `true` when the marker is synced with the backend, `false` when it was created on the device and has not been saved yet (so it has no server-assigned ID). The pin looks the same either way; the flag is returned in [`onMarkerClick`](#onmarkerclickcallback-errorcallback) events. |

`latitude` and `longitude` default to `0`, which is a valid coordinate in the Gulf of Guinea. Always pass them.

#### Returns

A Promise that resolves with `{ id: string }`.

#### Errors

The Promise rejects with one of these messages:

| Message | Cause |
|---|---|
| `Map is not initialized.` | `initialize` has not completed, or the map was closed. |
| `Invalid coordinates: latitude must be in [-90, 90], longitude in [-180, 180].` | Coordinates out of range. |
| `Marker imageUrl must be an HTTPS URL on an allowed host or a base64 image data URI up to 1 MiB.` | `imageUrl` is not an [allowed image source](#marker-image-sources). |
| `Marker unavailable or marker/id limit exceeded.` (Android)<br>`Marker/id limit exceeded.` (iOS) | 10,000 markers already exist, or the `id` is empty or longer than 256 characters. |
| `Invalid id: must be a string, number, or boolean.` / `Invalid id: must not be empty.` (iOS only) | Unusable `id`. |
| `Map input exceeds the 4 MiB limit.` | The options, including any data URI, exceed 4 MiB. |

#### Example

```javascript
window.MapboxPlugin.addMarker({
  id: $parameters.Id,
  latitude: $parameters.Latitude,
  longitude: $parameters.Longitude,
  isFind: $parameters.IsFind,
  pinColor: "#16A34A",
  imageUrl: "https://cdn.example.com/avatars/42.png"
})
  .then(function (result) {
    $parameters.MarkerId = result.id;
    $resolve();
  })
  .catch($reject);
```

### loadMarkers(markers, options)

Adds many markers in one native call. Use this instead of calling `addMarker` in a loop.

#### Parameters

`markers` is an array of marker objects. Their keys are capitalized to match the OutSystems structure, unlike `addMarker`:

| Key | Type | Required | Default | Description |
|---|---|---|---|---|
| `Id` | string or number | No | The item's array index | Unique marker ID, up to 256 characters. |
| `Latitude` | number | Yes | `0` | Between -90 and 90. |
| `Longitude` | number | Yes | `0` | Between -180 and 180. |
| `IsFind` | boolean | No | `false` | Same as `isFind` in `addMarker`. |
| `PinColor` | string | No | Near-black (`#1F1F1F`) | Same as `pinColor` in `addMarker`. |
| `ImageUrl` | string | No | None | Same as `imageUrl` in `addMarker`. |
| `ImageBinary` | Binary Data / base64 | No | None | Same as `imageBinary` in `addMarker` (Waypoints only). |
| `ImageMimeType` | string | No | Detected | Same as `imageMimeType` in `addMarker`. |
| `PinStyle` | string | No | Classic pin | Same as `pinStyle` in `addMarker`. |
| `IsOnline` | boolean | No | `true` | Same as `isOnline` in `addMarker`. `true` if the marker is synced, `false` if it is still offline. |

`options` is optional:

| Field | Type | Default | Description |
|---|---|---|---|
| `replace` | boolean | `true` | `true` removes all existing markers first. `false` keeps them and adds or replaces by `Id`. |

#### Returns

A Promise that resolves with no value.

#### Validation and errors

Every marker is validated before any marker is changed. The whole call is rejected, and existing markers are untouched, when:

| Message | Cause |
|---|---|
| `Too many markers: maximum allowed is 10000.` | More than 10,000 items in the array. Checked in JavaScript. |
| `Too many markers: maximum total is 10000.` | Existing plus new markers would exceed 10,000 when `replace` is `false`. |
| `Invalid marker id.` | An `Id` is empty or longer than 256 characters. |
| `Marker imageUrl must be an HTTPS URL on an allowed host or a base64 image data URI up to 1 MiB.` | An `ImageUrl` is not an [allowed image source](#marker-image-sources). |
| `Map is not initialized.` | The map is not ready. |
| `Map input exceeds the 4 MiB limit.` | The call's JSON exceeds 4 MiB. Many data URIs add up quickly; prefer HTTPS URLs for bulk loads. |

Items with invalid coordinates do not reject the call. They are skipped silently.

#### Example

```javascript
// $parameters.Markers: List of { Id, Latitude, Longitude, IsFind, PinColor, ImageUrl, IsOnline }
window.MapboxPlugin.loadMarkers($parameters.Markers, { replace: true })
  .then($resolve)
  .catch($reject);
```

### removeMarker(id)

Removes one marker. Resolves with no value, whether or not the marker existed.

| Parameter | Type | Description |
|---|---|---|
| `id` | string | The marker's ID. |

The ID must be a non-empty string; numeric IDs are rejected on both platforms. See [Remove one marker](#remove-one-marker-android-and-ios) for the optional callbacks and error behavior.

```javascript
window.MapboxPlugin.removeMarker(String($parameters.Id)).then($resolve).catch($reject);
```

To replace an offline marker with its synced version, remove it by its temporary ID and add it again with the server ID and `isOnline: true`.

### clearMarkers()

Removes every marker. Resolves with no value.

```javascript
window.MapboxPlugin.clearMarkers().then($resolve).catch($reject);
```

### onMarkerClick(callback, errorCallback)

Registers one listener for marker taps. Registering again replaces the previous listener. It does not return a Promise.

The callback receives:

| Field | Type | Description |
|---|---|---|
| `type` | string | Always `"markerClicked"`. |
| `id` | string | ID of the tapped marker. |
| `isOnline` | boolean | The `isOnline` / `IsOnline` value the marker was added with (`true` if it was not set). |
| `latitude` | number | The marker's latitude. |
| `longitude` | number | The marker's longitude. |

```javascript
window.MapboxPlugin.onMarkerClick(function (event) {
  $actions.OnMarkerSelected(event.id);
});
```

- Events are rate-limited to one every 100 ms.
- On Android, tapping the map within 150 m of a marker also reports the nearest marker. On iOS, only a tap on the pin itself counts.

### Marker image loading

1. The marker appears immediately as a plain pin in its color.
2. The image downloads in the background.
3. When the download finishes, the pin switches to show the image.

- **One download per URL.** Markers sharing an `imageUrl` share one download, even if they are added while it is still in progress.
- **Cached in memory.** Up to 64 recent images are kept, so re-adding markers with the same image is instant. The cache is not saved to disk and is cleared when the app restarts.
- **Failures keep the plain pin.** This covers network errors, timeouts (10 seconds), non-200 responses, files over 1 MiB, and data that is not an image. Nothing is reported to JavaScript. The download is retried the next time a marker uses that URL.
- **Images are scaled down** to at most 128 pixels on the long side before drawing. Animated GIFs show their first frame.
- **Replaced or removed markers are safe.** If a marker is removed or given a different image before its download finishes, the late download does not affect it. Downloads that finish after the map is closed are ignored.

### Marker image sources

`imageUrl` (`ImageUrl` in `loadMarkers`) accepts two forms:

| Form | Example | Rules |
|---|---|---|
| HTTPS URL | `https://cdn.example.com/a.png` | HTTPS only, no user name or password in the URL. Must pass the host allowlist, if one is configured, including every redirect. |
| Base64 data URI | `data:image/png;base64,iVBORw0...` | Type `png`, `jpeg`, `jpg`, `webp`, or `gif`. Base64 only, up to 1 MiB of image data. Useful for OutSystems binary data, for example `"data:image/png;base64," + BinaryToBase64(Image)`. |

### Waypoint binary images

Find and Waypoint markers use the same `addMarker` / `loadMarkers` functions and the same native pin renderer; `isFind` / `IsFind` is the only difference. Images are handled per type:

| Marker | Image input | What happens |
|---|---|---|
| Find (`isFind: true`) | `imageUrl` | Unchanged. The URL is passed to native and downloaded there. Binary fields are ignored (one console warning). |
| Waypoint (`isFind` false or omitted) | `imageBinary` | Converted in the WebView into a `data:image/...;base64,` source, then rendered through the existing `imageUrl` path. |
| Waypoint without binary | `imageUrl` | Unchanged, as before. |

Conversion of each Waypoint binary:

1. Accepts OutSystems Binary Data (a base64 string), a data URI, URL-safe or line-wrapped base64, `ArrayBuffer`, `Uint8Array` or an array of bytes.
2. Detects PNG, JPEG, GIF, WebP and BMP from the file signature, so a wrong or `application/octet-stream` type is fine.
3. Decodes the image with the WebView, takes the center square (what the round pin shows anyway) and redraws it at up to 192 x 192: JPEG stays JPEG, everything else becomes PNG (keeps transparency). EXIF rotation is applied, and photos up to 10 MiB are accepted because they are reduced before crossing the bridge.
4. If the WebView cannot decode it, a PNG/JPEG/GIF/WebP up to 1 MiB is sent as-is; otherwise the marker shows a plain pin (or its `imageUrl`, if also given) and a console warning is logged, like a failed image download.

Data that is not binary at all (for example invalid base64) rejects the call before any marker changes. Markers that carry identical bytes share one conversion and one native image. Each Waypoint keeps its own image, ID, coordinates, color and `pinStyle`.

OutSystems example (`Markers` is a List of `{ Id, Latitude, Longitude, IsFind, ImageUrl, ImageBinary, ... }`):

```javascript
// Find markers: ImageUrl. Waypoint markers: ImageBinary (Binary Data).
window.MapboxPlugin.loadMarkers($parameters.Markers, { replace: true })
  .then($resolve)
  .catch($reject);
```

```javascript
window.MapboxPlugin.addMarker({
  id: $parameters.WaypointId,
  latitude: $parameters.Latitude,
  longitude: $parameters.Longitude,
  isFind: false,
  imageBinary: $parameters.Photo   // OutSystems Binary Data input
}).then($resolve).catch($reject);
```

Notes:

- Calls with a binary image resolve after conversion. Await them before `clearMarkers()`, `removeMarker()` or another load that must run after them. Calls without binary fields run exactly as before.
- If the converted list is larger than the 4 MiB bridge limit, `loadMarkers` sends it in several native calls: the first applies `replace`, the rest append. Missing `Id`s keep their list index. If a later batch fails, earlier batches stay on the map.
- `close()` during conversion rejects the call with a cancellation error and nothing is drawn.

Rejected examples: `http://...`, `file:///...`, `https://user:pass@host/...`, `data:text/html;base64,...`, `data:image/png,<raw data>`.

#### Restricting image hosts

By default, any HTTPS host is allowed. To allow only specific hosts, set the `MAPBOX_ALLOWED_MARKER_IMAGE_HOSTS` plugin variable to a comma-separated list in OutSystems Extensibility Configuration, next to the access token, then rebuild the app:

```json
{
  "plugin": {
    "url": "https://github.com/SupriyaDevEnv/cordova-mapbox-outsystems.git",
    "variables": [
      { "name": "MAPBOX_ACCESS_TOKEN", "value": "pk.your_public_runtime_token_here" },
      { "name": "MAPBOX_ALLOWED_MARKER_IMAGE_HOSTS", "value": "cdn.example.com,images.example.com" }
    ]
  }
}
```

- Host names must match exactly (case-insensitive). Subdomains are not included automatically.
- Downloads that redirect to an unlisted host fail, and the marker keeps its plain pin.
- The allowlist does not affect data URIs.

### Marker limits

| Limit | Value |
|---|---|
| Markers on the map | 10,000 |
| Marker ID length | 256 characters |
| Items per `loadMarkers` call | 10,000 |
| Size of one call | 4 MiB of JSON |
| Image data | 1 MiB per image |
| Image download timeout | 10 seconds |
| Tap events | One per 100 ms |

### Marker platform differences

- **Pin size.** The pin is drawn 72 × 96 on both platforms, but Android uses physical pixels and iOS uses points. On a typical 3Ã— Android phone the pin appears about one third the size of the iOS pin, so images inside it are smaller too.
- **Tap area.** Android also reports taps on the map within 150 m of a marker. iOS reports only taps on the pin.
- **Non-object items in `loadMarkers`.** Android skips `null` items. On iOS, one non-object item makes the whole array read as empty, so with `replace: true` all markers are cleared.

### Marker changes from earlier versions

- **Breaking:** `loadMarkers` item keys are capitalized: `Id`, `Latitude`, `Longitude`, `IsFind`. Items using `id`, `latitude`, `longitude`, or `isFind` are no longer read. Every such item falls back to an index ID and coordinates `0, 0`. `addMarker` still uses lowercase keys.
- **New:** `pinColor` / `PinColor` and `imageUrl` / `ImageUrl`.
- **New:** `isFind` pins show a magnifying-glass icon on both platforms.
- **Changed (1.0.6):** Find markers are white rounded-square cards and Waypoint markers are white circles, each with a black outline and tip, replacing the red/blue teardrop pin. Size, anchor and position are unchanged; `pinColor` and `pinStyle: "photo"` work as before.
- **New:** the `MAPBOX_ALLOWED_MARKER_IMAGE_HOSTS` preference.
- **New (1.0.5):** `imageBinary` / `ImageBinary` and `imageMimeType` / `ImageMimeType` for Waypoint markers. Find markers are unchanged.

## Touch Routing

On iOS, dragging or pinching either the native map or the behind-WebView overlay pauses camera following and cancels the current camera animation. Location updates and an already-running path recording continue. Ending the gesture does not resume following; call `moveToCurrentLocation` or enable user tracking explicitly to resume. A pending current-location result still resolves if a gesture pauses its camera movement before the fix arrives.

On Android and iOS, a gesture starting in an overlay exclusion rectangle stays with the WebView. Otherwise it goes to native Mapbox only when it starts inside the laid-out map rectangle. Touches outside the map stay with the WebView. The chosen target keeps the entire gesture, including movement outside its original rectangle.

### Automatic Android and iOS overlay regions

`initialize({ behindWebView: true, ... })` starts plugin-owned DOM tracking by default on Android and iOS. No separate OutSystems touch script is needed. It tracks standard interactive HTML controls and roles, OutSystems bottom sheets, popups, modals and sidebars, `.map-control-btn`, `.map-touchable`, `.map-touch-region`, `[data-map-touch-region]`, and inline `cursor: pointer` tag controls inside `.tags-container`.

- Keep your existing map initialization and `setViewport`/`resizeMap` calls. Automatic tracking discovers overlays only; it does not infer which DOM element is your map container.
- Mark custom clickable or draggable containers with the `map-touch-region` class. Do not put this class on the map container or the whole screen unless all touches there should go to the WebView.
- Rectangles are clipped to the viewport and scroll containers, converted from CSS coordinates to native pixels using device pixel ratio, and deduplicated. Hidden and `pointer-events: none` controls are excluded.
- The plugin updates regions after DOM changes, scrolling, resizing, loads, and relevant CSS animations/transitions. It disconnects observers and listeners on `close()`, reinitialization, or page exit. Call `close()` when leaving the map screen in an OutSystems single-page navigation flow.
- Updates are serialized and failed updates are retried. If more than 50 independent regions on Android or 20 on iOS remain, the plugin temporarily routes the entire viewport to the WebView and warns in the console. Group related controls under `map-touch-region` to reduce the count and restore map gestures.
- Normal WebView events handle clicks. The plugin does not simulate `.click()` or intercept DOM touch events. Controls must be reachable through normal DOM hit testing; check overlapping elements and `pointer-events` if a custom control still fails.

After rebuilding the Android and iOS apps with this version, disable/remove the old standalone touch script. Its `window.__mapboxTouchBridge.stop()` is also called at automatic initialization if present, but the old script must not run again afterward. Remove the standalone touch script on both Android and iOS.

Test map pan/pinch, tags, bottom-sheet drags, popup backdrops, scrolling, rotation, and repeated open/close on physical Android and iOS devices. DOM/bridge tests do not replace device testing.

OutSystems feedback messages (`.feedback-message`, including error, warning, success and information variants) are also tracked automatically on Android and iOS. Their taps remain in the WebView so existing OutSystems click/dismiss handlers can run. The plugin does not add a dismissal action to messages that do not already have one.

If your page disables pointer events on map overlays, enable them for feedback messages in the app stylesheet:

```css
.feedback-message,
.feedback-message * {
  pointer-events: auto !important;
}
```

Rebuild both native apps after updating the plugin. Verify a message can receive taps while visible, and map gestures work in its former area after it disappears. No separate message touch script or additional class on generated feedback messages is required.

### Manual regions and compatibility

Set `autoTouchRouting: false` to disable broad automatic discovery. For compatibility with existing OutSystems initialization JS, behind-WebView maps still track `.map-control-btn` elements in this mode. Only their visible, clipped bounds are reserved for WebView input; the surrounding map remains available to native gestures. This deliberately changes the previous full opt-out behavior so existing map buttons work without editing initialization JS. Calling `setTouchableRects` explicitly (including `[]`) takes full manual ownership and stops all automatic tracking until the next initialization, so manual and automatic writers do not overwrite each other. The existing method and native-pixel coordinate convention remain supported. Both platforms accept WebView-local native-pixel rectangles (CSS viewport coordinates multiplied by device pixel ratio). iOS converts them to UIKit points and checks them relative to the WebView, so map resizing does not discard off-map exclusions.

```javascript
var dpr = window.devicePixelRatio || 1;
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

The Mapbox access token is configured once through OutSystems extensibility configuration. It never crosses the JavaScript bridge: passing `token` in `initialize()` is ignored for security and never reaches native.

```json
{
  "plugin": {
    "url": "https://github.com/SupriyaDevEnv/cordova-mapbox-outsystems.git",
    "variables": [
      {
        "name": "MAPBOX_ACCESS_TOKEN",
        "value": "pk.your_public_runtime_token_here"
      }
    ]
  }
}
```

## OutSystems Setup

1. Push this folder to a Git repository.
2. Add the plugin repository URL to the mobile app extensibility configuration.
3. Create OutSystems Client Actions that call the JavaScript examples above.
4. Generate the Android/iOS mobile app.
5. Test on a real device.

## Production packaging

The production plugin contains `plugin.xml`, `package.json`, `README.md`, `www/`, `src/`, and `hooks/`. Both hooks, the Android Gradle file, and all Android/iOS source files are build inputs and must remain included.

The npm `files` allowlist excludes development files from `npm pack` (npm also includes `package.json` and `README.md`). `.gitattributes` excludes `tests/`, `.github/`, and Git configuration files from `git archive` and GitHub source ZIPs. All regression tests, including the five native Python tests, remain in Git and CI; Python is not required to install or run the packaged plugin.

Create an OutSystems plugin ZIP from a committed revision:

```sh
git archive --format=zip --output=../cordova-mapbox-outsystems.zip HEAD
```

The ZIP has `plugin.xml` at its root. Do not ZIP the working directory manually: that would include development files and bypass the archive exclusions. A Git clone still contains tests and CI files; for a clean distributable use the ZIP or npm package. Pin the reviewed commit when configuring a production Git installation.

From a development checkout, run `python3 tests/check-package.py` to verify the actual ZIP and npm tarball, including every file referenced by `plugin.xml`. Native build and device validation must still be performed with the consuming OutSystems application.

The known rollback revision is `f88b385f0dae6382d2c8fcca80a438be1a68404b`. This packaging change preserves its history. To generate the original rollback package, use that exact revision instead of `HEAD`; it retains the packaging rules and contents of that revision.

## SDK Versions

- Android uses Mapbox Maps `11.30.0` by default.
- iOS pins Mapbox Maps to `11.30.0`.
- iOS reads the token natively and assigns it via `MapboxOptions.accessToken`.

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


### Touch routing regression check on a device

Rebuild the Cordova app using this plugin revision. Keep existing OutSystems initialization JS, including `autoTouchRouting: false`, unchanged when map controls use `.map-control-btn`.

1. Tap each map control and verify its OutSystems action fires once. Pan, pinch and rotate in the map area between controls.
2. Start a drag on a control or an automatically discovered sheet and move outside its original bounds; verify the gesture stays with its starting target. Broad sheet discovery requires the default automatic mode.
3. Navigate away and back repeatedly, then close/reinitialize. Verify replacement buttons work, removed buttons leave no stale touch exclusions, and map/marker callbacks fire only for the current session.
4. Open/close overlays, scroll, and rotate the device. Verify control bounds update. For manual routing, call `setTouchableRects` and verify automatic updates stop until reinitialization.

The JavaScript regression suite exercises both platform configurations and lifecycle races with a simulated DOM/bridge. Native CI checks and device checks are separate; passing the JavaScript suite does not establish end-to-end device behavior.

### Remove one marker (Android and iOS)

`MapboxPlugin.removeMarker(markerId, success?, error?)` removes the marker created
by `addMarker` or `loadMarkers` with that exact string ID. It returns a Promise and
also accepts optional success/error callbacks:

```javascript
await MapboxPlugin.removeMarker('record-123');

// Callback form for Cordova/OutSystems integrations:
MapboxPlugin.removeMarker('record-123', function () {
  console.log('Marker removed');
}, function (error) {
  console.error(error);
});
```

- Success has no payload. Unknown/already removed IDs succeed without changing other markers.
- Missing, empty, or non-string IDs fail with `markerId must be a non-empty string.`.
  Numeric record IDs must be converted to strings explicitly.
- Without an error callback, failures reject the Promise. With callbacks, the returned
  Promise follows the callback result, just like `promise.then(success, error)`.
- Removal clears marker metadata and pending image subscriptions, while keeping other
  markers and the registered marker-click callback active. Closing/reinitializing the
  map cancels pending operations through the existing cancellation mechanism.
- The native Cordova action remains `removeMarker` with arguments `[{ id: markerId }]`.

Device verification: add two markers (including an image marker), click both, remove
one while its image is loading, and confirm only the remaining marker still produces
clicks. Repeat removal, try an unknown ID and invalid IDs, then re-add the removed ID.
Run this on Android and iOS; JavaScript bridge tests do not exercise native rendering.

