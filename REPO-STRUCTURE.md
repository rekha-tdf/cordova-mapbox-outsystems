# Required Repository Structure

The Git repository root must be the Cordova plugin root.

Correct:

```text
cordova-mapbox-outsystems/
  plugin.xml
  package.json
  www/
    Mapbox.js
  src/
    android/
      MapboxPlugin.java
      mapbox.gradle
    ios/
      CDVMapboxPlugin.swift
```

Incorrect:

```text
cordova-mapbox-outsystems/
  Mapbox-o11-modern/
    plugin.xml
    src/
    www/
```

MABS/plugman resolves paths in `plugin.xml` from the plugin root. If `plugin.xml` references `src/android/MapboxPlugin.java`, that file must exist at:

```text
<repo-root>/src/android/MapboxPlugin.java
```
