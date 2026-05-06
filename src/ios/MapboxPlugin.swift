import Foundation
import Cordova
import MapboxMaps

@objc(MapboxPlugin)
class MapboxPlugin: CDVPlugin {
    private var mapView: MapView?
    private var annotations: PointAnnotationManager?
    private var markers: [String: PointAnnotation] = [:]

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
            self.makeWebViewTransparent()

            if behindWebView, let superview = self.webView.superview {
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

    @objc(setLayerMode:)
    func setLayerMode(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            guard let mapView = self.mapView else {
                self.sendError("Map is not initialized.", command)
                return
            }

            let options = command.argument(at: 0) as? [String: Any] ?? [:]
            let mode = options["mode"] as? String ?? "behind"

            if mode.lowercased() == "front" {
                self.webView.superview?.bringSubviewToFront(mapView)
            } else if let superview = self.webView.superview {
                self.makeWebViewTransparent()
                superview.insertSubview(mapView, belowSubview: self.webView)
            }

            self.sendSuccess(command)
        }
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
