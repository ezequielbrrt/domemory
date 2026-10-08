//
//  LevelCurve.swift
//  DoMemory
//
//  Pure difficulty curve for Levels mode: how many pairs and how much time a
//  given level number gets. Deliberately table-driven (anchor points +
//  linear interpolation) rather than a closed-form formula, since the
//  approved curve is sub-linear and this keeps it easy to read and retune.
//

import Foundation

enum LevelCurve {
    /// (level, pairs) anchors. Value is held constant past the last anchor.
    private static let pairAnchors: [(level: Int, value: Int)] = [
        (1, 3), (5, 4), (10, 6), (25, 9), (50, 12)
    ]

    /// (level, seconds) anchors. Value is held constant past the last anchor.
    private static let secondAnchors: [(level: Int, value: Int)] = [
        (1, 90), (5, 85), (10, 75), (25, 60), (50, 45), (80, 35)
    ]

    /// (level, mistakes) anchors — the budget of failed matches before the
    /// level is lost. Tracks roughly `pairs + 2`: even perfect recall costs
    /// about N/2–N mismatches on an N-pair board, so a budget at or below the
    /// pair count would be effectively unwinnable.
    private static let failureAnchors: [(level: Int, value: Int)] = [
        (1, 4), (5, 6), (10, 8), (25, 11), (50, 14)
    ]

    /// Number of pairs of cards for a level. Non-decreasing as level increases; capped at 12.
    static func pairs(for level: Int) -> Int {
        interpolate(level: level, anchors: pairAnchors)
    }

    /// Time limit in seconds for a level. Non-increasing as level increases; floored at 35.
    static func seconds(for level: Int) -> Int {
        interpolate(level: level, anchors: secondAnchors)
    }

    /// How many failed matches a level allows before it's lost. Always greater
    /// than `pairs(for:)` so every level stays winnable; capped at 14.
    static func maxFailures(for level: Int) -> Int {
        interpolate(level: level, anchors: failureAnchors)
    }

    /// (level, frozen cards) steps — the first blocker. A frozen card needs one
    /// tap to crack before it can flip, so each one is a tap and a beat of the
    /// clock the player must plan around. Held at zero until the player has
    /// learnt the plain game, then stepped (not interpolated: half a frozen
    /// card means nothing) and capped at 4, a third of the largest board.
    ///
    /// PROTOTYPE TUNING: the first step sits at level 6 so the blocker is
    /// reachable in a minute on a fresh install. The intended release value is
    /// around level 20.
    private static let frozenCardSteps: [(level: Int, value: Int)] = [
        (1, 0), (6, 2), (25, 3), (50, 4)
    ]

    /// Frozen cards placed on a level's board. Non-decreasing; never more than
    /// `pairs(for:)`, since `MemoryGame.freezeCards` refuses to ice both cards
    /// of one pair.
    static func frozenCards(for level: Int) -> Int {
        min(step(level, frozenCardSteps), pairs(for: level))
    }

    /// (level, bomb cards) steps. A bomb card costs the clock (or a move) when
    /// it is part of a mismatch and pays a bonus when its pair is found, so it
    /// turns a blind flip into a bet. Arrives after ice, which only costs taps.
    private static let bombCardSteps: [(level: Int, value: Int)] = [
        (1, 0), (10, 1), (30, 2)
    ]

    /// (level, chained cards) steps. A chained card stays locked until the
    /// player has matched `chainLength` more pairs, so it forces the rest of
    /// the board to be played first and shapes the order of a clear.
    private static let chainedCardSteps: [(level: Int, value: Int)] = [
        (1, 0), (15, 1), (35, 2)
    ]

    /// Pairs a chained card waits for before its lock opens.
    static let chainLength = 2

    static func bombCards(for level: Int) -> Int {
        min(step(level, bombCardSteps), pairs(for: level))
    }

    static func chainedCards(for level: Int) -> Int {
        min(step(level, chainedCardSteps), pairs(for: level))
    }

    /// Moves levels come round every `movesLevelPeriod` levels, from the first
    /// one. Alternating the two objectives gives the climb a rhythm: a race,
    /// then a think, then a race.
    static let movesLevelPeriod = 4

    /// What the level asks for: beat the clock, or clear the board within a
    /// budget of attempts. Seasons follow the same rhythm as endless Levels.
    static func objective(for level: Int) -> LevelObjective {
        let clampedLevel = max(1, level)
        if clampedLevel >= movesLevelPeriod, clampedLevel % movesLevelPeriod == 0 {
            return .moves(movesBudget(for: level))
        }
        return .timed
    }

    /// Attempts a moves level allows. An attempt is a pair turned over, so a
    /// perfect clear spends exactly `pairs`; the slack is the mistake budget
    /// the timed version of the level would have had.
    static func movesBudget(for level: Int) -> Int {
        pairs(for: level) + maxFailures(for: level)
    }

    private static func interpolate(level: Int, anchors: [(level: Int, value: Int)]) -> Int {
        let clampedLevel = max(1, level)

        guard let first = anchors.first, let last = anchors.last else { return 0 }
        if clampedLevel <= first.level { return first.value }
        if clampedLevel >= last.level { return last.value }

        for index in 1..<anchors.count {
            let lo = anchors[index - 1]
            let hi = anchors[index]
            guard clampedLevel <= hi.level else { continue }

            let span = Double(hi.level - lo.level)
            let progress = Double(clampedLevel - lo.level) / span
            let value = Double(lo.value) + progress * Double(hi.value - lo.value)
            return Int(value.rounded())
        }
        return last.value
    }

    private static func step(_ level: Int, _ steps: [(level: Int, value: Int)]) -> Int {
        let clampedLevel = max(1, level)
        return steps.last { $0.level <= clampedLevel }?.value ?? 0
    }
}

/// How a level is won. Timed levels race a countdown with a mistake budget;
/// moves levels have no clock and a budget of attempts instead.
enum LevelObjective: Equatable {
    case timed
    case moves(Int)

    var isMoves: Bool {
        if case .moves = self { return true }
        return false
    }

}
