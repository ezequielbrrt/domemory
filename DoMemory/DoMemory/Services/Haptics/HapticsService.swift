//
//  HapticsService.swift
//  DoMemory
//
//  Single entry point for Taptic Engine feedback. Call sites name the *moment*
//  (`.match`, `.success`) rather than a generator style, so the whole feel of
//  the app can be retuned from the one table below instead of chasing sixty
//  scattered `UIImpactFeedbackGenerator(style:)` calls.
//

import Foundation
#if os(iOS)
import UIKit
#endif

@MainActor
final class HapticsService {
    static let shared = HapticsService()

    /// What just happened, in the player's terms. Adding a case here is the
    /// only place a new haptic is designed.
    enum Intent: String, CaseIterable {
        case tap        // any button or row
        case select     // picker change, level tile, turn handover
        case cardFlip   // first card of a pair turned over
        case match      // pair resolved
        case mismatch   // pair missed
        case success    // level cleared, game won
        case failure    // level lost, out of lives
        case warning    // action refused, purchase failed
        case reward     // stars earned, life granted, ad rewarded
    }

    /// The concrete UIKit feedback each intent produces. Split out from `fire`
    /// so tests can assert the mapping without a Taptic Engine.
    enum Feedback: Equatable {
        case impact(UIImpactFeedbackGenerator.FeedbackStyle)
        case selection
        case notification(UINotificationFeedbackGenerator.FeedbackType)
    }

    private let defaults: UserDefaults
    /// Test seam. `nil` in the app, where `emit` drives the real generators.
    private let sink: ((Feedback) -> Void)?

    #if os(iOS)
    // Held for the lifetime of the service rather than constructed per call:
    // a cold Taptic Engine adds latency you can feel on the first tap, which
    // is exactly the tap that matters when a card flips.
    private lazy var impactGenerators: [UIImpactFeedbackGenerator.FeedbackStyle: UIImpactFeedbackGenerator] = [
        .light: UIImpactFeedbackGenerator(style: .light),
        .medium: UIImpactFeedbackGenerator(style: .medium),
        .heavy: UIImpactFeedbackGenerator(style: .heavy),
        .soft: UIImpactFeedbackGenerator(style: .soft),
        .rigid: UIImpactFeedbackGenerator(style: .rigid)
    ]
    private lazy var selectionGenerator = UISelectionFeedbackGenerator()
    private lazy var notificationGenerator = UINotificationFeedbackGenerator()
    #endif

    init(defaults: UserDefaults = .standard, sink: ((Feedback) -> Void)? = nil) {
        self.defaults = defaults
        self.sink = sink
    }

    /// Haptics default to **on**. Read through `object(forKey:)` rather than
    /// `bool(forKey:)`, which reports `false` for a key that was never written
    /// and would ship the whole feature silently disabled on a fresh install.
    var isEnabled: Bool {
        defaults.object(forKey: UserDefaultsKeys.hapticsEnabled) as? Bool ?? true
    }

    static func feedback(for intent: Intent) -> Feedback {
        switch intent {
        case .tap:      return .impact(.light)
        case .select:   return .selection
        case .cardFlip: return .impact(.soft)
        case .match:    return .impact(.medium)
        case .mismatch: return .impact(.rigid)
        case .success:  return .notification(.success)
        case .failure:  return .notification(.error)
        case .warning:  return .notification(.warning)
        case .reward:   return .impact(.heavy)
        }
    }

    /// The single gate. Every call site goes through here, so no caller has to
    /// remember to check the setting.
    func fire(_ intent: Intent) {
        guard isEnabled else { return }
        let feedback = Self.feedback(for: intent)

        if let sink {
            sink(feedback)
            return
        }

        #if os(iOS)
        switch feedback {
        case .impact(let style):
            impactGenerators[style]?.impactOccurred()
        case .selection:
            selectionGenerator.selectionChanged()
        case .notification(let type):
            notificationGenerator.notificationOccurred(type)
        }
        #endif
    }

    /// Warms the engine for a feedback that is about to happen — a card the
    /// player is mid-tap on, or the first flip of a new board. Never emits.
    func prepare(for intent: Intent) {
        guard isEnabled else { return }
        guard sink == nil else { return }

        #if os(iOS)
        switch Self.feedback(for: intent) {
        case .impact(let style):
            impactGenerators[style]?.prepare()
        case .selection:
            selectionGenerator.prepare()
        case .notification:
            notificationGenerator.prepare()
        }
        #endif
    }
}

#if canImport(SwiftUI)
import SwiftUI

extension View {
    /// Tap feedback for a control whose action lives in the view rather than a
    /// view model. Runs alongside the button's own gesture, so it changes
    /// neither layout nor styling, and a `.disabled` control stays silent
    /// because the modifier sits inside the disabled subtree.
    func hapticTap(_ intent: HapticsService.Intent = .tap) -> some View {
        simultaneousGesture(
            TapGesture().onEnded { HapticsService.shared.fire(intent) }
        )
    }
}
#endif
