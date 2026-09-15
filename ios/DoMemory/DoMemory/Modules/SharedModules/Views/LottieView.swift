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
///
/// The JSON lives once, in `assets/lottie/` at the repository root, reached
/// through the `SupportingFiles/Lottie` symlink; Android bundles the very same
/// files, so a clip that exists here exists there with identical timing.
///
/// `tint` recolours every fill and stroke the clip's author named `tint`
/// (see `assets/lottie/generate_animations.py`) to the given palette colour,
/// resolved for the current appearance. Clips authored in several colours,
/// like the confetti, simply pass none.
struct LottieView: UIViewRepresentable {
    let name: String
    var bundle: Bundle = .main
    var loopMode: LottieLoopMode = .playOnce
    var speed: CGFloat = 1
    var tint: Color? = nil
    var animationDidFinish: (() -> Void)? = nil

    @Environment(\.colorScheme) private var colorScheme

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
        applyTint(to: view)
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
        // Re-resolved on every update so a light/dark switch mid-clip retints
        // rather than leaving the light colour on a dark surface.
        applyTint(to: uiView)
    }

    /// The keypath every tintable clip agrees on: any shape named `tint`, at
    /// any depth, and its `Color` property (fills and strokes both have one).
    static let tintKeypath = AnimationKeypath(keypath: "**.tint.Color")

    private func applyTint(to view: LottieAnimationView) {
        guard let tint else { return }
        let style: UIUserInterfaceStyle = colorScheme == .dark ? .dark : .light
        let resolved = UIColor(tint).resolvedColor(with: UITraitCollection(userInterfaceStyle: style))
        view.setValueProvider(ColorValueProvider(resolved.lottieColorValue), keypath: Self.tintKeypath)
    }
}
