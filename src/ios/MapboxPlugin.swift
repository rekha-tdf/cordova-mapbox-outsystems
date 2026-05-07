import Foundation
import Cordova
import CoreLocation
import MapboxMaps

@objc(MapboxPlugin)
class MapboxPlugin: CDVPlugin, CLLocationManagerDelegate {
    private var mapView: MapView?
    private var annotations: PointAnnotationManager?
    private var markers: [String: PointAnnotation] = [:]
    private var headingLocationManager: CLLocationManager?
    private var lastHeadingBearing: CLLocationDirection = -1
    private var lastHeadingUpdate: TimeInterval = 0
    private var isUserTrackingEnabled = false
    private var lastUserTrackingUpdate: TimeInterval = 0

    @objc(ping:)
    func ping(command: CDVInvokedUrlCommand) {
        sendSuccess([
            "status": "ok",
            "service": "MapboxPlugin",
            "class": "MapboxPlugin"
        ], command)
    }

    @objc(initialize:)
    func initialize(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            guard let options = command.argument(at: 0) as? [String: Any] else {
                self.sendError("Options are required.", command)
                return
            }

            let runtimeToken = options["token"] as? String ?? ""
            let fallbackToken = self.preferenceValue("MAPBOX_ACCESS_TOKEN")
            let token = runtimeToken.isEmpty ? fallbackToken : runtimeToken

            guard !token.isEmpty else {
                self.sendError("Mapbox access token is required.", command)
                return
            }

            let latitude = options["latitude"] as? Double ?? 0
            let longitude = options["longitude"] as? Double ?? 0
            let zoom = options["zoom"] as? Double ?? 12
            let styleUrl = options["styleUrl"] as? String
            let behindWebView = options["behindWebView"] as? Bool ?? false

            self.closeInternal()

            MapboxOptions.accessToken = token

            let camera = CameraOptions(
                center: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
                zoom: zoom
            )

            let styleURI = styleUrl.flatMap { StyleURI(rawValue: $0) } ?? .streets
            let initOptions = MapInitOptions(cameraOptions: camera, styleURI: styleURI)

            let isInline = options["inline"] as? Bool ?? false
            let mapView = MapView(
                frame: isInline ? self.frameFromOptions(options) : self.webView.bounds,
                mapInitOptions: initOptions
            )
            mapView.autoresizingMask = isInline ? [] : [.flexibleWidth, .flexibleHeight]

            if behindWebView, let superview = self.webView.superview {
                self.makeWebViewTransparent()
                superview.insertSubview(mapView, belowSubview: self.webView)
            } else {
                self.webView.superview?.addSubview(mapView)
            }

            self.mapView = mapView
            self.annotations = mapView.annotations.makePointAnnotationManager()

            if !isInline {
                let closeButton = UIButton(type: .system)
                closeButton.setTitle("Close", for: .normal)
                closeButton.backgroundColor = UIColor.white
                closeButton.layer.cornerRadius = 6
                closeButton.contentEdgeInsets = UIEdgeInsets(top: 8, left: 14, bottom: 8, right: 14)
                closeButton.addTarget(self, action: #selector(self.closeFromButton), for: .touchUpInside)
                closeButton.translatesAutoresizingMaskIntoConstraints = false
                mapView.addSubview(closeButton)

                NSLayoutConstraint.activate([
                    closeButton.topAnchor.constraint(equalTo: mapView.safeAreaLayoutGuide.topAnchor, constant: 12),
                    closeButton.trailingAnchor.constraint(equalTo: mapView.trailingAnchor, constant: -16)
                ])
            }

            self.sendSuccess(["status": "initialized"], command)
        }
    }

    @objc(setCamera:)
    func setCamera(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            guard let mapView = self.mapView else {
                self.sendError("Map is not initialized.", command)
                return
            }

            let options = command.argument(at: 0) as? [String: Any] ?? [:]
            let latitude = options["latitude"] as? Double ?? mapView.cameraState.center.latitude
            let longitude = options["longitude"] as? Double ?? mapView.cameraState.center.longitude
            let zoom = options["zoom"] as? Double ?? mapView.cameraState.zoom
            let bearing = options["bearing"] as? Double ?? mapView.cameraState.bearing
            let pitch = options["pitch"] as? Double ?? mapView.cameraState.pitch

            mapView.mapboxMap.setCamera(to: CameraOptions(
                center: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
                zoom: zoom,
                bearing: bearing,
                pitch: pitch
            ))

            self.sendSuccess(command)
        }
    }

    @objc(setViewport:)
    func setViewport(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            guard let mapView = self.mapView else {
                self.sendError("Map is not initialized.", command)
                return
            }

            let options = command.argument(at: 0) as? [String: Any] ?? [:]
            mapView.frame = self.frameFromOptions(options)
            self.sendSuccess(command)
        }
    }

    @objc(setTouchableRects:)
    func setTouchableRects(command: CDVInvokedUrlCommand) {
        sendSuccess(command)
    }

    @objc(enableUserLocation:)
    func enableUserLocation(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            guard let mapView = self.mapView else {
                self.sendError("Map is not initialized.", command)
                return
            }

            mapView.location.options.puckType = .puck2D()
            mapView.location.options.puckBearingEnabled = true
            self.sendSuccess(command)
        }
    }

    @objc(setDeviceHeadingEnabled:)
    func setDeviceHeadingEnabled(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            guard let mapView = self.mapView else {
                self.sendError("Map is not initialized.", command)
                return
            }

            let options = command.argument(at: 0) as? [String: Any] ?? [:]
            let enabled = options["enabled"] as? Bool ?? true

            if enabled {
                mapView.location.options.puckType = .puck2D(.makeDefault(showBearing: true))
                mapView.location.options.puckBearing = .heading
            }

            mapView.location.options.puckBearingEnabled = enabled
            self.sendSuccess(command)
        }
    }

    @objc(setHeadingFollowMode:)
    func setHeadingFollowMode(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            guard let mapView = self.mapView else {
                self.sendError("Map is not initialized.", command)
                return
            }

            let options = command.argument(at: 0) as? [String: Any] ?? [:]
            let enabled = options["enabled"] as? Bool ?? true

            if !enabled {
                self.stopHeadingFollowMode()
                self.sendSuccess(command)
                return
            }

            mapView.location.options.puckType = .puck2D(.makeDefault(showBearing: true))
            mapView.location.options.puckBearing = .heading
            mapView.location.options.puckBearingEnabled = true

            self.startHeadingFollowMode(command)
        }
    }

    @objc(setUserTrackingEnabled:)
    func setUserTrackingEnabled(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            guard self.mapView != nil else {
                self.sendError("Map is not initialized.", command)
                return
            }

            let options = command.argument(at: 0) as? [String: Any] ?? [:]
            let enabled = options["enabled"] as? Bool ?? true

            if !enabled {
                self.stopUserTracking()
                self.sendSuccess(command)
                return
            }

            self.startUserTracking(command)
        }
    }

    private func startHeadingFollowMode(_ command: CDVInvokedUrlCommand) {
        guard CLLocationManager.headingAvailable() else {
            sendError("Device heading sensor is not available.", command)
            return
        }

        if headingLocationManager == nil {
            let manager = CLLocationManager()
            manager.delegate = self
            manager.headingFilter = 1
            headingLocationManager = manager
        }

        if CLLocationManager.authorizationStatus() == .notDetermined {
            headingLocationManager?.requestWhenInUseAuthorization()
        }

        headingLocationManager?.startUpdatingHeading()
        sendSuccess(command)
    }

    private func startUserTracking(_ command: CDVInvokedUrlCommand) {
        if headingLocationManager == nil {
            let manager = CLLocationManager()
            manager.delegate = self
            manager.headingFilter = 1
            manager.desiredAccuracy = kCLLocationAccuracyBest
            headingLocationManager = manager
        }

        if CLLocationManager.authorizationStatus() == .notDetermined {
            headingLocationManager?.requestWhenInUseAuthorization()
        }

        isUserTrackingEnabled = true
        headingLocationManager?.startUpdatingLocation()
        sendSuccess(command)
    }

    private func stopUserTracking() {
        isUserTrackingEnabled = false
        headingLocationManager?.stopUpdatingLocation()
        lastUserTrackingUpdate = 0
    }

    private func stopHeadingFollowMode() {
        headingLocationManager?.stopUpdatingHeading()
        lastHeadingBearing = -1
        lastHeadingUpdate = 0
    }

    func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        var bearing = newHeading.trueHeading >= 0 ? newHeading.trueHeading : newHeading.magneticHeading
        guard bearing >= 0 else {
            return
        }

        let now = Date().timeIntervalSince1970
        if now - lastHeadingUpdate < 0.08 {
            return
        }

        if lastHeadingBearing >= 0 {
            var diff = abs(bearing - lastHeadingBearing)
            diff = min(diff, 360.0 - diff)

            if diff < 1.5 {
                return
            }

            bearing = lastHeadingBearing + shortestBearingDelta(from: lastHeadingBearing, to: bearing) * 0.25
            if bearing < 0 {
                bearing += 360.0
            } else if bearing >= 360.0 {
                bearing -= 360.0
            }
        }

        lastHeadingBearing = bearing
        lastHeadingUpdate = now

        DispatchQueue.main.async {
            self.mapView?.mapboxMap.setCamera(to: CameraOptions(bearing: bearing))
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard isUserTrackingEnabled, let location = locations.last else {
            return
        }

        let now = Date().timeIntervalSince1970
        if now - lastUserTrackingUpdate < 0.5 {
            return
        }

        lastUserTrackingUpdate = now
        let coordinate = location.coordinate

        DispatchQueue.main.async {
            self.mapView?.mapboxMap.setCamera(to: CameraOptions(center: coordinate))
        }
    }

    func locationManagerShouldDisplayHeadingCalibration(_ manager: CLLocationManager) -> Bool {
        return true
    }

    private func shortestBearingDelta(from: CLLocationDirection, to: CLLocationDirection) -> CLLocationDirection {
        return (to - from + 540.0).truncatingRemainder(dividingBy: 360.0) - 180.0
    }

    @objc(addMarker:)
    func addMarker(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            guard var manager = self.annotations else {
                self.sendError("Map is not initialized.", command)
                return
            }

            let options = command.argument(at: 0) as? [String: Any] ?? [:]
            let id = (options["id"] as? String)?.isEmpty == false
                ? options["id"] as! String
                : String(Int(Date().timeIntervalSince1970 * 1000))
            let latitude = options["latitude"] as? Double ?? 0
            let longitude = options["longitude"] as? Double ?? 0

            self.markers.removeValue(forKey: id)

            var marker = PointAnnotation(
                coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
            )
            marker.userInfo = ["id": id]
            self.markers[id] = marker

            manager.annotations = Array(self.markers.values)
            self.annotations = manager
            self.sendSuccess(["id": id], command)
        }
    }

    @objc(removeMarker:)
    func removeMarker(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            guard var manager = self.annotations else {
                self.sendError("Map is not initialized.", command)
                return
            }

            let options = command.argument(at: 0) as? [String: Any] ?? [:]
            let id = options["id"] as? String ?? ""
            self.markers.removeValue(forKey: id)
            manager.annotations = Array(self.markers.values)
            self.annotations = manager
            self.sendSuccess(command)
        }
    }

    @objc(clearMarkers:)
    func clearMarkers(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            guard var manager = self.annotations else {
                self.sendError("Map is not initialized.", command)
                return
            }

            self.markers.removeAll()
            manager.annotations = []
            self.annotations = manager
            self.sendSuccess(command)
        }
    }

    @objc(getCamera:)
    func getCamera(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            guard let mapView = self.mapView else {
                self.sendError("Map is not initialized.", command)
                return
            }

            let state = mapView.cameraState
            self.sendSuccess([
                "latitude": state.center.latitude,
                "longitude": state.center.longitude,
                "zoom": state.zoom,
                "bearing": state.bearing,
                "pitch": state.pitch
            ], command)
        }
    }

    @objc(close:)
    func close(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            self.closeInternal()
            self.sendSuccess(command)
        }
    }

    private func closeInternal() {
        stopHeadingFollowMode()
        stopUserTracking()
        markers.removeAll()
        annotations = nil
        mapView?.removeFromSuperview()
        mapView = nil
    }

    @objc private func closeFromButton() {
        closeInternal()
    }

    private func preferenceValue(_ key: String) -> String {
        if let value = commandDelegate.settings[key] as? String, !value.isEmpty {
            return value == "__MAPBOX_ACCESS_TOKEN_NOT_SET__" ? "" : value
        }
        let lowerKey = key.lowercased()
        if let value = commandDelegate.settings[lowerKey] as? String, !value.isEmpty {
            return value == "__MAPBOX_ACCESS_TOKEN_NOT_SET__" ? "" : value
        }
        return ""
    }

    private func frameFromOptions(_ options: [String: Any]) -> CGRect {
        let x = options["x"] as? Double ?? 0
        let y = options["y"] as? Double ?? 0
        let width = options["width"] as? Double ?? Double(webView.bounds.width)
        let height = options["height"] as? Double ?? Double(webView.bounds.height)
        return CGRect(x: x, y: y, width: max(width, 1), height: max(height, 1))
    }

    private func makeWebViewTransparent() {
        webView.isOpaque = false
        webView.backgroundColor = UIColor.clear
        webView.scrollView.backgroundColor = UIColor.clear
    }

    private func sendSuccess(_ command: CDVInvokedUrlCommand) {
        let result = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.send(result, callbackId: command.callbackId)
    }

    private func sendSuccess(_ payload: [String: Any], _ command: CDVInvokedUrlCommand) {
        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: payload)
        commandDelegate.send(result, callbackId: command.callbackId)
    }

    private func sendError(_ message: String, _ command: CDVInvokedUrlCommand) {
        let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: message)
        commandDelegate.send(result, callbackId: command.callbackId)
    }
}
