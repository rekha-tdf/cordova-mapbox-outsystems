import CryptoKit
import Foundation
import ImageIO
import MapboxMaps
import UIKit

/// Draws marker pins and loads the images shown inside them.
final class MarkerIcons: NSObject, URLSessionDataDelegate {
    // Find (square) and Waypoint (circle) markers share a near-black frame; pinColor overrides it.
    static let defaultPinColor = UIColor(red: 31 / 255, green: 31 / 255, blue: 31 / 255, alpha: 1)
    static let findPinColor = UIColor(red: 31 / 255, green: 31 / 255, blue: 31 / 255, alpha: 1)

    private static let size = CGSize(width: 72, height: 96)
    // Marker head: a 52x52 white card (rounded square for Find, circle for Waypoint) inside
    // a thin black outline that narrows below the card to a rounded tip. The tip ends at
    // height - 10, where the classic pin ended, so the bottom anchor and every marker's
    // position on the map are unchanged.
    private static let headSize: CGFloat = 52
    private static let headY: CGFloat = 46
    private static let outline: CGFloat = 2.5
    private static let frameInset: CGFloat = 3.8
    private static let squareRadius: CGFloat = 6
    private static let chinHalfWidth: CGFloat = 19
    private static let chinBand: CGFloat = 2.5
    private static let tipRounding: CGFloat = 2
    private static let placeholderColor = UIColor(red: 229 / 255, green: 231 / 255, blue: 235 / 255, alpha: 1)
    private static let placeholderGlyphColor = UIColor(red: 107 / 255, green: 114 / 255, blue: 128 / 255, alpha: 1)
    private static let crosshairColor = UIColor(red: 211 / 255, green: 47 / 255, blue: 47 / 255, alpha: 1)
    // Private (lock) tile: warm, blurred-photo look with a white lock.
    private static let lockTileColor = UIColor(red: 134 / 255, green: 108 / 255, blue: 99 / 255, alpha: 1)
    private static let lockLightColor = UIColor(red: 196 / 255, green: 171 / 255, blue: 156 / 255, alpha: 0.6)
    private static let lockDarkColor = UIColor(red: 94 / 255, green: 74 / 255, blue: 67 / 255, alpha: 0.65)
    private static let imageDecodeSize = 128

    private struct Download {
        var data = Data()
        let source: String
        let hosts: String
        let completion: (UIImage?) -> Void
        let task: URLSessionDataTask
    }

    private let pins = NSCache<NSString, UIImage>()
    private let images = NSCache<NSString, UIImage>()
    // Download bookkeeping is only touched on this serial queue.
    private let queue: OperationQueue = {
        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 1
        return queue
    }()
    private var downloads: [Int: Download] = [:]
    private var currentSession: URLSession?
    private func activeSession() -> URLSession {
        if let session = currentSession { return session }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 10
        configuration.httpMaximumConnectionsPerHost = 3
        let session = URLSession(configuration: configuration, delegate: self, delegateQueue: self.queue)
        currentSession = session
        return session
    }

    override init() {
        super.init()
        pins.countLimit = 64
        images.countLimit = 64
    }

    func cancelAll() {
        queue.addOperation {
            for download in self.downloads.values { download.task.cancel() }
            self.downloads.removeAll()
            self.currentSession?.invalidateAndCancel()
            self.currentSession = nil
            self.pins.removeAllObjects()
            self.images.removeAllObjects()
        }
    }

    func hasImage(_ source: String?) -> Bool {
        guard let source = source else { return false }
        return images.object(forKey: source as NSString) != nil
    }

    /// Returns the pin for this look, using the cached image when it has loaded.
    func pin(color: UIColor, isFind: Bool, source: String?, image: UIImage? = nil) -> PointAnnotation.Image {
        let image = image ?? source.flatMap { images.object(forKey: $0 as NSString) }
        var name = "marker-\(Self.colorKey(color))-\(isFind ? "find" : "pin")"
        if let source = source, image != nil {
            let digest = SHA256.hash(data: Data(source.utf8))
            name += "-" + digest.prefix(12).map { String(format: "%02x", $0) }.joined()
        }

        if let cached = pins.object(forKey: name as NSString) {
            return .init(image: cached, name: name)
        }
        let drawn = Self.draw(pinColor: color, isFind: isFind, image: image)
        pins.setObject(drawn, forKey: name as NSString)
        return .init(image: drawn, name: name)
    }

    /// Private marker: same shape, with a lock tile instead of an image.
    func lockedPin(color: UIColor, isFind: Bool) -> PointAnnotation.Image {
        let name = "marker-locked-\(Self.colorKey(color))-\(isFind ? "find" : "pin")"
        if let cached = pins.object(forKey: name as NSString) {
            return .init(image: cached, name: name)
        }
        let drawn = Self.draw(pinColor: color, isFind: isFind, image: nil, locked: true)
        pins.setObject(drawn, forKey: name as NSString)
        return .init(image: drawn, name: name)
    }

    /// Loads an allowed image source off the main thread; `completion` runs on a background queue.
    func load(_ source: String, hosts: String, completion: @escaping (UIImage?) -> Void) {
        if source.hasPrefix("data:") {
            DispatchQueue.global(qos: .utility).async {
                let payload = source.split(separator: ",", maxSplits: 1).last.map(String.init) ?? ""
                let data = Data(base64Encoded: payload, options: .ignoreUnknownCharacters)
                let image = data.flatMap {
                    $0.count <= MapboxSecurity.maxMarkerImageBytes ? self.decode($0, source: source) : nil
                }
                completion(image)
            }
            return
        }

        guard let url = URL(string: source) else {
            completion(nil)
            return
        }
        var request = URLRequest(url: url)
        request.setValue("image/*", forHTTPHeaderField: "Accept")
        queue.addOperation {
            let task = self.activeSession().dataTask(with: request)
            self.downloads[task.taskIdentifier] = Download(source: source, hosts: hosts, completion: completion, task: task)
            task.resume()
        }
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        let ok = (response as? HTTPURLResponse)?.statusCode == 200
            && response.expectedContentLength <= Int64(MapboxSecurity.maxMarkerImageBytes)
        completionHandler(ok ? .allow : .cancel)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        guard var download = downloads[dataTask.taskIdentifier] else { return }
        download.data.append(data)
        downloads[dataTask.taskIdentifier] = download
        if download.data.count > MapboxSecurity.maxMarkerImageBytes {
            dataTask.cancel()
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        // Redirect targets must pass the same policy as the original URL.
        let allowed = downloads[task.taskIdentifier].map {
            MapboxSecurity.markerImageAllowed(request.url?.absoluteString ?? "", hosts: $0.hosts)
        } ?? false
        completionHandler(allowed ? request : nil)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let download = downloads.removeValue(forKey: task.taskIdentifier) else { return }
        let status = (task.response as? HTTPURLResponse)?.statusCode
        guard error == nil, status == 200, download.data.count <= MapboxSecurity.maxMarkerImageBytes else {
            NSLog("MapboxPlugin: Marker image could not be loaded.")
            download.completion(nil)
            return
        }
        download.completion(decode(download.data, source: download.source))
    }

    private func decode(_ data: Data, source: String) -> UIImage? {
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: Self.imageDecodeSize
        ]
        guard let imageSource = CGImageSourceCreateWithData(data as CFData, nil),
              let cgImage = CGImageSourceCreateThumbnailAtIndex(imageSource, 0, options as CFDictionary) else {
            return nil
        }
        let image = UIImage(cgImage: cgImage)
        images.setObject(image, forKey: source as NSString)
        return image
    }

    private static func colorKey(_ color: UIColor) -> String {
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        color.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return [red, green, blue, alpha].map { String(format: "%02x", Int(($0 * 255).rounded())) }.joined()
    }

    private static func draw(pinColor: UIColor, isFind: Bool, image: UIImage?, locked: Bool = false) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            let cg = context.cgContext
            let centerX = size.width / 2

            // Soft shadow: the outline silhouette, slightly larger, lower and translucent.
            UIColor.black.withAlphaComponent(0.157).setFill()
            outlinePath(isFind: isFind, centerX: centerX, dy: 1, grow: 1.5).fill()
            chinPath(isFind: isFind, centerX: centerX, dy: 1, grow: 1.5).fill()

            // Black outline around the card, narrowing below it to the tip on the coordinate.
            pinColor.setFill()
            outlinePath(isFind: isFind, centerX: centerX, dy: 0, grow: 0).fill()
            chinPath(isFind: isFind, centerX: centerX, dy: 0, grow: 0).fill()

            // White card: rounded square for Find markers, circle for Waypoint markers.
            UIColor.white.setFill()
            headPath(isFind: isFind, centerX: centerX, centerY: headY, inset: 0).fill()

            let inner = headPath(isFind: isFind, centerX: centerX, centerY: headY, inset: frameInset)
            if locked {
                drawLockTile(cg, inner: inner, isFind: isFind, centerX: centerX)
                return
            }
            if let image = image {
                // Center-crop the image inside the white border.
                let side = headSize - 2 * frameInset
                let scale = side / min(image.size.width, image.size.height)
                let drawSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
                cg.saveGState()
                inner.addClip()
                image.draw(in: CGRect(
                    x: centerX - drawSize.width / 2,
                    y: headY - drawSize.height / 2,
                    width: drawSize.width,
                    height: drawSize.height
                ))
                cg.restoreGState()
                return
            }

            cg.setLineCap(.round)
            if isFind {
                // Find without an image (or still loading): grey tile with a magnifying glass.
                placeholderColor.setFill()
                inner.fill()
                cg.setStrokeColor(placeholderGlyphColor.cgColor)
                cg.setLineWidth(3)
                cg.strokeEllipse(in: circle(centerX - 2, headY - 2, 7))
                cg.move(to: CGPoint(x: centerX + 3, y: headY + 3))
                cg.addLine(to: CGPoint(x: centerX + 9, y: headY + 9))
                cg.strokePath()
            } else {
                // Waypoint without an image (or still loading): red compass crosshair.
                cg.setStrokeColor(crosshairColor.cgColor)
                cg.setLineWidth(3)
                cg.strokeEllipse(in: circle(centerX, headY, 10.5))
                for (from, to) in [((0.0, -17.0), (0.0, -8.0)), ((0.0, 8.0), (0.0, 17.0)),
                                   ((-17.0, 0.0), (-8.0, 0.0)), ((8.0, 0.0), (17.0, 0.0))] {
                    cg.move(to: CGPoint(x: centerX + CGFloat(from.0), y: headY + CGFloat(from.1)))
                    cg.addLine(to: CGPoint(x: centerX + CGFloat(to.0), y: headY + CGFloat(to.1)))
                }
                cg.strokePath()
                let star = UIBezierPath()
                let points: [(CGFloat, CGFloat)] = [(0, -8.5), (2, -2), (8.5, 0), (2, 2), (0, 8.5), (-2, 2), (-8.5, 0), (-2, -2)]
                for (index, point) in points.enumerated() {
                    let location = CGPoint(x: centerX + point.0, y: headY + point.1)
                    if index == 0 { star.move(to: location) } else { star.addLine(to: location) }
                }
                star.close()
                crosshairColor.setFill()
                star.fill()
            }
        }
    }

    /// Warm "hidden photo" background with a centered white outlined lock.
    private static func drawLockTile(_ cg: CGContext, inner: UIBezierPath, isFind: Bool, centerX: CGFloat) {
        cg.saveGState()
        inner.addClip()
        lockTileColor.setFill()
        inner.fill()
        // Soft light and dark patches, like a heavily blurred photo.
        for (color, center) in [(lockLightColor, CGPoint(x: centerX - 12, y: headY - 14)),
                                (lockDarkColor, CGPoint(x: centerX + 14, y: headY + 16))] {
            let colors = [color.cgColor, color.withAlphaComponent(0).cgColor] as CFArray
            if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1]) {
                cg.drawRadialGradient(gradient, startCenter: center, startRadius: 0,
                                      endCenter: center, endRadius: 24, options: [])
            }
        }
        cg.restoreGState()

        // A smaller lock in the circle keeps it clear of the curved edge.
        let s: CGFloat = isFind ? 1 : 0.85
        let cx = centerX
        let cy = headY + 1.5 * s
        let body = UIBezierPath(
            roundedRect: CGRect(x: cx - 10 * s, y: cy - 1 * s, width: 20 * s, height: 14 * s),
            cornerRadius: 2.5 * s
        )
        UIColor.white.withAlphaComponent(0.1).setFill()
        body.fill()

        UIColor.white.setStroke()
        body.lineWidth = 2.6 * s
        body.lineJoinStyle = .round
        body.stroke()

        let shackle = UIBezierPath()
        shackle.move(to: CGPoint(x: cx - 6 * s, y: cy - 1 * s))
        shackle.addLine(to: CGPoint(x: cx - 6 * s, y: cy - 6 * s))
        shackle.addArc(withCenter: CGPoint(x: cx, y: cy - 6 * s), radius: 6 * s,
                       startAngle: .pi, endAngle: 0, clockwise: true)
        shackle.addLine(to: CGPoint(x: cx + 6 * s, y: cy - 1 * s))
        shackle.lineWidth = 2.6 * s
        shackle.lineCapStyle = .round
        shackle.lineJoinStyle = .round
        shackle.stroke()

        let keyhole = UIBezierPath(ovalIn: circle(cx, cy + 5 * s, 2.2 * s))
        keyhole.lineWidth = 1.8 * s
        keyhole.stroke()
        let slot = UIBezierPath()
        slot.move(to: CGPoint(x: cx, y: cy + 7.2 * s))
        slot.addLine(to: CGPoint(x: cx, y: cy + 9.5 * s))
        slot.lineWidth = 1.8 * s
        slot.lineCapStyle = .round
        slot.stroke()
    }

    /// Rounded square (Find) or circle (Waypoint) centered at centerY; a negative inset grows it.
    private static func headPath(isFind: Bool, centerX: CGFloat, centerY: CGFloat, inset: CGFloat) -> UIBezierPath {
        let half = headSize / 2 - inset
        let rect = CGRect(x: centerX - half, y: centerY - half, width: half * 2, height: half * 2)
        if isFind {
            return UIBezierPath(roundedRect: rect, cornerRadius: max(2, squareRadius - inset * 0.5))
        }
        return UIBezierPath(ovalIn: rect)
    }

    /// Black outline around the card: for Find it extends chinBand further below the card so
    /// the chin starts from a full-width band; for Waypoint it is a slightly larger circle.
    private static func outlinePath(isFind: Bool, centerX: CGFloat, dy: CGFloat, grow: CGFloat) -> UIBezierPath {
        guard isFind else {
            return headPath(isFind: false, centerX: centerX, centerY: headY + dy, inset: -outline - grow)
        }
        let half = headSize / 2 + outline + grow
        let rect = CGRect(x: centerX - half, y: headY + dy - half, width: half * 2, height: half * 2 + chinBand)
        return UIBezierPath(roundedRect: rect, cornerRadius: squareRadius + outline * 0.5 + grow)
    }

    /// Wedge from the outline down to a rounded tip at the coordinate. Square heads get a
    /// flat chin; circular heads get tangent sides, making a teardrop.
    private static func chinPath(isFind: Bool, centerX: CGFloat, dy: CGFloat, grow: CGFloat) -> UIBezierPath {
        let tipY = size.height - 10 + dy + grow
        let sideX: CGFloat
        let sideY: CGFloat
        if isFind {
            sideX = chinHalfWidth + grow
            sideY = headY + headSize / 2 + outline + chinBand + dy
        } else {
            // Tangent points from the tip to the outlined circle.
            let radius = headSize / 2 + outline + grow
            let cosine = radius / (tipY - (headY + dy))
            let sine = (max(0, 1 - cosine * cosine)).squareRoot()
            sideX = radius * sine
            sideY = headY + dy + radius * cosine
        }
        // Start inside the head so the wedge joins it without a seam.
        let topY = headY + dy
        let topX = min(sideX * (tipY - topY) / (tipY - sideY), headSize / 2)
        let shoulder = tipRounding / (tipY - sideY) * sideX
        let path = UIBezierPath()
        path.move(to: CGPoint(x: centerX - topX, y: topY))
        path.addLine(to: CGPoint(x: centerX + topX, y: topY))
        path.addLine(to: CGPoint(x: centerX + sideX, y: sideY))
        path.addLine(to: CGPoint(x: centerX + shoulder, y: tipY - tipRounding))
        path.addQuadCurve(to: CGPoint(x: centerX - shoulder, y: tipY - tipRounding), controlPoint: CGPoint(x: centerX, y: tipY))
        path.addLine(to: CGPoint(x: centerX - sideX, y: sideY))
        path.close()
        return path
    }

    private static func circle(_ x: CGFloat, _ y: CGFloat, _ radius: CGFloat) -> CGRect {
        CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)
    }
}
