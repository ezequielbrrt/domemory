//
//  LevelsIntroView.swift
//  DoMemory
//
//  Explains Levels mode — progression, stars, daily lives, and the mistake
//  budget — the first time the tab is opened, and any time the player taps the
//  info button on the map.
//

import SwiftUI

struct LevelsIntroView: View {
    /// `"auto"` for the one-shot first showing, `"info_button"` when reopened.
    let source: String
    let onDismiss: () -> Void

    private let gate = LevelsIntroGate()

    private var slides: [IntroSlide] {
        [
            IntroSlide(
                symbol: "trophy.fill",
                color: .easyGreen,
                title: Strings.levelsIntroProgressTitle,
                subtitle: Strings.levelsIntroProgressSubtitle
            ),
            IntroSlide(
                symbol: "star.fill",
                color: .hardAmber,
                title: Strings.levelsIntroStarsTitle,
                subtitle: Strings.levelsIntroStarsSubtitle
            ),
            IntroSlide(
                symbol: "heart.fill",
                color: .secundaryColor,
                title: Strings.levelsIntroLivesTitle,
                subtitle: Strings.levelsIntroLivesSubtitle(LevelLivesService.maxLives)
            ),
            // Neutral indigo rather than the success green — a green ✗ reads as
            // "you passed", which is the opposite of what this slide teaches.
            IntroSlide(
                symbol: "xmark.circle.fill",
                color: .primaryColor,
                title: Strings.levelsIntroMistakesTitle,
                subtitle: Strings.levelsIntroMistakesSubtitle
            )
        ]
    }

    var body: some View {
        IntroCarouselView(
            slides: slides,
            finishTitle: Strings.levelsIntroDone,
            onSkip: {
                AnalyticsService.log(.levelsIntroSkipped)
                finish()
            },
            onFinish: {
                AnalyticsService.log(.levelsIntroCompleted)
                finish()
            }
        )
        .onAppear {
            AnalyticsService.log(.screenView(name: "levels_intro", screenClass: "LevelsIntroView"))
            AnalyticsService.log(.levelsIntroShown(source: source))
        }
    }

    /// Persisted on dismissal rather than on appearance, so a kill mid-intro
    /// leaves the player eligible to see it again.
    private func finish() {
        gate.markSeen()
        onDismiss()
    }
}

#Preview {
    LevelsIntroView(source: "preview", onDismiss: {})
}
