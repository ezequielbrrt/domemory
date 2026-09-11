//
//  LottieView.swift
//  DoMemory
//
//  A bundled Lottie animation, backed by `LottieAnimationView`.
//

import SwiftUI
import Lottie

/// Plays a bundled Lottie JSON animation.
///
/// Mirrors `RemoteImage`'s role for this module: it keeps the underlying
/// platform view (`LottieAnimationView`) out of call sites, which only need
/// the animation's name, how it should loop, and — for one-shot moments like
/// a win celebration — a way to know when it finished.
struct LottieView: UIViewRepresentable {
    let name: String
    var bundle: Bundle = .main
    var loopMode: LottieLoopMode = .playOnce
    var speed: CGFloat = 1
    var animationDidFinish: (() -> Void)? = nil

    func makeUIView(context: Context) -> LottieAnimationView {
        let view = LottieAnimationView(name: name, bundle: bundle)
        view.contentMode = .scaleAspectFit
        view.loopMode = loopMode
        view.animationSpeed = speed
        // Pausing offscreen (rather than the default of restarting) matters
        // for the star row: the modal's share sheet can cover it mid-pop, and
        // resuming from the same frame reads as a brief interruption instead
        // of the animation stuttering back to its start.
        view.backgroundBehavior = .pauseAndRestore
        view.play { finished in
            if finished {
                animationDidFinish?()
            }
        }
        return view
    }

    func updateUIView(_ uiView: LottieAnimationView, context: Context) {
        uiView.loopMode = loopMode
        uiView.animationSpeed = speed
    }
}
