//
//  FeatureIntroView.swift
//  DoMemory
//

import SwiftUI

struct FeatureIntroView: View {
    let onFinish: () -> Void

    @State private var page = 0

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

    private var isLastPage: Bool { page == slides.count - 1 }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                HStack {
                    Spacer()
                    Button(Strings.introSkip) {
                        AnalyticsService.log(.onboardingIntroSkipped)
                        finish()
                    }
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundStyle(Color.textMuted)
                }
                .padding(.horizontal, 24)
                .padding(.top, 16)

                TabView(selection: $page) {
                    ForEach(Array(slides.enumerated()), id: \.offset) { index, slide in
                        IntroSlideView(slide: slide).tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .always))
                .indexViewStyle(.page(backgroundDisplayMode: .always))

                Button(action: advance) {
                    Text(isLastPage ? Strings.introGetStarted : Strings.introNext)
                        .font(.system(size: 17, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Capsule().fill(Color.primaryColor))
                        .shadow(color: Color.primaryColor.opacity(0.3), radius: 14, x: 0, y: 4)
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 24)
                .padding(.bottom, 32)
            }
        }
        .onAppear {
            AnalyticsService.log(.screenView(name: "onboarding_intro", screenClass: "FeatureIntroView"))
        }
    }

    private func advance() {
        if isLastPage {
            AnalyticsService.log(.onboardingIntroCompleted)
            finish()
        } else {
            withAnimation { page += 1 }
        }
    }

    private func finish() {
        UserDefaults.standard.set(true, forKey: UserDefaultsKeys.onboardingIntroShown)
        onFinish()
    }
}

private struct IntroSlide {
    let symbol: String
    let color: Color
    let title: String
    let subtitle: String
}

private struct IntroSlideView: View {
    let slide: IntroSlide

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            ZStack {
                Circle()
                    .fill(slide.color.opacity(0.15))
                    .frame(width: 140, height: 140)
                Image(systemName: slide.symbol)
                    .font(.system(size: 60, weight: .bold))
                    .foregroundStyle(slide.color)
            }

            VStack(spacing: 12) {
                Text(slide.title)
                    .font(.system(size: 26, weight: .heavy, design: .rounded))
                    .foregroundStyle(Color.textPrimary)
                    .multilineTextAlignment(.center)

                Text(slide.subtitle)
                    .font(.system(size: 16, weight: .medium, design: .rounded))
                    .foregroundStyle(Color.textMuted)
                    .multilineTextAlignment(.center)
                    .lineSpacing(3)
                    .padding(.horizontal, 32)
            }

            Spacer()
            Spacer()
        }
        .padding(.horizontal, 16)
    }
}

#Preview {
    FeatureIntroView(onFinish: {})
}
