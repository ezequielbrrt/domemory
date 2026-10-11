//
//  LevelPowerUp.swift
//  DoMemory
//
//  In-game assists bought with stars during Levels play. Costs live here and
//  nowhere else so the economy can be retuned in one place.
//

import Foundation

enum LevelPowerUp: String, CaseIterable, Identifiable {
    case extraTime
    case peek
    case freeze
    case revealPair

    var id: String { rawValue }

    /// Price in stars. Ordered as a power ladder: the more a power-up does,
    /// the more it costs. An average level clear pays ~2 stars.
    var cost: Int {
        switch self {
        case .extraTime: return 3
        case .peek: return 4
        case .freeze: return 5
        case .revealPair: return 6
        }
    }

    var systemImage: String {
        switch self {
        case .extraTime: return "goforward.15"
        case .peek: return "eye.fill"
        case .freeze: return "snowflake"
        case .revealPair: return "wand.and.stars"
        }
    }

    var title: String {
        switch self {
        case .extraTime: return Strings.powerUpExtraTime
        case .peek: return Strings.powerUpPeek
        case .freeze: return Strings.powerUpFreeze
        case .revealPair: return Strings.powerUpRevealPair
        }
    }

    /// The power-ups that make sense for a level's objective. A moves level
    /// has no clock, so time and Freeze have nothing to act on.
    static func available(for objective: LevelObjective) -> [LevelPowerUp] {
        switch objective {
        case .timed: return allCases
        case .moves: return [.peek, .revealPair]
        }
    }

    // MARK: - Tuning constants

    /// Seconds added to the clock by `.extraTime`.
    static let extraTimeSeconds = 15
    /// How long `.peek` keeps the board face up.
    static let peekDuration: TimeInterval = 1.5
    /// How long `.freeze` holds the countdown.
    static let freezeDuration: TimeInterval = 10
    /// Star price of one extra life, bought from an out-of-lives prompt.
    static let lifeCost = 10
    /// Star price of forgiving mistakes after busting the budget. Below a life,
    /// since it rescues the current attempt rather than granting a new one.
    static let forgiveCost = 8
    /// How many mistakes a rescue refunds, from an ad or from stars.
    static let forgiveAmount = 3
    /// Seconds the board is guaranteed to have left after a forgive rescue.
    /// Without a floor the rescue is worthless when the budget runs out late:
    /// at 0 seconds the countdown can't restart at all, and at 3 seconds the
    /// player loses again before they can use the refunded mistakes.
    static let forgiveMinimumSeconds = 15
    /// Star price of skipping a level you're stuck on.
    static let skipLevelCost = 15
}

/// What a match streak earns. Power-ups bought with stars are a menu decision;
/// a power-up *charged* by playing well is a gameplay moment, which is the
/// point. Each milestone hands out one free use of the power-up, as a power
/// ladder that mirrors the price list: a short run earns the cheap assist, a
/// long one the expensive one. The streak itself keeps counting past the last
/// milestone, and a 12-pair board run perfectly reaches 12.
enum StreakReward {
    /// (streak length, power-up charged) in the order they are reached.
    static let milestones: [(streak: Int, powerUp: LevelPowerUp)] = [
        (3, .peek), (5, .freeze), (7, .revealPair)
    ]

    /// The power-up charged by reaching exactly this streak, if any.
    static func powerUp(forStreak streak: Int) -> LevelPowerUp? {
        milestones.first { $0.streak == streak }?.powerUp
    }

    /// (streak length, power-up charged) on a moves level, where Freeze would
    /// charge nothing worth having. The two assists that still apply alternate.
    static let movesMilestones: [(streak: Int, powerUp: LevelPowerUp)] = [
        (3, .peek), (5, .revealPair), (7, .peek)
    ]

    static func powerUp(forStreak streak: Int, objective: LevelObjective) -> LevelPowerUp? {
        switch objective {
        case .timed: return powerUp(forStreak: streak)
        case .moves: return movesMilestones.first { $0.streak == streak }?.powerUp
        }
    }

    /// The next milestone still ahead of this streak; nil past the last one.
    static func nextMilestone(after streak: Int) -> Int? {
        milestones.first { $0.streak > streak }?.streak
    }

    /// The last milestone, which is also how many pips the combo meter draws.
    static var finalMilestone: Int { milestones.last?.streak ?? 0 }
}
