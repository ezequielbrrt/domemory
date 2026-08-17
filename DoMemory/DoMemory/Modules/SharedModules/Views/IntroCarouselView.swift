//
//  IntroCarouselView.swift
//  DoMemory
//
//  The paged "big symbol + title + subtitle" onboarding carousel, shared by the
//  first-launch feature intro and the Levels mode intro. Owners supply the slides
//  and handle their own persistence and analytics.
//

import SwiftUI

struct IntroSlide {
    let symbol: String
    let color: Color
    let title: String
    let subtitle: String
}

struct IntroCarouselView: View {
    let slides: [IntroSlide]
    /// Title for the button on the last page — "Get Started" on first launch,
    /// "Got It" when the carousel is a reference the player opened deliberately.
    let finishTitle: String
    let onSkip: () -> Void
    let onFinish: () -> Void

    @State private var page = 0

    private var isLastPage: Bool { page == slides.count - 1 }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                HStack {
                    Spacer()
                    Button(Strings.introSkip, action: onSkip)
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
                    Text(isLastPage ? finishTitle : Strings.introNext)
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
    }

    private func advance() {
        if isLastPage {
            onFinish()
        } else {
            withAnimation { page += 1 }
        }
    }
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
    IntroCarouselView(
        slides: [
            IntroSlide(
                symbol: "trophy.fill",
                color: .primaryColor,
                title: "A Title",
                subtitle: "A subtitle explaining the feature in a sentence or two."
            )
        ],
        finishTitle: "Got It",
        onSkip: {},
        onFinish: {}
    )
}
