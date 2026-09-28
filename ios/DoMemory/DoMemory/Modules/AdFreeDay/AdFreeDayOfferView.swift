//
//  AdFreeDayOfferView.swift
//  DoMemory
//
//  The ad-free day sheet: Flippo, a short pitch, a stepper showing the two-ad
//  chain, one primary action, the Remove Ads upsell and a dismiss. The first
//  presentation apologises for the ads; every later one uses the neutral copy.
//

import SwiftUI

/// Asset names for the sheet's illustration; the source art lives in
/// `assets/images/flippo` and the prompts in `ios/docs/plans/ad-free-day.md`.
enum AdFreeDayArt {
    static let sorry = "FlippoSorry"
    static let happy = "FlippoNoAds"
}

struct AdFreeDayOfferView: View {
    @State private var viewModel: AdFreeDayOfferViewModel
    @Environment(\.dismiss) private var dismiss

    init(source: String) {
        _viewModel = State(initialValue: AdFreeDayOfferViewModel(source: source))
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

            ScrollView {
                content
                    .frame(maxWidth: ContentWidth.modal)
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 24)
                    .padding(.top, 28)
                    .padding(.bottom, 16)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .presentationDetents([.fraction(0.85), .large])
        .presentationDragIndicator(.visible)
        .onAppear { viewModel.onAppear() }
    }

    private var isActive: Bool { viewModel.phase == .active }
    private var isBusy: Bool { viewModel.phase == .loading || viewModel.phase == .presenting }

    private var content: some View {
        VStack(spacing: 0) {
            Image(viewModel.isIntro && !isActive ? AdFreeDayArt.sorry : AdFreeDayArt.happy)
                .resizable()
                .scaledToFit()
                .frame(height: 150)
                .accessibilityHidden(true)
                .padding(.bottom, 16)

            Text(viewModel.isIntro ? Strings.adFreeDayIntroTitle : Strings.adFreeDayTitle)
                .font(.system(size: 26, weight: .heavy, design: .rounded))
                .foregroundStyle(Color.primaryColor)
                .multilineTextAlignment(.center)
                .padding(.bottom, 10)

            if !isActive {
                Text(viewModel.isIntro
                     ? Strings.adFreeDayIntroMessage(viewModel.requiredAds)
                     : Strings.adFreeDayMessage(viewModel.requiredAds))
                    .font(.system(size: 16, weight: .medium, design: .rounded))
                    .foregroundStyle(Color.textSecondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }

            AdFreeDayStepper(
                adsWatched: viewModel.adsWatched,
                required: viewModel.requiredAds,
                isGranted: isActive
            )
            .padding(.top, 24)
            .padding(.bottom, 12)

            // Fixed minimum height so the sheet does not jump between states.
            Text(statusText)
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundStyle(viewModel.phase == .noFill ? Color.secundaryColor : Color.textSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .frame(minHeight: 40)
                .padding(.bottom, 8)

            Button(action: primaryTapped) {
                Text(primaryTitle)
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(
                        Capsule().fill(isActive ? Color.primaryColor : Color.hardAmber)
                            .opacity(isBusy ? 0.6 : 1)
                    )
            }
            .buttonStyle(.plain)
            .disabled(isBusy)
            .padding(.bottom, 16)

            if !viewModel.hasPurchasedRemoveAds {
                Button {
                    HapticsService.shared.fire(.tap)
                    Task {
                        await viewModel.purchaseRemoveAds()
                        if viewModel.hasPurchasedRemoveAds {
                            dismiss()
                        }
                    }
                } label: {
                    Text(Strings.adFreeDayRemoveForever(viewModel.removeAdsPriceText))
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .foregroundStyle(Color.primaryColor)
                }
                .buttonStyle(.plain)
                .padding(.bottom, 14)
            }

            if !isActive {
                Button {
                    HapticsService.shared.fire(.tap)
                    dismiss()
                } label: {
                    Text(Strings.adFreeDayNotNow)
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundStyle(Color.textMuted)
                        .padding(.vertical, 6)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var statusText: String {
        switch viewModel.phase {
        case .active:
            return Strings.adFreeDayActive(viewModel.expirationText)
        case .noFill:
            return Strings.adFreeDayNoFill
        case .loading, .ready, .presenting:
            return viewModel.adsWatched == viewModel.requiredAds - 1 ? Strings.adFreeDayHalfwayHint : ""
        }
    }

    private var primaryTitle: String {
        switch viewModel.phase {
        case .loading, .presenting:
            return Strings.adLoading
        case .ready:
            if viewModel.isIntro && viewModel.adsWatched == 0 {
                return Strings.adFreeDayIntroWatch(viewModel.nextAdNumber, of: viewModel.requiredAds)
            }
            return Strings.adFreeDayWatch(viewModel.nextAdNumber, of: viewModel.requiredAds)
        case .noFill:
            return Strings.adFreeDayTryAgain
        case .active:
            return Strings.adFreeDayBack
        }
    }

    private func primaryTapped() {
        HapticsService.shared.fire(.tap)
        if isActive {
            dismiss()
        } else {
            viewModel.primaryAction()
        }
    }
}

/// `( 1 )──( 2 )──( ★ )` with labels: one node per ad, then the reward.
private struct AdFreeDayStepper: View {
    let adsWatched: Int
    let required: Int
    let isGranted: Bool

    private let nodeSize: CGFloat = 36

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            ForEach(1...required, id: \.self) { index in
                node(done: isGranted || adsWatched >= index, label: Strings.adFreeDayStepAd(index)) {
                    if isGranted || adsWatched >= index {
                        Image(systemName: "checkmark")
                            .font(.system(size: 15, weight: .heavy))
                            .foregroundStyle(.white)
                    } else {
                        Text("\(index)")
                            .font(.system(size: 15, weight: .heavy, design: .rounded))
                            .foregroundStyle(Color.textSecondary)
                    }
                }
                connector(filled: isGranted || adsWatched >= index)
            }
            node(done: isGranted, label: Strings.adFreeDayStepReward, fill: Color.hardAmber) {
                Image(systemName: "star.fill")
                    .font(.system(size: 15, weight: .heavy))
                    .foregroundStyle(isGranted ? .white : Color.hardAmber)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Strings.adFreeDayPillProgress(isGranted ? required : adsWatched, of: required))
    }

    private func node<Content: View>(
        done: Bool,
        label: String,
        fill: Color = Color.primaryColor,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(spacing: 6) {
            ZStack {
                Circle()
                    .fill(done ? fill : Color.surfacePrimary)
                Circle()
                    .stroke(done ? fill : Color.surfaceBorder, lineWidth: 2)
                content()
            }
            .frame(width: nodeSize, height: nodeSize)

            Text(label)
                .font(.system(size: 12, weight: .semibold, design: .rounded))
                .foregroundStyle(done ? Color.textPrimary : Color.textSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(width: 84)
    }

    private func connector(filled: Bool) -> some View {
        Rectangle()
            .fill(filled ? Color.primaryColor : Color.surfaceBorder)
            .frame(height: 3)
            .frame(maxWidth: .infinity)
            .padding(.top, nodeSize / 2 - 1.5)
            .padding(.horizontal, -18)
    }
}

#Preview("Ad-free day") {
    Color.clear
        .sheet(isPresented: .constant(true)) {
            AdFreeDayOfferView(source: "preview")
        }
}
