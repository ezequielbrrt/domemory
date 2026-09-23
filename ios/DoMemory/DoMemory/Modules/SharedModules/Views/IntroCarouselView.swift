//
//  IntroCarouselView.swift
//  DoMemory
//
//  The paged onboarding carousel, shared by the first-launch feature intro and the
//  Levels mode intro. Each slide shows either a full-width illustration or a big
//  tinted symbol, above a title and subtitle. Owners supply the slides and handle
//  their own persistence and analytics.
//

import SwiftUI

struct IntroSlide {
    let symbol: String
    let color: Color
    let title: String
    let subtitle: String
    /// Asset-catalog name of a full-width illustration, with its light and dark
    /// variants inside the image set. When nil the slide shows `symbol` instead.
    ///
    /// Artwork contract: 2:3 portrait, scene within the top ~48%, the rest a flat
    /// fill in the app background colour, which the title and subtitle overlap.
    var illustration: String? = nil
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
                    Button(Strings.introSkip) { HapticsService.shared.fire(.tap); onSkip() }
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
                .readableWidth(Layout.modalWidth)
                .padding(.bottom, 32)
            }
        }
    }

    private func advance() {
        if isLastPage {
            HapticsService.shared.fire(.tap)
            onFinish()
        } else {
            HapticsService.shared.fire(.select)
            withAnimation { page += 1 }
        }
    }
}

private struct IntroSlideView: View {
    let slide: IntroSlide

    var body: some View {
        if let name = slide.illustration, let artwork = UIImage(named: name) {
            IllustratedSlideView(slide: slide, imageName: name, artworkSize: artwork.size)
        } else {
            symbolSlide
        }
    }

    private var symbolSlide: some View {
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

            IntroSlideText(slide: slide)

            Spacer()
            Spacer()
        }
        .padding(.horizontal, 16)
    }
}

private struct IllustratedSlideView: View {
    let slide: IntroSlide
    let imageName: String
    let artworkSize: CGSize

    /// Fraction of the artwork's height the scene occupies, card shadows
    /// included; the text starts below it.
    private static let sceneFraction: CGFloat = 0.48
    /// Most of the page the scene may take, so iPad and short iPhones keep room
    /// for the text above the page dots.
    private static let maxSceneShareOfPage: CGFloat = 0.55

    var body: some View {
        GeometryReader { geo in
            let aspect = artworkSize.height / artworkSize.width
            let width = min(
                geo.size.width,
                geo.size.height * Self.maxSceneShareOfPage / (Self.sceneFraction * aspect)
            )
            let height = width * aspect

            // Only the scene takes up layout space, so the scene and text centre
            // together like a symbol slide; the artwork's empty lower part hangs
            // behind the text.
            VStack(spacing: 16) {
                Spacer(minLength: 0)

                Color.clear
                    .frame(height: height * Self.sceneFraction)
                    .overlay(alignment: .top) {
                        Image(imageName)
                            .resizable()
                            .scaledToFit()
                            .frame(width: width, height: height)
                            .mask { edgeFade(isInset: width < geo.size.width) }
                            .accessibilityHidden(true)
                    }

                IntroSlideText(slide: slide)
                    .padding(.horizontal, 16)

                Spacer(minLength: 0)
                Spacer(minLength: 0)
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }

    /// Fades the artwork into the background: a short fade at the top, a long
    /// one at the bottom (hiding any mismatch with the app background), and side
    /// fades only when the image is narrower than the page, as on iPad.
    private func edgeFade(isInset: Bool) -> some View {
        let vertical = LinearGradient(
            stops: [
                .init(color: .clear, location: 0),
                .init(color: .black, location: 0.04),
                .init(color: .black, location: 0.8),
                .init(color: .clear, location: 1)
            ],
            startPoint: .top,
            endPoint: .bottom
        )
        let horizontal = LinearGradient(
            stops: isInset
                ? [
                    .init(color: .clear, location: 0),
                    .init(color: .black, location: 0.1),
                    .init(color: .black, location: 0.9),
                    .init(color: .clear, location: 1)
                ]
                : [.init(color: .black, location: 0), .init(color: .black, location: 1)],
            startPoint: .leading,
            endPoint: .trailing
        )
        return vertical.mask { horizontal }
    }
}

private struct IntroSlideText: View {
    let slide: IntroSlide

    var body: some View {
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
        // A subtitle set across a landscape iPad is one very long line.
        .readableWidth(Layout.modalWidth + 100)
    }
}

#Preview {
    IntroCarouselView(
        slides: [
            IntroSlide(
                symbol: "trophy.fill",
                color: .primaryColor,
                title: "A Title",
                subtitle: "A subtitle explaining the feature in a sentence or two.",
                illustration: "onboarding-play-with-friends"
            ),
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
