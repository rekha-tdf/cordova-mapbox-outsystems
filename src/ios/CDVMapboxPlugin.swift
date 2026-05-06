import Foundation
import Cordova
import MapboxMaps
import CoreLocation

@objc(CDVMapboxPlugin)
final class CDVMapboxPlugin: CDVPlugin {
    private var mapView: MapView?
    private var regionDidChangeCallbackId: String?

    @objc(show:)
    func show(command: CDVInvokedUrlCommand) {
        guard let args = command.arguments.first as? [String: Any] else {
            fail(command, "Missing options.")
            return
        }

        guard let token = Bundle.main.object(forInfoDictionaryKey: "MBXAccessToken") as? String, !token.isEmpty else {
            fail(command, "ACCESS_TOKEN is missing.")
            return
        }

        MapboxOptions.accessToken = token

        DispatchQueue.main.async {
            let margins = args["margins"] as? [String: Any]
            let left = CGFloat((margins?["left"] as? NSNumber)?.doubleValue ?? 0)
            let top = CGFloat((margins?["top"] as? NSNumber)?.doubleValue ?? 0)
            let right = CGFloat((margins?["right"] as? NSNumber)?.doubleValue ?? 0)
            let bottom = CGFloat((margins?["bottom"] as? NSNumber)?.doubleValue ?? 0)

            let frame = self.webView.bounds.inset(by: UIEdgeInsets(top: top, left: left, bottom: bottom, right: right))
            let center = args["center"] as? [String: Any]
            let lat = (center?["lat"] as? NSNumber)?.doubleValue ?? 0
            let lng = (center?["lng"] as? NSNumber)?.doubleValue ?? 0
            let zoom = (args["zoomLevel"] as? NSNumber)?.doubleValue ?? (center == nil ? 0 : 10)

            let camera = CameraOptions(center: CLLocationCoordinate2D(latitude: lat, longitude: lng), zoom: zoom)
            let initOptions = MapInitOptions(cameraOptions: camera)
            let view = MapView(frame: frame, mapInitOptions: initOptions)
            view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            view.mapboxMap.loadStyle(self.resolveStyle(args["style"] as? String))
            self.webView.addSubview(view)
            self.mapView = view
            self.ok(command)
        }
    }

    @objc(hide:)
    func hide(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            self.mapView?.removeFromSuperview()
            self.mapView = nil
            self.ok(command)
        }
    }

    @objc(setCenter:)
    func setCenter(command: CDVInvokedUrlCommand) {
        guard let args = command.arguments.first as? [String: Any], let mapView = mapView else {
            fail(command, "Mapbox.show must be called first.")
            return
        }
        let lat = (args["lat"] as? NSNumber)?.doubleValue ?? 0
        let lng = (args["lng"] as? NSNumber)?.doubleValue ?? 0
        mapView.mapboxMap.setCamera(to: CameraOptions(center: CLLocationCoordinate2D(latitude: lat, longitude: lng)))
        sendRegionDidChange()
        ok(command)
    }

    @objc(getCenter:)
    func getCenter(command: CDVInvokedUrlCommand) {
        guard let mapView = mapView else {
            fail(command, "Mapbox.show must be called first.")
            return
        }
        let center = mapView.mapboxMap.cameraState.center
        ok(command, ["lat": center.latitude, "lng": center.longitude])
    }

    @objc(setZoomLevel:)
    func setZoomLevel(command: CDVInvokedUrlCommand) {
        guard let args = command.arguments.first as? [String: Any], let mapView = mapView else {
            fail(command, "Mapbox.show must be called first.")
            return
        }
        mapView.mapboxMap.setCamera(to: CameraOptions(zoom: (args["level"] as? NSNumber)?.doubleValue ?? 0))
        sendRegionDidChange()
        ok(command)
    }

    @objc(getZoomLevel:)
    func getZoomLevel(command: CDVInvokedUrlCommand) {
        guard let mapView = mapView else {
            fail(command, "Mapbox.show must be called first.")
            return
        }
        commandDelegate.send(CDVPluginResult(status: CDVCommandStatus_OK, messageAs: mapView.mapboxMap.cameraState.zoom), callbackId: command.callbackId)
    }

    @objc(setTilt:)
    func setTilt(command: CDVInvokedUrlCommand) {
        guard let args = command.arguments.first as? [String: Any], let mapView = mapView else {
            fail(command, "Mapbox.show must be called first.")
            return
        }
        mapView.mapboxMap.setCamera(to: CameraOptions(pitch: (args["pitch"] as? NSNumber)?.doubleValue ?? 0))
        sendRegionDidChange()
        ok(command)
    }

    @objc(getTilt:)
    func getTilt(command: CDVInvokedUrlCommand) {
        guard let mapView = mapView else {
            fail(command, "Mapbox.show must be called first.")
            return
        }
        commandDelegate.send(CDVPluginResult(status: CDVCommandStatus_OK, messageAs: mapView.mapboxMap.cameraState.pitch), callbackId: command.callbackId)
    }

    @objc(animateCamera:)
    func animateCamera(command: CDVInvokedUrlCommand) {
        guard let args = command.arguments.first as? [String: Any], let mapView = mapView else {
            fail(command, "Mapbox.show must be called first.")
            return
        }
        let target = args["target"] as? [String: Any]
        let lat = (target?["lat"] as? NSNumber)?.doubleValue
        let lng = (target?["lng"] as? NSNumber)?.doubleValue
        let center = lat != nil && lng != nil ? CLLocationCoordinate2D(latitude: lat!, longitude: lng!) : nil
        let camera = CameraOptions(
            center: center,
            zoom: (args["zoomLevel"] as? NSNumber)?.doubleValue,
            bearing: (args["bearing"] as? NSNumber)?.doubleValue,
            pitch: (args["tilt"] as? NSNumber)?.doubleValue
        )
        mapView.camera.fly(to: camera, duration: (args["duration"] as? NSNumber)?.doubleValue ?? 1.0)
        sendRegionDidChange()
        ok(command)
    }

    @objc(getBounds:)
    func getBounds(command: CDVInvokedUrlCommand) {
        guard let mapView = mapView else {
            fail(command, "Mapbox.show must be called first.")
            return
        }
        let bounds = mapView.mapboxMap.coordinateBounds(for: mapView.bounds)
        ok(command, [
            "sw_lat": bounds.southwest.latitude,
            "sw_lng": bounds.southwest.longitude,
            "ne_lat": bounds.northeast.latitude,
            "ne_lng": bounds.northeast.longitude
        ])
    }

    @objc(convertCoordinate:)
    func convertCoordinate(command: CDVInvokedUrlCommand) {
        guard let args = command.arguments.first as? [String: Any], let mapView = mapView else {
            fail(command, "Mapbox.show must be called first.")
            return
        }
        let lat = (args["lat"] as? NSNumber)?.doubleValue ?? 0
        let lng = (args["lng"] as? NSNumber)?.doubleValue ?? 0
        let point = mapView.mapboxMap.point(for: CLLocationCoordinate2D(latitude: lat, longitude: lng))
        ok(command, ["x": point.x, "y": point.y])
    }

    @objc(convertPoint:)
    func convertPoint(command: CDVInvokedUrlCommand) {
        guard let args = command.arguments.first as? [String: Any], let mapView = mapView else {
            fail(command, "Mapbox.show must be called first.")
            return
        }
        let x = CGFloat((args["x"] as? NSNumber)?.doubleValue ?? 0)
        let y = CGFloat((args["y"] as? NSNumber)?.doubleValue ?? 0)
        let coordinate = mapView.mapboxMap.coordinate(for: CGPoint(x: x, y: y))
        ok(command, ["lat": coordinate.latitude, "lng": coordinate.longitude])
    }

    @objc(onRegionDidChange:)
    func onRegionDidChange(command: CDVInvokedUrlCommand) {
        regionDidChangeCallbackId = command.callbackId
        let result = CDVPluginResult(status: CDVCommandStatus_NO_RESULT)
        result?.setKeepCallbackAs(true)
        commandDelegate.send(result, callbackId: command.callbackId)
    }

    @objc(addMarkers:)
    func addMarkers(command: CDVInvokedUrlCommand) { notImplemented(command, "addMarkers") }
    @objc(removeAllMarkers:)
    func removeAllMarkers(command: CDVInvokedUrlCommand) { notImplemented(command, "removeAllMarkers") }
    @objc(addMarkerCallback:)
    func addMarkerCallback(command: CDVInvokedUrlCommand) { notImplemented(command, "addMarkerCallback") }
    @objc(addPolygon:)
    func addPolygon(command: CDVInvokedUrlCommand) { notImplemented(command, "addPolygon") }
    @objc(addGeoJSON:)
    func addGeoJSON(command: CDVInvokedUrlCommand) { notImplemented(command, "addGeoJSON") }
    @objc(setBounds:)
    func setBounds(command: CDVInvokedUrlCommand) { notImplemented(command, "setBounds") }
    @objc(onRegionWillChange:)
    func onRegionWillChange(command: CDVInvokedUrlCommand) { notImplemented(command, "onRegionWillChange") }
    @objc(onRegionIsChanging:)
    func onRegionIsChanging(command: CDVInvokedUrlCommand) { notImplemented(command, "onRegionIsChanging") }

    private func resolveStyle(_ input: String?) -> StyleURI {
        switch input?.lowercased() {
        case "light": return .light
        case "dark": return .dark
        case "satellite": return .satellite
        case "hybrid": return .satelliteStreets
        case "streets", nil: return .streets
        default: return StyleURI(rawValue: input!) ?? .streets
        }
    }

    private func sendRegionDidChange() {
        guard let callbackId = regionDidChangeCallbackId, let mapView = mapView else { return }
        let state = mapView.mapboxMap.cameraState
        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: [
            "lat": state.center.latitude,
            "lng": state.center.longitude,
            "camPitch": state.pitch,
            "camHeading": state.bearing
        ])
        result?.setKeepCallbackAs(true)
        commandDelegate.send(result, callbackId: callbackId)
    }

    private func ok(_ command: CDVInvokedUrlCommand, _ dictionary: [String: Any]? = nil) {
        let result = dictionary == nil
            ? CDVPluginResult(status: CDVCommandStatus_OK)
            : CDVPluginResult(status: CDVCommandStatus_OK, messageAs: dictionary)
        commandDelegate.send(result, callbackId: command.callbackId)
    }

    private func fail(_ command: CDVInvokedUrlCommand, _ message: String) {
        commandDelegate.send(CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: message), callbackId: command.callbackId)
    }

    private func notImplemented(_ command: CDVInvokedUrlCommand, _ action: String) {
        fail(command, "\(action) is not implemented in this O11 starter. Implement with Mapbox v11 Annotation/Style APIs before production use.")
    }
}
