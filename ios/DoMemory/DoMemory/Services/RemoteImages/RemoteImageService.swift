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

import CryptoKit
import UIKit

/// Fetches remote images through a three-level cache: decoded `UIImage`s in
/// memory, raw response bytes in a dedicated on-disk store, and (as a last
/// resort) the session's own `URLCache`-backed network fetch.
///
/// The disk store is what makes a season's art free forever after the first
/// successful load — Firebase Hosting now serves it with an immutable
/// `Cache-Control`, so once a given URL's bytes are on disk they never need
/// revalidating, even across app relaunches. The memory half is what makes it
/// free on every redraw after that.
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

    /// Directory holding one file per cached URL (named by a hash of the URL),
    /// independent of the session's `URLCache`. A hit here means the response
    /// bytes are known-good forever (see the type doc), so `image(for:)` never
    /// revalidates them over HTTP.
    private let diskCacheDirectory: URL

    /// Soft cap on `diskCacheDirectory`'s total size, in the same order of
    /// magnitude as the `URLCache` disk budget below. Checked opportunistically
    /// after each write so the directory doesn't grow unbounded as seasons
    /// accumulate over the app's lifetime.
    private let diskCacheCapacityBytes: Int

    init(session: URLSession? = nil, diskCacheDirectory: URL? = nil, diskCacheCapacityBytes: Int? = nil) {
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

        if let diskCacheDirectory {
            self.diskCacheDirectory = diskCacheDirectory
        } else {
            let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            self.diskCacheDirectory = caches.appendingPathComponent("RemoteImages", isDirectory: true)
        }
        // Same order of magnitude as the `URLCache` disk budget above.
        self.diskCacheCapacityBytes = diskCacheCapacityBytes ?? 64 * 1024 * 1024

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
    /// Checks memory, then the on-disk store, and only falls through to the
    /// network when neither has the bytes. A disk hit skips HTTP entirely — no
    /// conditional GET — because a cached URL's bytes are good forever (see
    /// the type doc).
    ///
    /// Never throws: every caller's fallback is the flat colour it drew before
    /// the season had art, so a failure is a visual no-op rather than an error
    /// anyone needs to handle.
    func image(for url: URL) async -> UIImage? {
        if let cached = cachedImage(for: url) { return cached }
        if let existing = inFlight[url] { return await existing.value }

        let task = Task<UIImage?, Never> { [session, diskCacheDirectory, diskCacheCapacityBytes] in
            // Detached from the main actor: decoding a full-screen background
            // (and the disk I/O below) is expensive enough to drop frames on
            // the level map that is waiting for it.
            await Task.detached(priority: .utility) { () -> UIImage? in
                if let diskData = Self.readDiskCache(for: url, directory: diskCacheDirectory),
                   let cached = UIImage(data: diskData)?.preparingForDisplay() {
                    return cached
                }

                do {
                    let (data, response) = try await session.data(from: url)
                    if let http = response as? HTTPURLResponse,
                       !(200..<300).contains(http.statusCode) {
                        return nil
                    }
                    // `preparingForDisplay` forces the decode here instead of
                    // leaving it to the first draw on the main thread.
                    guard let image = UIImage(data: data)?.preparingForDisplay() else {
                        return nil
                    }
                    Self.writeDiskCache(
                        data: data,
                        for: url,
                        directory: diskCacheDirectory,
                        capacityBytes: diskCacheCapacityBytes
                    )
                    return image
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

    // MARK: - On-disk store

    /// `nonisolated` so it can run inside the detached task above without
    /// hopping back to the main actor for pure file I/O. Takes every value it
    /// needs as a parameter rather than touching `self`.
    nonisolated private static func diskCacheFileName(for url: URL) -> String {
        let digest = SHA256.hash(data: Data(url.absoluteString.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    nonisolated private static func readDiskCache(for url: URL, directory: URL) -> Data? {
        let fileURL = directory.appendingPathComponent(diskCacheFileName(for: url))
        return try? Data(contentsOf: fileURL)
    }

    nonisolated private static func writeDiskCache(data: Data, for url: URL, directory: URL, capacityBytes: Int) {
        let fileManager = FileManager.default
        do {
            try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
            let fileURL = directory.appendingPathComponent(diskCacheFileName(for: url))
            try data.write(to: fileURL, options: .atomic)
            evictIfNeeded(directory: directory, capacityBytes: capacityBytes)
        } catch {
            // Best-effort: a write failure just means this URL is fetched
            // again next time rather than being cached forever.
        }
    }

    /// Simple LRU-by-modification-date eviction, run opportunistically after
    /// each write. Good enough to keep the directory bounded without a
    /// separate index file to maintain.
    nonisolated private static func evictIfNeeded(directory: URL, capacityBytes: Int) {
        let fileManager = FileManager.default
        guard let entries = try? fileManager.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: [.contentModificationDateKey, .fileSizeKey]
        ) else { return }

        let files: [(url: URL, date: Date, size: Int)] = entries.compactMap { fileURL in
            guard let values = try? fileURL.resourceValues(forKeys: [.contentModificationDateKey, .fileSizeKey]),
                  let date = values.contentModificationDate,
                  let size = values.fileSize else { return nil }
            return (fileURL, date, size)
        }

        var totalSize = files.reduce(0) { $0 + $1.size }
        guard totalSize > capacityBytes else { return }

        for file in files.sorted(by: { $0.date < $1.date }) {
            guard totalSize > capacityBytes else { break }
            if (try? fileManager.removeItem(at: file.url)) != nil {
                totalSize -= file.size
            }
        }
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
