//
//  LivesRow.swift
//  DoMemory
//
//  Row of heart glyphs showing remaining Levels-mode lives. Reused by the
//  Levels map header and the out-of-lives prompts (LoseModal, OutOfLivesModal).
//

import SwiftUI

/// A one-shot effect the row plays over a single heart: the heart that was
/// just lost breaking apart, or the one just refilled bursting back in.
enum LivesRowEffect: Equatable {
    case lost(slot: Int)
    case gained(slot: Int)

    /// The effect a change in the remaining count deserves, if any. A drop
    /// breaks the first now-empty heart; a rise bursts the last now-filled
    /// one. A day-reset refill from 0 to 4 animates a single heart rather
    /// than four — one burst is a nicety, four is a fireworks show.
    static func forTransition(from previous: Int, to current: Int) -> LivesRowEffect? {
        if current < previous { return .lost(slot: current) }
        if current > previous { return .gained(slot: current - 1) }
        return nil
    }
}

struct LivesRow: View {
    var remaining: Int
    var total: Int = LevelLivesService.maxLives
    var iconSize: CGFloat = 16
    /// Plays once over the named slot. The row draws its real final state
    /// underneath — the clips end fully transparent — so a reduce-motion
    /// player, who never sees the clip, sees exactly the same hearts.
    var effect: LivesRowEffect? = nil
    var onEffectFinished: (() -> Void)? = nil
    /// A still-filled heart that a pending loss will take unless the player
    /// rescues the game. It pulses rather than breaking, because it hasn't
    /// gone yet.
    var atRiskSlot: Int? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The heart briefly scaled up by a refill, so the burst has a heart
    /// physically popping back in at its centre rather than just decorating one.
    @State private var bumpedSlot: Int? = nil

    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<total, id: \.self) { index in
                heart(at: index)
                    .scaleEffect(bumpedSlot == index ? 1.35 : 1)
                    .animation(.spring(response: 0.3, dampingFraction: 0.5), value: bumpedSlot)
                    .overlay { overlay(for: index) }
            }
        }
        .accessibilityElement()
        .accessibilityLabel(Strings.livesRemainingFormat(remaining, total))
    }

    @ViewBuilder
    private func heart(at index: Int) -> some View {
        let glyph = Image(systemName: index < remaining ? "heart.fill" : "heart")
            .font(.system(size: iconSize, weight: .semibold))
            .foregroundStyle(index < remaining ? Color.secundaryColor : Color.textMuted.opacity(0.3))

        if index == atRiskSlot, index < remaining {
            if reduceMotion {
                glyph.opacity(0.45)
            } else {
                glyph.phaseAnimator([false, true]) { content, dimmed in
                    content
                        .opacity(dimmed ? 0.3 : 1)
                        .scaleEffect(dimmed ? 0.85 : 1)
                } animation: { _ in
                    .easeInOut(duration: 0.7)
                }
            }
        } else {
            glyph
        }
    }

    @ViewBuilder
    private func overlay(for index: Int) -> some View {
        if !reduceMotion, let effect {
            switch effect {
            case .lost(let slot) where slot == index:
                // The clip's heart spans 60% of its canvas; 1.7x the glyph
                // size lands it on top of the SF Symbol before it splits.
                LottieView(name: "heart-break", tint: Color.secundaryColor) { onEffectFinished?() }
                    .frame(width: iconSize * 1.7, height: iconSize * 1.7)
                    .allowsHitTesting(false)
            case .gained(let slot) where slot == index:
                LottieView(name: "heart-refill", tint: Color.secundaryColor) { onEffectFinished?() }
                    .frame(width: iconSize * 3, height: iconSize * 3)
                    .allowsHitTesting(false)
                    .task {
                        bumpedSlot = index
                        try? await Task.sleep(nanoseconds: 250_000_000)
                        bumpedSlot = nil
                    }
            default:
                EmptyView()
            }
        }
    }
}

#Preview {
    VStack(spacing: 16) {
        LivesRow(remaining: 4)
        LivesRow(remaining: 2)
        LivesRow(remaining: 0)
        LivesRow(remaining: 2, effect: .lost(slot: 2))
        LivesRow(remaining: 3, effect: .gained(slot: 2))
        LivesRow(remaining: 3, atRiskSlot: 2)
    }
    .padding()
}
