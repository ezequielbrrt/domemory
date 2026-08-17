//
//  FeatureIntroView.swift
//  DoMemory
//

import SwiftUI

struct FeatureIntroView: View {
    let onFinish: () -> Void

    private let slides: [IntroSlide] = [
        IntroSlide(
            symbol: "person.2.fill",
            color: .primaryColor,
            title: Strings.introMultiplayerTitle,
            subtitle: Strings.introMultiplayerSubtitle
        ),
        IntroSlide(
            symbol: "square.grid.2x2.fill",
            color: .easyGreen,
            title: Strings.introCustomTitle,
            subtitle: Strings.introCustomSubtitle
        ),
        IntroSlide(
            symbol: "flame.fill",
            color: .hardAmber,
            title: Strings.introDailyTitle,
            subtitle: Strings.introDailySubtitle
        )
    ]

    var body: some View {
        IntroCarouselView(
            slides: slides,
            finishTitle: Strings.introGetStarted,
            onSkip: {
                AnalyticsService.log(.onboardingIntroSkipped)
                finish()
            },
            onFinish: {
                AnalyticsService.log(.onboardingIntroCompleted)
                finish()
            }
        )
        .onAppear {
            AnalyticsService.log(.screenView(name: "onboarding_intro", screenClass: "FeatureIntroView"))
        }
    }

    private func finish() {
        UserDefaults.standard.set(true, forKey: UserDefaultsKeys.onboardingIntroShown)
        onFinish()
    }
}

#Preview {
    FeatureIntroView(onFinish: {})
}
