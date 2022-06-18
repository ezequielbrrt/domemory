//
//  Blur.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 11/02/21.
//

import SwiftUI

struct Blur: UIViewRepresentable {
    let style: UIBlurEffect.Style = .systemThinMaterialLight

    func makeUIView(context: Context) -> UIVisualEffectView {
        return UIVisualEffectView(effect: UIBlurEffect(style: style))
    }

    func updateUIView(_ uiView: UIVisualEffectView, context: Context) {
        uiView.effect = UIBlurEffect(style: style)
    }
}
