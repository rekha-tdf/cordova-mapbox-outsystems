const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const android = fs.readFileSync('src/android/MapboxPluginEntry.java', 'utf8');
const androidPermissions = fs.readFileSync('src/android/MapboxPluginPermissionEntry.java', 'utf8');
const ios = fs.readFileSync('src/ios/MapboxPlugin.swift', 'utf8');
const iosMarkerIcons = fs.readFileSync('src/ios/MarkerIcons.swift', 'utf8');

function javaMethod(name, nextName) {
  return android.split('private void ' + name + '(')[1].split('private void ' + nextName + '(')[0];
}

test('camera actions preserve omitted center coordinates on both platforms', () => {
  for (const body of [javaMethod('setCamera', 'flyTo'), javaMethod('flyTo', 'enableUserLocation')]) {
    assert.match(body, /getCameraState\(\)\.getCenter\(\)/);
    assert.match(body, /optDouble\("latitude", currentCenter\.latitude\(\)\)/);
    assert.match(body, /optDouble\("longitude", currentCenter\.longitude\(\)\)/);
  }
  for (const name of ['setCamera', 'flyTo']) {
    const body = ios.split('func ' + name + '(command:')[1].split('@objc', 1)[0];
    assert.match(body, /defaultValue: mapView\.cameraState\.center\.latitude/);
    assert.match(body, /defaultValue: mapView\.cameraState\.center\.longitude/);
  }
});

test('initial camera applies bearing and pitch on both platforms', () => {
  const androidInitialize = android.split('private void initialize(')[1].split('private String getAccessToken')[0];
  assert.match(androidInitialize, /optDouble\("bearing", 0\.0\)/);
  assert.match(androidInitialize, /optDouble\("pitch", 0\.0\)/);
  assert.match(androidInitialize, /\.bearing\(bearing\)/);
  assert.match(androidInitialize, /\.pitch\(pitch\)/);

  const iosInitialize = ios.split('func initialize(command:')[1].split('@objc(setCamera:', 1)[0];
  assert.match(iosInitialize, /options\["bearing"\]/);
  assert.match(iosInitialize, /options\["pitch"\]/);
  assert.match(iosInitialize, /bearing: bearing/);
  assert.match(iosInitialize, /pitch: pitch/);
});

test('iOS consistently converts native-pixel rectangles to UIKit points', () => {
  const frame = ios.split('private func frameFromOptions')[1].split('private func touchRectFromOptions')[0];
  assert.doesNotMatch(frame, /appearsDevicePixelScaled/);
  assert.match(frame, /options\["x"\][\s\S]*\/ scale/);
  assert.match(frame, /options\["width"\][\s\S]*\/ scale/);

  const offline = ios.split('func downloadOfflineRegionForRect(command:')[1]
    .split('private func startOfflineDownload')[0];
  for (const name of ['x', 'y', 'width', 'height']) {
    assert.match(offline, new RegExp('let ' + name + ' = native' +
      name[0].toUpperCase() + name.slice(1) + ' / scale'));
  }
});

test('iOS matches Android option coercion and idempotent path visibility', () => {
  const pathStart = ios.split('func startPathTracking(command:')[1].split('@objc(stopPathTracking:', 1)[0];
  assert.match(pathStart, /boolOption\(options\["trackCamera"\], defaultValue: true\)/);

  const markers = ios.split('func loadMarkers(command:')[1].split('@objc(removeMarker:', 1)[0];
  assert.match(markers, /marker\["Id"\]\.flatMap\(self\.stringOption\)/);
  assert.match(markers, /marker\["Latitude"\]/);
  assert.match(markers, /marker\["Longitude"\]/);
  assert.match(markers, /marker\["IsFind"\]/);
  assert.match(iosMarkerIcons, /cg\.setLineCap\(\.round\)/);
  assert.match(iosMarkerIcons, /cg\.strokeEllipse\(in:/);
  assert.match(iosMarkerIcons, /format\.scale = 1/);
  assert.match(ios, /private func stringOption/);

  const visibility = ios.split('func setPathVisibility(command:')[1].split('@objc(downloadOfflineRegion:', 1)[0];
  assert.doesNotMatch(visibility, /No path is loaded/);
  assert.match(visibility, /self\.isPathVisible = visible/);
  assert.match(visibility, /self\.sendSuccess\(command\)/);
});

test('markers accept imageUrl and pinColor on both platforms and reject disallowed images', () => {
  const androidAdd = javaMethod('addMarker', 'loadMarkers');
  const androidLoad = android.split('private void loadMarkers(')[1].split('private boolean addMarkerInternal(')[0];
  for (const body of [androidAdd, androidLoad]) {
    assert.match(body, /markerStyle\(/);
    assert.match(body, /MARKER_IMAGE_NOT_ALLOWED/);
  }
  assert.match(androidAdd, /optString\("imageUrl"/);
  assert.match(androidAdd, /optString\("pinColor"/);
  assert.match(androidLoad, /optString\("ImageUrl"/);
  assert.match(androidLoad, /optString\("PinColor"/);
  assert.match(android, /markerImageAllowed\(imageUrl, allowedMarkerImageHosts\(\)\)/);

  const iosAdd = ios.split('func addMarker(command:')[1].split('@objc(loadMarkers:', 1)[0];
  const iosLoad = ios.split('func loadMarkers(command:')[1].split('@objc(removeMarker:', 1)[0];
  for (const body of [iosAdd, iosLoad]) {
    assert.match(body, /markerStyle\(isFind:/);
    assert.match(body, /markerImageNotAllowed/);
  }
  assert.match(iosAdd, /options\["imageUrl"\]/);
  assert.match(iosAdd, /options\["pinColor"\]/);
  assert.match(iosLoad, /marker\["ImageUrl"\]/);
  assert.match(iosLoad, /marker\["PinColor"\]/);
  assert.match(ios, /marker\.iconSize = 0\.5/);
  assert.match(android, /\.withIconSize\(0\.5\)/);
  assert.match(ios, /MAPBOX_ALLOWED_MARKER_IMAGE_HOSTS/);
});

test('location accuracy subscriptions actively monitor and clean up on both platforms', () => {
  const androidRegistration = javaMethod('registerLocationAccuracyCallback', 'stopLocationAccuracyMonitoring');
  assert.match(androidRegistration, /requestLocationUpdates/);
  assert.match(androidRegistration, /sendLocationAccuracyUpdate/);
  assert.match(androidPermissions, /"registerLocationAccuracyCallback"\.equals\(action\)/);
  assert.match(android, /stopLocationAccuracyMonitoring\(\);[\s\S]*locationAccuracyCallback = null/);

  const iosParity = fs.readFileSync('src/ios/MapboxPluginParity.swift', 'utf8');
  const iosRegistration = iosParity.split('func registerLocationAccuracyCallback(command:')[1]
    .split('@objc(getCurrentLocationAccuracy:', 1)[0];
  assert.match(iosRegistration, /startUpdatingLocation\(\)/);
  assert.match(iosParity, /stopAccuracyMonitoring\(\)/);
});
test('pinStyle "photo" is still accepted but every marker uses the Find/Waypoint design', () => {
  const androidIcons = fs.readFileSync('src/android/MarkerIcons.java', 'utf8');
  // Older screens may still send pinStyle; it must keep parsing on both platforms.
  assert.match(android, /optString\("pinStyle"/);
  assert.match(android, /optString\("PinStyle"/);
  assert.match(ios, /pinStyle: options\["pinStyle"\]/);
  assert.match(ios, /pinStyle: marker\["PinStyle"\]/);
  // ...but it no longer selects the retired teardrop pin.
  for (const source of [android, ios, androidIcons, iosMarkerIcons]) {
    assert.doesNotMatch(source, /photoPin|drawPhoto|PHOTO_PIN_COLOR|photoPinColor/);
  }
  assert.match(android, /\.withIconImage\(style\.isPublic\s*\?\s*markerIcons\.pin\(style\.color, style\.isFind, style\.imageUrl,/);
  assert.match(ios, /marker\.image = style\.isPublic\s*\?\s*markerIcons\.pin\(color: style\.color, isFind: style\.isFind, source: style\.imageUrl\)/);
  // Square Find and circular Waypoint shapes on both platforms.
  assert.match(androidIcons, /if \(isFind\) \{[\s\S]*?addRoundRect[\s\S]*?addCircle/);
  assert.match(iosMarkerIcons, /if isFind \{\s*return UIBezierPath\(roundedRect:[\s\S]*?UIBezierPath\(ovalIn:/);
  assert.match(android, /MarkerStyle\(int color, boolean isFind, String imageUrl\)\s*\{\s*this\(color, isFind, imageUrl, false\)/);
  assert.match(ios, /var photo = false/);
});

test('isOnline sync flag is accepted by addMarker and loadMarkers and returned in tap events on both platforms', () => {
  assert.match(android, /optBoolean\("isOnline", true\)/);
  assert.match(android, /optBoolean\("IsOnline", true\)/);
  assert.match(ios, /isOnline: options\["isOnline"\] as\? Bool \?\? true/);
  assert.match(ios, /isOnline: marker\["IsOnline"\] as\? Bool \?\? true/);
  assert.match(ios, /var isOnline = true/);
  assert.equal((android.match(/payload\.put\("isOnline"/g) || []).length, 2);
  assert.equal((ios.match(/"isOnline": /g) || []).length, 3);
});

test('iOS implements boundary drawing actions and live acreage updates', () => {
  for (const action of [
    'startBoundaryDrawing',
    'undoBoundaryPoint',
    'saveBoundaryDrawing',
    'cancelBoundaryDrawing',
    'registerBoundaryAreaCallback'
  ]) {
    assert.match(ios, new RegExp('@objc\\(' + action + ':\\)'));
  }

  assert.match(ios, /if self\.isBoundaryDrawing[\s\S]*?addBoundaryDrawingPoint\(context\.coordinate\)/);
  assert.match(ios, /if isBoundaryDrawing[\s\S]*?addBoundaryDrawingPoint\(coordinate\)/);
  assert.match(ios, /"areaAcres": calculateBoundaryAreaAcres\(boundaryDrawingPoints\)/);
  assert.match(ios, /"pointCount": boundaryDrawingPoints\.count/);
  assert.match(ios, /"areaAcres": self\.calculateBoundaryAreaAcres\(self\.boundaryDrawingPoints\)/);
  assert.match(ios, /completedBoundaryDrawingPolygons\.append\(polygon\)/);
});

test('path line style is validated the same way on both platforms', () => {
  const androidStart = android.split('private void startPathTracking(')[1].split('private void stopPathTracking(')[0];
  const androidLoad = android.split('private void loadPath(')[1].split('private void clearPaths(')[0];
  for (const body of [androidStart, androidLoad]) {
    assert.match(body, /MarkerIcons\.parseColor\(options\.optString\("lineColor"/);
    assert.match(body, /readLineWidth\(options\)/);
    assert.match(body, /readLineOpacity\(options\)/);
  }
  const iosStart = ios.split('func startPathTracking(command:')[1].split('@objc(stopPathTracking:', 1)[0];
  const iosLoad = ios.split('func loadPath(command:')[1].split('@objc(clearPaths:', 1)[0];
  for (const body of [iosStart, iosLoad]) {
    assert.match(body, /lineWidthOption\(options\["lineWidth"\]\)/);
    assert.match(body, /lineOpacityOption\(options\["lineOpacity"\]\)/);
  }
});

test('IsPublic false draws a lock tile on both platforms and never loads the image', () => {
  const androidIcons = fs.readFileSync('src/android/MarkerIcons.java', 'utf8');
  assert.match(android, /optBoolean\("isPublic", true\)/);
  assert.match(android, /optBoolean\("IsPublic", true\)/);
  assert.match(ios, /isPublic: options\["isPublic"\] as\? Bool \?\? true/);
  assert.match(ios, /isPublic: marker\["IsPublic"\] as\? Bool \?\? true/);
  // Private styles carry no image URL, so nothing is validated or downloaded.
  assert.match(android, /if \(!isPublic\) \{[\s\S]*?new MarkerStyle\(color, isFind, null, photo, isOnline, false\)/);
  assert.match(ios, /if !isPublic \{[\s\S]*?imageUrl: nil, photo: photo, isOnline: isOnline, isPublic: false\)/);
  assert.match(android, /style\.isPublic\s*\?\s*markerIcons\.pin\([\s\S]*?:\s*markerIcons\.lockedPin\(style\.color, style\.isFind,/);
  assert.match(ios, /style\.isPublic\s*\?\s*markerIcons\.pin\([\s\S]*?:\s*markerIcons\.lockedPin\(color: style\.color, isFind: style\.isFind\)/);
  assert.match(androidIcons, /Bitmap lockedPin\(int color, boolean isFind, float density\)/);
  assert.match(iosMarkerIcons, /func lockedPin\(color: UIColor, isFind: Bool\)/);
  for (const source of [androidIcons, iosMarkerIcons]) assert.match(source, /drawLockTile/);
});
