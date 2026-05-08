import Foundation
import Cordova
import CoreLocation
import UIKit
import MapboxMaps

@objc(MapboxPlugin)
class MapboxPlugin: CDVPlugin, CLLocationManagerDelegate {
    private var mapView: MapView?
    private var annotations: PointAnnotationManager?
    private var markers: [String: PointAnnotation] = [:]
    private var waypointSelectedCallbackId: String?
    private var markerClickCallbackId: String?
    private var waypointSelectionEnabled = false
    private var autoAddWaypointMarker = false
    private var cancelables = Set<AnyCancelable>()
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
            self.installMapTapHandler(on: mapView)

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
            guard self.mapView != nil else {
                self.sendError("Map is not initialized.", command)
                return
            }

            let options = command.argument(at: 0) as? [String: Any] ?? [:]
            let id = (options["id"] as? String)?.isEmpty == false
                ? options["id"] as! String
                : String(Int(Date().timeIntervalSince1970 * 1000))
            let latitude = options["latitude"] as? Double ?? 0
            let longitude = options["longitude"] as? Double ?? 0

            self.addMarkerInternal(id: id, latitude: latitude, longitude: longitude)
            self.sendSuccess(["id": id], command)
        }
    }

    @objc(loadMarkers:)
    func loadMarkers(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            guard self.mapView != nil else {
                self.sendError("Map is not initialized.", command)
                return
            }

            let options = command.argument(at: 0) as? [String: Any] ?? [:]
            if options["replace"] as? Bool ?? true {
                self.clearMarkersInternal()
            }

            let markers = options["markers"] as? [[String: Any]] ?? []
            for (index, marker) in markers.enumerated() {
                let id = marker["id"] as? String ?? String(index)
                let latitude = marker["latitude"] as? Double ?? 0
                let longitude = marker["longitude"] as? Double ?? 0
                self.addMarkerInternal(id: id, latitude: latitude, longitude: longitude)
            }

            self.sendSuccess(command)
        }
    }

    @objc(removeMarker:)
    func removeMarker(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            let options = command.argument(at: 0) as? [String: Any] ?? [:]
            let id = options["id"] as? String ?? ""
            self.markers.removeValue(forKey: id)
            self.annotations?.annotations = Array(self.markers.values)
            self.sendSuccess(command)
        }
    }

    @objc(clearMarkers:)
    func clearMarkers(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            self.clearMarkersInternal()
            self.sendSuccess(command)
        }
    }

    @objc(setWaypointSelectionEnabled:)
    func setWaypointSelectionEnabled(command: CDVInvokedUrlCommand) {
        let options = command.argument(at: 0) as? [String: Any] ?? [:]
        waypointSelectionEnabled = options["enabled"] as? Bool ?? true
        autoAddWaypointMarker = options["autoAddMarker"] as? Bool ?? false
        sendSuccess(command)
    }

    @objc(registerWaypointSelectedCallback:)
    func registerWaypointSelectedCallback(command: CDVInvokedUrlCommand) {
        waypointSelectedCallbackId = command.callbackId
        sendNoResultKeepCallback(command)
    }

    @objc(registerMarkerClickCallback:)
    func registerMarkerClickCallback(command: CDVInvokedUrlCommand) {
        markerClickCallbackId = command.callbackId
        sendNoResultKeepCallback(command)
    }

    private func installMapTapHandler(on mapView: MapView) {
        mapView.gestures.onMapTap.observe { [weak self] context in
            guard let self = self else {
                return
            }

            if self.sendMarkerClickIfNear(context.coordinate) {
                return
            }

            guard self.waypointSelectionEnabled else {
                return
            }

            var id = ""
            if self.autoAddWaypointMarker {
                id = String(Int(Date().timeIntervalSince1970 * 1000))
                self.addMarkerInternal(
                    id: id,
                    latitude: context.coordinate.latitude,
                    longitude: context.coordinate.longitude
                )
            }

            self.sendKeepCallback(self.waypointSelectedCallbackId, payload: [
                "type": "waypointSelected",
                "id": id,
                "latitude": context.coordinate.latitude,
                "longitude": context.coordinate.longitude
            ])
        }.store(in: &cancelables)
    }

    private func addMarkerInternal(id: String, latitude: Double, longitude: Double) {
        guard var manager = annotations else {
            return
        }

        var marker = PointAnnotation(
            coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        )
        marker.image = .init(image: createWaypointMarkerImage(), name: "waypoint-marker")
        marker.iconAnchor = .bottom
        marker.tapHandler = { [weak self, id] context in
            self?.sendKeepCallback(self?.markerClickCallbackId, payload: [
                "type": "markerClicked",
                "id": id,
                "latitude": context.coordinate.latitude,
                "longitude": context.coordinate.longitude
            ])
            return true
        }

        markers[id] = marker
        manager.annotations = Array(markers.values)
        annotations = manager
    }

    private func clearMarkersInternal() {
        markers.removeAll()
        annotations?.annotations = []
    }

    private func sendMarkerClickIfNear(_ coordinate: CLLocationCoordinate2D) -> Bool {
        let tapLocation = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        var nearestId = ""
        var nearestMarker: PointAnnotation?
        var nearestDistance = CLLocationDistance.greatestFiniteMagnitude

        for (id, marker) in markers {
            let markerLocation = CLLocation(
                latitude: marker.point.coordinates.latitude,
                longitude: marker.point.coordinates.longitude
            )
            let distance = tapLocation.distance(from: markerLocation)
            if distance < nearestDistance {
                nearestDistance = distance
                nearestId = id
                nearestMarker = marker
            }
        }

        guard let marker = nearestMarker, nearestDistance <= 75 else {
            return false
        }

        sendKeepCallback(markerClickCallbackId, payload: [
            "type": "markerClicked",
            "id": nearestId,
            "latitude": marker.point.coordinates.latitude,
            "longitude": marker.point.coordinates.longitude
        ])
        return true
    }

    private func createWaypointMarkerImage() -> UIImage {
        let size = CGSize(width: 72, height: 96)
        let renderer = UIGraphicsImageRenderer(size: size)

        return renderer.image { context in
            let cg = context.cgContext
            let centerX = size.width / 2
            let circleCenterY: CGFloat = 30
            let circleRadius: CGFloat = 24

            cg.setFillColor(UIColor.black.withAlphaComponent(0.25).cgColor)
            cg.fillEllipse(in: CGRect(x: centerX - 18, y: size.height - 14, width: 36, height: 8))

            let path = UIBezierPath()
            path.addArc(
                withCenter: CGPoint(x: centerX, y: circleCenterY),
                radius: circleRadius,
                startAngle: 0,
                endAngle: CGFloat.pi * 2,
                clockwise: true
            )
            path.move(to: CGPoint(x: centerX - 15, y: circleCenterY + 18))
            path.addLine(to: CGPoint(x: centerX, y: size.height - 12))
            path.addLine(to: CGPoint(x: centerX + 15, y: circleCenterY + 18))
            path.close()

            UIColor.systemPink.setFill()
            path.fill()
            UIColor.white.setStroke()
            path.lineWidth = 4
            path.stroke()

            UIColor.white.setFill()
            UIBezierPath(
                ovalIn: CGRect(x: centerX - 9, y: circleCenterY - 9, width: 18, height: 18)
            ).fill()
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
        waypointSelectedCallbackId = nil
        markerClickCallbackId = nil
        waypointSelectionEnabled = false
        autoAddWaypointMarker = false
        cancelables.removeAll()
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

    private func sendNoResultKeepCallback(_ command: CDVInvokedUrlCommand) {
        let result = CDVPluginResult(status: CDVCommandStatus_NO_RESULT)
        result?.setKeepCallbackAs(true)
        commandDelegate.send(result, callbackId: command.callbackId)
    }

    private func sendKeepCallback(_ callbackId: String?, payload: [String: Any]) {
        guard let callbackId = callbackId else {
            return
        }

        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: payload)
        result?.setKeepCallbackAs(true)
        commandDelegate.send(result, callbackId: callbackId)
    }

    private func sendError(_ message: String, _ command: CDVInvokedUrlCommand) {
        let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: message)
        commandDelegate.send(result, callbackId: command.callbackId)
    }
}
