//
//  LevelProgressStore.swift
//  DoMemory
//
//  The progress-and-board surface a numbered-level game is played against, so
//  endless Levels and a finite Season can share MemorizeViewModel's single
//  `.level` case instead of growing a parallel game mode.
//

import Foundation

/// Everything a numbered-level game needs from whatever owns its progress.
///
/// Deliberately *not* `@MainActor`. `LevelProgressService` is a plain
/// UserDefaults-backed singleton with no isolation today and is read from
/// non-isolated contexts; isolating the protocol would force those callers to
/// hop for no benefit. `MemorizeViewModel` is `@MainActor` and reaches the
/// store only from the main actor, which a non-isolated protocol allows.
protocol LevelProgressStore {
    /// Highest level the player may currently play. Levels
    /// `1..<highestUnlockedLevel` have been cleared.
    var highestUnlockedLevel: Int { get }

    /// 0 if the level has not been cleared yet.
    func stars(for level: Int) -> Int

    func isUnlocked(_ level: Int) -> Bool

    /// The board to deal for `level`.
    ///
    /// This is the one member the two stores did not already share:
    /// `SeasonProgressService` needs the season's emoji pool as well as the
    /// level number. Rather than widen the protocol with a pool parameter that
    /// endless Levels would have to pass `nil` for, the season side binds its
    /// pool in an adapter (`SeasonLevelProgressStore`) so the protocol keeps a
    /// single uniform accessor.
    func board(for level: Int) -> Memorama

    /// Records the result of an attempt and returns the level's stored star
    /// count afterwards (0 on a loss).
    @discardableResult
    func recordCompletion(level: Int, didWin: Bool, timeRemaining: Int, totalTime: Int, failedTries: Int) -> Int

    /// Unlocks the next level without clearing this one.
    func skipLevel(_ level: Int)

    /// The level to offer after clearing `level`, or nil when there is none.
    /// Endless Levels always has one; a finite season stops at its last.
    func nextLevel(after level: Int) -> Int?
}

extension LevelProgressService: LevelProgressStore {
    /// Endless by construction — there is no last level to stop at.
    func nextLevel(after level: Int) -> Int? { level + 1 }
}

/// Which numbered level is being played and who owns its progress.
///
/// Carrying the store as a value means `GameMode.level` no longer implies
/// `LevelProgressService`: the same timer, mistake budget, power-ups, stars,
/// lives and skip flow serve a season by swapping this one witness.
struct LevelContext {
    /// The level number as the player sees it. A season's levels are numbered
    /// from 1 like endless Levels', because `LevelCurve` is reused with no
    /// offset.
    let number: Int
    /// Where unlocks, stars and boards for this level come from.
    let store: LevelProgressStore
    /// nil for endless Levels; the Realtime Database season key otherwise.
    ///
    /// Rides along on the `levelStarted` / `levelFinished` / `levelUnlocked`
    /// events as a `season_id` parameter so season play can be told apart from
    /// endless play without a parallel event set.
    let seasonID: String?
    /// nil for endless Levels; the season's length otherwise. A season is
    /// finite, so callers must not simply increment past this.
    let levelCount: Int?

    init(
        number: Int,
        store: LevelProgressStore = LevelProgressService.shared,
        seasonID: String? = nil,
        levelCount: Int? = nil
    ) {
        self.number = number
        self.store = store
        self.seasonID = seasonID
        self.levelCount = levelCount
    }

    /// The level to offer after clearing this one; nil on the final level of a
    /// finite season. Endless Levels never returns nil.
    var nextLevelNumber: Int? { store.nextLevel(after: number) }

    /// The board for this level, from whichever store owns it.
    func board() -> Memorama { store.board(for: number) }

    /// The same season and store, moved to `level`.
    func advanced(to level: Int) -> LevelContext {
        LevelContext(number: level, store: store, seasonID: seasonID, levelCount: levelCount)
    }
}
