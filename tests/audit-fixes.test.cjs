const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const read = (path) => fs.readFileSync(path, 'utf8');
const android = read('src/android/MapboxPluginEntry.java');
const androidIcons = read('src/android/MarkerIcons.java');
const gradle = read('src/android/mapbox-plugin.gradle');
const ios = read('src/ios/MapboxPlugin.swift');
const iosParity = read('src/ios/MapboxPluginParity.swift');
const plugin = read('plugin.xml');

function between(source, start, end) {
  const from = source.indexOf(start);
  assert.ok(from >= 0, 'Missing ' + start);
  const to = source.indexOf(end, from + start.length);
  assert.ok(to >= 0, 'Missing ' + end);
  return source.slice(from, to);
}

test('non-finite numeric strings never reach UIKit or the map camera', () => {
  // "NaN" in a frame raised CALayerInvalidGeometry and terminated the iOS app.
  const iosDouble = between(ios, 'private func doubleOption(', 'private func boolOption(');
  assert.match(iosDouble, /number\.isFinite/);
  const parityDouble = between(iosParity, 'private func doubleValue(', 'private func sendParitySuccess(');
  assert.match(parityDouble, /number\.isFinite/);
  const frame = between(ios, 'private func frameFromOptions', 'private func touchRectFromOptions');
  assert.match(frame, /16384 \/ scale/);

  assert.match(android, /private static double finiteOption\(JSONObject options, String key, double fallback\)/);
  for (const name of ['setCamera', 'flyTo']) {
    const body = between(android, 'private void ' + name + '(', 'callback.success();');
    for (const key of ['zoom', 'bearing', 'pitch']) {
      assert.match(body, new RegExp('finiteOption\\(options, "' + key + '"'));
    }
  }
  assert.match(android, /Math\.min\(options\.optLong\("duration", 2000L\), MAX_ANIMATION_MS\)/);
  const resize = between(android, 'private void resizeMap(', 'private void setTouchableRects(');
  assert.match(resize, /clamp\(options\.optInt\("width", 1\), 1, screenWidth\)/);
});

test('Android marker images check every redirect hop and cap decoded size', () => {
  const download = between(androidIcons, 'private byte[] read(', 'private static Bitmap decode(');
  assert.match(download, /setInstanceFollowRedirects\(false\)/);
  assert.ok(download.indexOf('markerImageAllowed(location, hosts)') < download.indexOf('openConnection()'),
    'The allowlist must be checked before connecting');
  assert.match(download, /hop <= MAX_REDIRECTS/);
  const decode = between(androidIcons, 'private static Bitmap decode(', 'private static float validDensity(');
  assert.match(decode, /outWidth > MAX_IMAGE_SIDE/);
  assert.match(decode, /\(long\) bounds\.outWidth \* bounds\.outHeight > MAX_IMAGE_PIXELS/);
  assert.match(androidIcons, /protected int sizeOf\(String key, Bitmap value\)/);
});

test('the plugin does not force R8 or unchecked ABI and version values on the host app', () => {
  assert.doesNotMatch(gradle, /minifyEnabled\s+true/);
  assert.doesNotMatch(gradle, /shrinkResources\s+true/);
  assert.match(gradle, /cdvHelpers\.getConfigPreference/);
  assert.match(gradle, /MAPBOX_ANDROID_VERSION must be an exact version/);
  assert.match(gradle, /MAPBOX_ANDROID_ABIS contains unknown ABIs/);
  assert.match(gradle, /includeGroupByRegex "com\\\\\.mapbox\.\*"/);
  assert.match(plugin, /<preference name="MAPBOX_ANDROID_ABIS" value="\$MAPBOX_ANDROID_ABIS" \/>/);
  assert.match(plugin, /<preference name="MAPBOX_ANDROID_VERSION" value="\$MAPBOX_ANDROID_VERSION" \/>/);
  assert.match(plugin, /<string>\$LOCATION_USAGE_DESCRIPTION<\/string>/);
});

test('legacy credential caches are cleared when the plugin loads', () => {
  const androidInit = between(android, 'protected void pluginInitialize()', 'private void clearLegacyCredentialCache()');
  assert.match(androidInit, /clearLegacyCredentialCache\(\)/);
  assert.match(android, /deleteSharedPreferences\(LEGACY_CREDENTIAL_PREFS\)/);
  const iosInit = between(ios, 'override func pluginInitialize()', 'private func clearLegacyTokenCache()');
  assert.match(iosInit, /clearLegacyTokenCache\(\)/);
  assert.doesNotMatch(between(ios, 'private func getAccessToken()', 'private func styleAllowed('), /SecItemDelete/);
});

test('iOS recenter skips stale fixes, times out, and settles superseded requests', () => {
  const start = between(iosParity, 'override func moveToCurrentLocation(', '@objc(setLayerVisibility:)');
  assert.match(start, /Location request superseded\./);
  assert.ok(start.indexOf('superseded') < start.indexOf('self.moveLocationZoom ='),
    'Settle the old request before replacing its state');
  assert.match(start, /asyncAfter\(deadline: \.now\(\) \+ self\.moveLocationTimeout/);
  const handle = between(iosParity, 'private func handleMoveToCurrentLocation(', 'private func sendLocationAccuracyUpdate(');
  assert.match(handle, /location\.horizontalAccuracy >= 0/);
  assert.match(handle, /timeIntervalSinceNow\) <= maxLocationAge/);
  assert.match(between(iosParity, 'private func stopMoveLocationUpdates()', 'private func activeMapView()'),
    /moveLocationTimeoutWork\?\.cancel\(\)/);
});

test('offline deletes keep shared style packs unless asked', () => {
  assert.match(android, /optBoolean\("deleteStylePack", false\)/);
  assert.match(ios, /options\["deleteStylePack"\] as\? Bool \?\? false/);
});

test('path visibility redraws stored geometry and clear resets segments on both platforms', () => {
  const androidVisibility = between(android, 'private void setPathVisibility(', 'private void sendKeepCallback(');
  assert.match(androidVisibility, /redrawPathAnnotations\(\)/);
  const iosVisibility = between(ios, 'func setPathVisibility(command:', '@objc(downloadOfflineRegion:');
  assert.match(iosVisibility, /self\.redrawPathAnnotations\(\)/);
  for (const [source, start, end] of [
    [android, 'private void clearPaths(', 'private void setPathVisibility('],
    [ios, 'func clearPaths(command:', '@objc(setPathVisibility:'],
    [android, 'private void loadPath(', 'private void clearPaths('],
    [ios, 'func loadPath(command:', '@objc(clearPaths:']
  ]) {
    assert.match(between(source, start, end), /pathSegments\.(clear|removeAll)\(\)/);
  }
});

test('Android creates and clears markers and boundaries in batches', () => {
  const load = between(android, 'private void loadMarkers(', 'private boolean addMarkerInternal(');
  assert.match(load, /pointAnnotationManager\.create\(batchOptions\)/);
  assert.doesNotMatch(load, /addMarkerInternal\(/);
  assert.match(between(android, 'private void clearMarkersInternal()', 'private void loadBoundaries('),
    /pointAnnotationManager\.deleteAll\(\)/);
  assert.match(android, /boundaryAnnotationManager\.create\(boundaryAnnotationOptions\)/);
  assert.match(android, /boundaryAnnotationManager\.delete\(new ArrayList<>\(boundaryAnnotations\)\)/);
});
