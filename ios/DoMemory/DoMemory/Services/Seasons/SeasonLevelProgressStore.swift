//
//  SeasonLevelProgressStore.swift
//  DoMemory
//
//  Binds a season's emoji pool and length to its SeasonProgressService, so
//  season play satisfies LevelProgressStore and can be handed straight to
//  MemorizeViewModel's `.level` case.
//

import Foundation

/// A `SeasonProgressService` bound to the season it tracks.
///
/// The pool and the length live here rather than as stored properties on
/// `SeasonProgressService` for two reasons. The service is addressed by season
/// id alone — that is how it namespaces its keys and how its own tests exercise
/// it — so giving it an optional stored pool would let a pool-less instance
/// silently deal an empty board. And keeping `board(for:emojiPool:)` a pure
/// function of its inputs keeps board determinism testable without constructing
/// a whole season.
struct SeasonLevelProgressStore: LevelProgressStore {
    let progress: SeasonProgressService
    /// The season's emoji pool, already deduplicated and length-checked at
    /// decode time (`Season.minimumEmojiPoolSize`).
    let emojiPool: [String]
    /// How many levels the season holds. The ceiling `nextLevel(after:)`
    /// enforces.
    let levelCount: Int

    /// - Parameter progress: the progress service to use. Defaults to one built
    ///   from the season, and exists so tests can inject a scratch
    ///   `UserDefaults` suite.
    init(season: Season, progress: SeasonProgressService? = nil) {
        self.progress = progress ?? SeasonProgressService(season: season)
        self.emojiPool = season.emojiPool
        self.levelCount = season.levelCount
    }

    var highestUnlockedLevel: Int { progress.highestUnlockedLevel }

    func stars(for level: Int) -> Int { progress.stars(for: level) }

    func isUnlocked(_ level: Int) -> Bool { progress.isUnlocked(level) }

    func board(for level: Int) -> Memorama {
        progress.board(for: level, emojiPool: emojiPool)
    }

    @discardableResult
    func recordCompletion(
        level: Int,
        didWin: Bool,
        timeRemaining: Int,
        totalTime: Int,
        failedTries: Int
    ) -> Int {
        progress.recordCompletion(
            level: level,
            didWin: didWin,
            timeRemaining: timeRemaining,
            totalTime: totalTime,
            failedTries: failedTries
        )
    }

    func skipLevel(_ level: Int) { progress.skipLevel(level) }

    /// nil past the season's last level — a season is finite, so the win modal
    /// must not offer level 21 of a 20-level season.
    func nextLevel(after level: Int) -> Int? {
        progress.nextLevel(after: level, levelCount: levelCount)
    }
}

extension LevelContext {
    /// A context for playing `level` of `season`.
    ///
    /// The single place a season's id, length and pool are read together, so
    /// the ceiling the store enforces and the `levelCount` the UI displays can
    /// never drift apart.
    static func season(
        _ season: Season,
        level: Int,
        progress: SeasonProgressService? = nil
    ) -> LevelContext {
        LevelContext(
            number: level,
            store: SeasonLevelProgressStore(season: season, progress: progress),
            seasonID: season.id,
            levelCount: season.levelCount
        )
    }
}
