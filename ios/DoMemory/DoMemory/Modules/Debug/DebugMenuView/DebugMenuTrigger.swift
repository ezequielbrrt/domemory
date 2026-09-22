//
//  DebugMenuTrigger.swift
//  DoMemory
//
//  A debug-build-only hidden entry point for `DebugMenuView`: five taps within a
//  rolling window trigger it. A strict `.onTapGesture(count: 5)` requires all
//  five taps to land as one fast, tightly-grouped gesture, which is too
//  finicky for a real QA workflow — this instead resets the count whenever a
//  tap arrives too long after the previous one.
//

#if DEBUG
import SwiftUI

private struct DebugMenuTapTrigger: ViewModifier {
    let action: () -> Void

    @State private var tapCount = 0
    @State private var lastTapDate: Date?

    private static let requiredTaps = 5
    private static let maxGapBetweenTaps: TimeInterval = 1.5

    func body(content: Content) -> some View {
        content
            .contentShape(Rectangle())
            .onTapGesture {
                let now = Date()
                if let lastTapDate, now.timeIntervalSince(lastTapDate) > Self.maxGapBetweenTaps {
                    tapCount = 0
                }
                lastTapDate = now
                tapCount += 1

                if tapCount >= Self.requiredTaps {
                    tapCount = 0
                    lastTapDate = nil
                    action()
                }
            }
    }
}

extension View {
    /// Debug-only: fires `action` after five taps landing within 1.5s of
    /// each other. Compiled out entirely in Release builds — this modifier
    /// does not exist at all outside `#if DEBUG`, so every call site must
    /// share the same guard.
    func debugMenuTapTrigger(action: @escaping () -> Void) -> some View {
        modifier(DebugMenuTapTrigger(action: action))
    }
}
#endif
