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
            }.cardify(isFaceUp: card.isFaceUp)
                .transition(.scale)
        }
        
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

extension View {
    /// VoiceOver for one board card: what the player would see, not the drawn
    /// layers. A face-down card reads "Face-down card" and is a button. Its emoji
    /// is still in the view tree at zero opacity (`Cardify`) and must never be
    /// read out. A face-up card reads its emoji, with "Face up" or "Matched". A
    /// matched card that has left the board draws nothing and is hidden. The
    /// explicit action is the tap itself, so a VoiceOver double tap flips the
    /// card without relying on the tap gesture being exposed. Matches Android's
    /// `CardView` semantics.
    func cardAccessibility(_ card: MemoryGame<String>.Card, onActivate: @escaping () -> Void) -> some View {
        self
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(card.isFaceUp ? Text(verbatim: card.content) : Text(Strings.cardFaceDown))
            .accessibilityValue(card.isMatched ? Strings.cardMatched : card.isFaceUp ? Strings.cardFaceUp : "")
            .accessibilityAddTraits(card.isFaceUp ? [] : .isButton)
            .accessibilityAction { onActivate() }
            .accessibilityHidden(card.isMatched && !card.isFaceUp)
    }
}
