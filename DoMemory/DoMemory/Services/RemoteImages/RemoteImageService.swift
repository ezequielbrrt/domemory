//
//  RemoteImageService.swift
//  DoMemory
//
//  Loads and caches images fetched over the network. Introduced for season
//  artwork, which Firebase supplies by URL so a season published after the
//  player's last app update still arrives with its own art.
//
//  Deliberately not `AsyncImage`: that re-fetches whenever its view is rebuilt,
//  and the season card sits above the menu's `TabView`, so it is rebuilt on
//  every tab change. The result would be a visible flash of flat accent colour
//  each time the player switched tabs.
//

import UIKit

/// Fetches remote images through a two-level cache: decoded `UIImage`s in
/// memory, raw responses on disk via the session's own `URLCache`.
///
/// The disk half is what makes a season's background free after the first cold
/// launch. The memory half is what makes it free on every redraw after that.
@MainActor
final class RemoteImageService {
    static let shared = RemoteImageService()

    /// Decoded images, keyed by URL. `NSCache` evicts under memory pressure on
    /// its own, so there is no warning observer to keep in sync.
    private let memory = NSCache<NSURL, UIImage>()

    private let session: URLSession

    /// One in-flight request per URL. Both season surfaces can ask for the same
    /// artwork in the same frame — the menu card while the map behind it is
    /// still on screen — and without this each caller would open its own
    /// connection for the same bytes.
    private var inFlight: [URL: Task<UIImage?, Never>] = [:]

    init(session: URLSession? = nil) {
        if let session {
            self.session = session
        } else {
            let configuration = URLSessionConfiguration.default
            // Sized for a handful of full-screen backgrounds and cards. The
            // policy stays `.useProtocolCachePolicy`, so replacing a season's
            // art at the same URL still reaches players once the response's
            // own `Cache-Control` lifetime is up.
            configuration.urlCache = URLCache(
                memoryCapacity: 8 * 1024 * 1024,
                diskCapacity: 64 * 1024 * 1024,
                diskPath: "com.domemory.remote-images"
            )
            configuration.requestCachePolicy = .useProtocolCachePolicy
            // Artwork is decoration; it must never hold up a level map behind a
            // slow connection.
            configuration.timeoutIntervalForRequest = 15
            configuration.waitsForConnectivity = false
            self.session = URLSession(configuration: configuration)
        }

        // Roughly 40 MB of decoded pixels, costed in bytes below.
        memory.totalCostLimit = 40 * 1024 * 1024
    }

    /// The decoded image if it is already in memory. Synchronous, so a surface
    /// that has shown this artwork before can paint it in its first frame
    /// rather than fading it in again.
    func cachedImage(for url: URL) -> UIImage? {
        memory.object(forKey: url as NSURL)
    }

    /// The image at `url`, or nil if it could not be fetched or decoded.
    ///
    /// Never throws: every caller's fallback is the flat colour it drew before
    /// the season had art, so a failure is a visual no-op rather than an error
    /// anyone needs to handle.
    func image(for url: URL) async -> UIImage? {
        if let cached = cachedImage(for: url) { return cached }
        if let existing = inFlight[url] { return await existing.value }

        let task = Task<UIImage?, Never> { [session] in
            // Detached from the main actor: decoding a full-screen background
            // is expensive enough to drop frames on the level map that is
            // waiting for it.
            await Task.detached(priority: .utility) { () -> UIImage? in
                do {
                    let (data, response) = try await session.data(from: url)
                    if let http = response as? HTTPURLResponse,
                       !(200..<300).contains(http.statusCode) {
                        return nil
                    }
                    // `preparingForDisplay` forces the decode here instead of
                    // leaving it to the first draw on the main thread.
                    return UIImage(data: data)?.preparingForDisplay()
                } catch {
                    return nil
                }
            }.value
        }

        inFlight[url] = task
        let image = await task.value
        inFlight[url] = nil

        if let image {
            memory.setObject(image, forKey: url as NSURL, cost: image.estimatedByteCount)
        }
        return image
    }
}

private extension UIImage {
    /// Decoded size in bytes, for `NSCache` costing. Close enough to weigh one
    /// image against another, which is all the cache needs.
    var estimatedByteCount: Int {
        guard let cgImage else { return 0 }
        return cgImage.bytesPerRow * cgImage.height
    }
}
