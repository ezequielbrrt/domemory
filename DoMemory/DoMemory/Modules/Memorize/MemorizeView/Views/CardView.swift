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
        if card.isFaceUp || !card.isMatched {
            ZStack {
                Group {
                    if shouldShowPie {
                        if card.isConsumingBonusTime {
                            Pie(startAngle: Angle.degrees(0-90),
                                endAngle: Angle.degrees(-animatedBonusRemaining*360-90),
                                clockwise: true)
                                .onAppear {
                                    self.startBonusAnimation()
                                }
                        } else {
                            Pie(startAngle: Angle.degrees(0-90),
                                endAngle: Angle.degrees(-card.bonusRemaining*360-90),
                                clockwise: true)
                                
                        }
                    }
                }.padding(5).opacity(0.4).transition(.identity)
                
                Text(self.card.content)
                    .minimumScaleFactor(0.0001)
                    .font(Font.system(size: fontSize(for: size)))
                .rotationEffect(Angle(degrees: card.isMatched ? 360 : 0))
                    .animation(card.isMatched ? Animation.linear(duration: 1).repeatForever(autoreverses: false) : .default)
            }.cardify(isFaceUp: card.isFaceUp)
                .transition(AnyTransition.scale)
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
