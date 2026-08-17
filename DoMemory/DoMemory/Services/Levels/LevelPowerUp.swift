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
    /// Star price of skipping a level you're stuck on.
    static let skipLevelCost = 15
}
