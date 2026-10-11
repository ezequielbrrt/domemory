//
//  CardView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 24/09/20.
//

import SwiftUI

struct CardView: View {
    var card: MemoryGame<String>.Card
    var shouldShowPie: Bool
    /// Shown face up by a cascade flash without the model turning it.
    var isRevealed: Bool = false
    /// The colour slot of the player's pin on this card, if any.
    var pinSlot: Int? = nil
    /// Juice for a pair that just resolved on this card.
    var effect: MatchEffect? = nil

    /// One colour per pin slot, so three marks stay tellable apart.
    static let pinColors: [Color] = [Color.hardAmber, Color.secundaryColor, Color.freezeBlue]

    private var showsFace: Bool { card.isFaceUp || isRevealed }
    
    var body: some View {
        GeometryReader(content: { geometry in
            self.body(for: geometry.size)
        })
        
    }
    
    @ViewBuilder
    private func body(for size: CGSize) -> some View {
        if !card.isMatched || card.isFaceUp {
            ZStack {
//                Group {
//                    if shouldShowPie {
//                       
//                        if card.isConsumingBonusTime {
//                            Pie(startAngle: Angle.degrees(0-90),
//                                endAngle: Angle.degrees(-animatedBonusRemaining*360-90),
//                                clockwise: true)
//                                .onAppear {
//                                    self.startBonusAnimation()
//                                }
//                        } else {
//                            Pie(startAngle: Angle.degrees(0-90),
//                                endAngle: Angle.degrees(-card.bonusRemaining*360-90),
//                                clockwise: true)
//
//                        }
//                    }
//                }.padding(5).opacity(0.4).transition(.identity)
                
                Text(self.card.content)
                    .minimumScaleFactor(0.0001)
                    .font(Font.system(size: fontSize(for: size)))
            }.cardify(isFaceUp: showsFace)
                .overlay {
                    if card.isBomb && !showsFace {
                        bombMark(for: size)
                            .transition(.opacity)
                    }
                }
                .overlay {
                    if card.isLocked && !showsFace {
                        chainSheet(for: size)
                            .transition(.scale(scale: 1.1).combined(with: .opacity))
                    }
                }
                .overlay {
                    if card.isFrozen {
                        iceSheet(for: size)
                            .transition(.scale(scale: 1.15).combined(with: .opacity))
                    }
                }
                .overlay(alignment: .topLeading) {
                    if let pinSlot, !showsFace {
                        Circle()
                            .fill(Self.pinColors[pinSlot % Self.pinColors.count])
                            .frame(width: max(10, size.width * 0.18), height: max(10, size.width * 0.18))
                            .overlay(Circle().stroke(Color.white.opacity(0.9), lineWidth: 2))
                            .shadow(color: Color.shadowColor, radius: 2, x: 0, y: 1)
                            .padding(6)
                            .transition(.scale.combined(with: .opacity))
                    }
                }
                // Punch: the pair squashes out and settles the instant it
                // resolves, so the match is felt on the cards, not only heard.
                .scaleEffect(punch ? 1.14 : 1)
                .onChange(of: card.isMatched) { _, isMatched in
                    guard isMatched, !reduceMotion else { return }
                    withAnimation(.spring(duration: 0.22, bounce: 0.65)) { punch = true }
                    Task { @MainActor in
                        try? await Task.sleep(for: .seconds(0.2))
                        withAnimation(.spring(duration: 0.3, bounce: 0.3)) { punch = false }
                    }
                }
                .overlay {
                    if let effect, effect.tier > 0, !reduceMotion {
                        let scale = 1.2 + 0.35 * CGFloat(effect.tier)
                        LottieView(name: "star-sparkle", tint: Self.burstColor(tier: effect.tier))
                            .id(effect.id)
                            .frame(width: size.width * scale, height: size.width * scale)
                            .allowsHitTesting(false)
                            .accessibilityHidden(true)
                    }
                }
                .overlay {
                    if let effect, let text = effect.text {
                        FloatingNumber(text: text, color: effect.isPenalty ? Color.secundaryColor : Self.burstColor(tier: effect.tier), rise: size.height * 0.7)
                            .id(effect.id)
                            .allowsHitTesting(false)
                            .accessibilityHidden(true)
                    }
                }
                .animation(.spring(duration: 0.25, bounce: 0.4), value: pinSlot)
                .animation(.easeInOut(duration: 0.25), value: isRevealed)
                .animation(.spring(duration: 0.3, bounce: 0.3), value: card.lockedForMatches)
                .overlay {
                    if showShatter {
                        LottieView(name: "freeze-thaw", tint: Color.freezeBlue) { showShatter = false }
                            .frame(width: size.width * 1.4, height: size.width * 1.4)
                            .allowsHitTesting(false)
                            .accessibilityHidden(true)
                    }
                }
                .onChange(of: card.isFrozen) { wasFrozen, isFrozen in
                    if wasFrozen && !isFrozen && !reduceMotion { showShatter = true }
                }
                .animation(.spring(duration: 0.3, bounce: 0.3), value: card.isFrozen)
                .transition(.scale)
        }

    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var punch = false

    /// The burst takes the meter's colour for the run it belongs to.
    static func burstColor(tier: Int) -> Color {
        switch tier {
        case ..<2: Color.primaryColor
        case 2: Color.hardAmber
        default: Color.secundaryColor
        }
    }
    /// The ice-shatter burst over a card the moment its ice cracks. Reuses the
    /// timer chip's thaw clip, tinted the same blue, so ice reads as one thing
    /// wherever it appears.
    @State private var showShatter = false

    /// A bomb is announced on the back, so flipping it is a bet the player
    /// takes knowingly: a warm tint and the fuse, nothing covering the card.
    private func bombMark(for size: CGSize) -> some View {
        RoundedRectangle(cornerRadius: 10)
            .fill(Color.secundaryColor.opacity(0.28))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(Color.secundaryColor.opacity(0.8), lineWidth: 2)
            )
            .overlay(
                Text("💣")
                    .font(.system(size: min(size.width, size.height) * 0.4))
            )
    }

    /// A chain over a face-down card: dark, a padlock, and the number of
    /// pairs that still have to be matched before it opens.
    private func chainSheet(for size: CGSize) -> some View {
        let side = min(size.width, size.height)
        return RoundedRectangle(cornerRadius: 10)
            .fill(Color.textPrimary.opacity(0.55))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(Color.textPrimary.opacity(0.7), lineWidth: 2)
            )
            .overlay(
                VStack(spacing: 2) {
                    Image(systemName: "lock.fill")
                        .font(.system(size: side * 0.34, weight: .semibold))
                    Text("\(card.lockedForMatches)")
                        .font(.system(size: side * 0.22, weight: .heavy, design: .rounded))
                        .contentTransition(.numericText())
                }
                .foregroundStyle(Color.white.opacity(0.95))
            )
    }

    /// A translucent sheet of ice over a face-down card: frosted blue, a
    /// lighter rim, and a snowflake large enough to read at the smallest card
    /// size. It hides nothing the player could otherwise see — the card is
    /// face down under it — so the sheet is the blocker's whole signal.
    private func iceSheet(for size: CGSize) -> some View {
        RoundedRectangle(cornerRadius: 10)
            .fill(
                LinearGradient(
                    colors: [Color.freezeBlue.opacity(0.55), Color.freezeBlue.opacity(0.85)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(Color.white.opacity(0.7), lineWidth: 2)
            )
            .overlay(
                Image(systemName: "snowflake")
                    .font(.system(size: min(size.width, size.height) * 0.42, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.95))
                    .shadow(color: Color.freezeBlue.opacity(0.6), radius: 4)
            )
    }
    
    // MARK: - Drawing constants
    
    private func fontSize(for size: CGSize) -> CGFloat {
        if self.card.content.count > 1 {
            return 20
        }
        
        return min(size.width, size.height) * 0.7
    }
    
    @State private var animatedBonusRemaining: Double = 0
    
    private func startBonusAnimation() {    
        animatedBonusRemaining = card.bonusRemaining
        withAnimation(.linear(duration: card.bonusTimeRemaining)) {
            animatedBonusRemaining = 0
        }
    }
}

/// A score-style number that pops in over a card and drifts up as it fades,
/// the way a cleared tile reports its points. Reduce Motion shows it still.
struct FloatingNumber: View {
    let text: String
    let color: Color
    let rise: CGFloat
    /// Shown at rest, with no drift: for previews and renders.
    var holdsStill = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var floated = false

    private var isStill: Bool { reduceMotion || holdsStill }

    var body: some View {
        Text(text)
            .font(.system(size: 22, weight: .black, design: .rounded))
            .foregroundStyle(color)
            // A solid white halo keeps the number legible over any emoji,
            // including one in the number's own colour.
            .shadow(color: Color.white, radius: 1)
            .shadow(color: Color.white, radius: 2)
            .shadow(color: Color.white.opacity(0.8), radius: 4)
            .shadow(color: color.opacity(0.5), radius: 8)
            .lineLimit(1)
            .fixedSize()
            .scaleEffect(floated || isStill ? 1 : 0.4)
            .offset(y: floated && !isStill ? -rise : 0)
            .opacity(floated && !isStill ? 0 : 1)
            .onAppear {
                guard !isStill else { return }
                withAnimation(.spring(duration: 0.25, bounce: 0.5)) { floated = false }
                withAnimation(.easeIn(duration: 0.85).delay(0.1)) { floated = true }
            }
    }
}

extension View {
    /// VoiceOver for one board card: what the player would see, not the drawn
    /// layers. A face-down card reads "Face-down card" and is a button. Its emoji
    /// is still in the view tree at zero opacity (`Cardify`) and must never be
    /// read out. A face-up card reads its emoji, with "Face up" or "Matched". A
    /// matched card that has left the board draws nothing and is hidden. The
    /// explicit action is the tap itself, so a VoiceOver double tap flips the
    /// card without relying on the tap gesture being exposed. Matches Android's
    /// `CardView` semantics.
    func cardAccessibility(
        _ card: MemoryGame<String>.Card,
        isPinned: Bool = false,
        onActivate: @escaping () -> Void,
        onTogglePin: (() -> Void)? = nil
    ) -> some View {
        self
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(
                card.isFrozen ? Text(Strings.cardFrozen)
                    : card.isLocked ? Text(Strings.cardLockedFormat(card.lockedForMatches))
                    : card.isFaceUp ? Text(verbatim: card.content)
                    : card.isBomb ? Text(Strings.cardBomb)
                    : Text(Strings.cardFaceDown)
            )
            .accessibilityValue(
                card.isMatched ? Strings.cardMatched
                    : card.isFaceUp ? Strings.cardFaceUp
                    : isPinned ? Strings.cardPinned
                    : ""
            )
            .accessibilityAddTraits(card.isFaceUp ? [] : .isButton)
            .accessibilityAction { onActivate() }
            .accessibilityAction(named: Text(isPinned ? Strings.unpinCard : Strings.pinCard)) {
                onTogglePin?()
            }
            .accessibilityHidden(card.isMatched && !card.isFaceUp)
    }
}
