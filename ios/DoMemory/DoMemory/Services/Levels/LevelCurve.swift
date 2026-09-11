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
}
