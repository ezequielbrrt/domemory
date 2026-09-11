//
//  Cardify.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 24/09/20.
//
import SwiftUI

struct Cardify: AnimatableModifier {
    var rotation: Double
    
    init(isFaceUp: Bool) {
        rotation = isFaceUp ? 0 : 180
    }
    
    var isFaceUp: Bool {
        rotation < 90
    }
    
    var animatableData: Double {
        get { return rotation }
        set { rotation = newValue }
    }
    
    func body(content: Content) -> some View {
        ZStack {
            Group {
                RoundedRectangle(cornerRadius: cornerRadius).fill(Color.primaryColor)
                RoundedRectangle(cornerRadius: cornerRadius).stroke(Color.primaryColor.opacity(0.85), lineWidth: edgeLineWidht)
                content
            }.opacity(isFaceUp ? 1 : 0)
                RoundedRectangle(cornerRadius: cornerRadius).fill(Color.primaryColor.opacity(0.35))
                    .opacity(isFaceUp ? 0 : 1)
            
        }
        .rotation3DEffect(Angle.degrees(rotation), axis: (0,1,0))
    }
    
    private let cornerRadius: CGFloat = 10.0
    private let edgeLineWidht: CGFloat = 3
}

extension View {
    func cardify(isFaceUp: Bool) -> some View {
        self.modifier(Cardify(isFaceUp: isFaceUp))
    }
}
