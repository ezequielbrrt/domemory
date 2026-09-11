//
//  SeasonCatalogService.swift
//  DoMemory
//
//  One-shot read of the Realtime Database `/seasons` node, plus the
//  UserDefaults cache that lets the season card render instantly on cold
//  launch and while offline.
//

import Foundation
import Observation
import SwiftUI
import FirebaseCore
import FirebaseDatabase
import FirebaseAuth

@Observable
final class SeasonCatalogService {
    static let shared = SeasonCatalogService()

    /// The season to show today, or nil when there is none. Nil is the correct
    /// answer for every failure mode too: no cache and no network, a kill
    /// switch flipped off, or a payload that decoded to nothing.
    private(set) var activeSeason: Season?

    /// Everything currently known, active or not. Kept so `refreshActiveSeason`
    /// can re-evaluate the window without another network round trip.
    private(set) var seasons: [Season] = []

    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private var hasLoaded = false

    /// Seam for warming `RemoteImageService`'s cache with one artwork URL.
    /// Defaults to the real service; tests substitute a spy so they can
    /// observe *which* URLs were requested without depending on
    /// `RemoteImageService`'s network/disk internals (covered separately).
    @ObservationIgnored private let prefetchImage: @Sendable (URL) async -> Void

    init(
        defaults: UserDefaults = .standard,
        prefetchImage: @escaping @Sendable (URL) async -> Void = { url in
            _ = await RemoteImageService.shared.image(for: url)
        }
    ) {
        self.defaults = defaults
        self.prefetchImage = prefetchImage
        // Read the cache synchronously so the first render already has an
        // answer; the network read below only ever corrects it.
        seasons = Self.decodeCache(defaults.data(forKey: UserDefaultsKeys.seasonCatalog))
        refreshActiveSeason()
    }

    // MARK: - Loading

    /// Re-reads `/seasons` once. Safe to call repeatedly; only the first call
    /// per launch hits the network unless `force` is set.
    func load(force: Bool = false) {
        guard force || !hasLoaded else { return }
        hasLoaded = true

        // Mirrors MenuViewModel.getData(): guard that Firebase is configured,
        // sign in anonymously, then take a single snapshot of the node.
        guard FirebaseApp.app() != nil else {
            print("Firebase is not configured. Skipping remote season fetch.")
            return
        }

        Auth.auth().signInAnonymously { [weak self] authResult, error in
            guard let self else { return }

            if let error {
                print("Firebase anonymous sign-in failed for the season catalog")
                print(error)
                return
            }
            guard authResult != nil else { return }

            Database.database().reference().child("seasons").observeSingleEvent(of: .value) { [weak self] snapshot in
                guard let self else { return }
                // `/seasons` is a dictionary keyed by season id, not an array
                // like `/data`. An absent or unexpected node leaves the cached
                // catalog in place rather than wiping it.
                guard let payload = snapshot.value as? [String: Any] else { return }
                self.apply(payload: payload)
            }
        }
    }

    /// Decodes a raw `/seasons` payload, caches it, and re-picks the active
    /// season. Split out from `load` so it is reachable without Firebase.
    func apply(payload: [String: Any], on date: Date = Date()) {
        seasons = Self.decodeSeasons(from: payload)
        cache(seasons)
        refreshActiveSeason(on: date)
    }

    /// Re-evaluates which season is active. Cheap; call it when the app returns
    /// to the foreground so a season cannot appear stale across midnight.
    func refreshActiveSeason(on date: Date = Date(), calendar: Calendar = .current) {
        let previousSeasonID = activeSeason?.id
        let newActiveSeason = Self.selectActive(from: seasons, on: date, calendar: calendar)
        activeSeason = newActiveSeason

        // Only warm the cache when the active season actually changed to a
        // new, non-nil season — this method is called repeatedly (e.g. on
        // every app foreground) and re-prefetching the same season each time
        // would spawn needless tasks, even though a cache hit inside
        // `RemoteImageService` would make each of them cheap regardless.
        guard let newActiveSeason, newActiveSeason.id != previousSeasonID else { return }
        prefetchArtwork(for: newActiveSeason)
    }

    /// Fire-and-forget cache warm for `season`'s card and both background
    /// artwork variants, so `SeasonLevelsView`/the menu card do not show a
    /// flash of flat colour on first paint. Must never block the caller —
    /// `refreshActiveSeason` is synchronous and is called from `init`.
    ///
    /// Only `Sendable` values (the resolved `URL`s and the `prefetchImage`
    /// closure) cross into the detached task; `self` and the `Season` value
    /// are never read from inside it, so there is nothing to race against a
    /// later, unrelated call to `refreshActiveSeason` on the main actor.
    private func prefetchArtwork(for season: Season) {
        let urls: [URL] = [
            season.cardArtworkURL,
            season.backgroundArtworkURL(for: .light),
            season.backgroundArtworkURL(for: .dark)
        ].compactMap { $0 }
        guard !urls.isEmpty else { return }

        let prefetchImage = self.prefetchImage
        Task.detached {
            for url in urls {
                await prefetchImage(url)
            }
        }
    }

    // MARK: - Decoding

    /// Decodes each child of `/seasons`, skipping any season that fails
    /// validation. One malformed or under-sized season must not cost the
    /// player a second, valid one.
    static func decodeSeasons(from payload: [String: Any]) -> [Season] {
        let decoder = JSONDecoder()
        return payload.compactMap { key, value -> Season? in
            guard var body = value as? [String: Any] else { return nil }
            // The dictionary key *is* the season id; it has no place in the
            // value body, so inject it before decoding.
            body["id"] = key
            do {
                let data = try JSONSerialization.data(withJSONObject: body, options: [])
                return try decoder.decode(Season.self, from: data)
            } catch {
                print("Skipping season \"\(key)\": \(error)")
                return nil
            }
        }
        // Dictionary iteration order is not stable, so impose one before
        // anything downstream depends on it.
        .sorted { $0.id < $1.id }
    }

    // MARK: - Active selection

    /// Highest `priority` among the seasons active on `date`.
    ///
    /// Priority alone is not a total order — two seasons can legitimately share
    /// one — so ties break on the lexicographically smallest id. Without that
    /// second key the winner would depend on dictionary iteration order and
    /// could flicker between launches.
    static func selectActive(
        from seasons: [Season],
        on date: Date = Date(),
        calendar: Calendar = .current
    ) -> Season? {
        seasons
            .filter { $0.isActive(on: date, calendar: calendar) }
            .sorted { lhs, rhs in
                if lhs.priority != rhs.priority { return lhs.priority > rhs.priority }
                return lhs.id < rhs.id
            }
            .first
    }

    // MARK: - Cache

    private func cache(_ seasons: [Season]) {
        guard let data = try? JSONEncoder().encode(seasons) else { return }
        defaults.set(data, forKey: UserDefaultsKeys.seasonCatalog)
    }

    /// Only already-validated seasons are ever written, so a cache that no
    /// longer decodes is treated as absent rather than partially trusted.
    static func decodeCache(_ data: Data?) -> [Season] {
        guard let data else { return [] }
        return (try? JSONDecoder().decode([Season].self, from: data)) ?? []
    }
}
