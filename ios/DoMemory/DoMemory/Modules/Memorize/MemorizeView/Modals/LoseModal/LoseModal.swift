//
//  LoseModal.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 19/10/20.
//

import SwiftUI

struct LoseModal: View {
    var listener: LoseModalViewModelListener?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Plays when a life is actually booked while the modal is up — the
    /// out-of-lives path, where Try again spends the last heart in place.
    @State private var livesEffect: LivesRowEffect?

    private var isOutOfLives: Bool {
        (listener?.levelLivesRemaining ?? -1) == 0
    }

    private var lostToMistakes: Bool {
        listener?.loseReason == .tooManyMistakes
    }

    private var showSkipConfirm: Bool {
        listener?.showSkipLevelConfirm == true
    }

    private var isLevelPlay: Bool {
        listener?.levelNumber != nil
    }

    private var showsTryAgain: Bool {
        listener?.isDailyChallenge != true && !isOutOfLives
    }

    private var showsSkip: Bool {
        listener?.canSkipLevelWithStars == true
    }

    /// One rescue per loss, payable by ad, stars or either. What it buys
    /// follows from why the game ended: a life when there are none left,
    /// forgiven mistakes when the budget ran out, time when the clock did.
    private var offer: RescueOffer? {
        guard let listener else { return nil }
        let offer: RescueOffer
        if isOutOfLives {
            offer = RescueOffer(
                kind: .life,
                title: Strings.loseOfferExtraLife,
                hasAd: listener.canWatchAdForLife,
                starCost: listener.canBuyLifeWithStars ? LevelPowerUp.lifeCost : nil
            )
        } else if lostToMistakes {
            // Extra time is meaningless here — the clock wasn't the problem.
            offer = RescueOffer(
                kind: .forgive,
                title: Strings.loseOfferForgiveFormat(LevelPowerUp.forgiveAmount),
                hasAd: listener.canWatchAdToForgive,
                starCost: listener.canForgiveWithStars ? LevelPowerUp.forgiveCost : nil
            )
        } else {
            // Stars can't buy time: the only payment is the ad.
            offer = RescueOffer(
                kind: .time,
                title: Strings.loseOfferExtraTime,
                hasAd: listener.canOfferRewardedAds,
                starCost: nil
            )
        }
        return offer.isAvailable ? offer : nil
    }

    private func payWithAd(for kind: RescueOffer.Kind) {
        switch kind {
        case .life: listener?.tapOnWatchAdForLife()
        case .forgive: listener?.tapOnWatchAdToForgive()
        case .time: listener?.tapOnRewardedExtraTime()
        }
    }

    private func payWithStars(for kind: RescueOffer.Kind) {
        switch kind {
        case .life: listener?.tapOnBuyLifeWithStars()
        case .forgive: listener?.tapOnForgiveWithStars()
        case .time: break
        }
    }

    var body: some View {
        ZStack {
            Color.overlayBackdrop
                .ignoresSafeArea()
                .background(.ultraThinMaterial)

            VStack(spacing: 0) {
                // The hero says *why*: a clock rings and cracks on a timeout,
                // a red badge stamps in and shakes its head on a mistake
                // bust. Both end on a still picture. Reduce-motion players
                // keep the face the modal has always shown.
                if reduceMotion {
                    Text("😳")
                        .font(.system(size: 64))
                        .padding(.bottom, 12)
                } else {
                    LottieView(name: lostToMistakes ? "x-shake" : "clock-crack", tint: Color.secundaryColor)
                        .frame(width: 72, height: 72)
                        .padding(.bottom, 12)
                        .accessibilityHidden(true)
                }

                // Reason pill chip
                HStack(spacing: 6) {
                    Image(systemName: lostToMistakes ? "xmark.circle.fill" : "timer")
                        .font(.system(size: 13, weight: .semibold))
                    Text(lostToMistakes ? Strings.loseTooManyMistakes : Strings.youLose)
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                }
                .foregroundStyle(Color.secundaryColor)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(
                    Capsule().fill(Color.secundaryColor.opacity(0.1))
                )
                .padding(.bottom, 12)

                if isLevelPlay {
                    statusRow
                        .padding(.bottom, 8)
                }

                if isOutOfLives {
                    Text(listener?.canWatchAdForLife == true
                         ? Strings.outOfLivesMessage
                         : Strings.outOfLivesMessageNoAd)
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundStyle(Color.textMuted)
                        .multilineTextAlignment(.center)
                        .padding(.bottom, 8)
                }

                Color.clear.frame(height: 16)

                VStack(spacing: 12) {
                    if let offer {
                        offerBlock(offer)
                    }

                    if showsTryAgain {
                        Button(action: { listener?.tapOnTryAgain() }) {
                            Text(Strings.tryAgain)
                                .font(.system(size: 17, weight: .bold, design: .rounded))
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 16)
                                .background(
                                    Capsule().fill(Color.secundaryColor)
                                )
                        }
                        .buttonStyle(.plain)
                    }

                    footer
                }
            }
            // Out of lives there is no Try again to stretch the card, and it
            // would shrink to its widest label and squeeze the offer buttons.
            .frame(maxWidth: .infinity)
            .padding(28)
            .background(
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(Color.surfacePrimary)
                    .overlay(
                        RoundedRectangle(cornerRadius: 28, style: .continuous)
                            .stroke(Color.surfaceBorder, lineWidth: 1)
                    )
                    .shadow(color: Color.shadowColor, radius: 24, x: 0, y: 8)
            )
            .frame(maxWidth: ContentWidth.modal)
            .padding(.horizontal, 32)
            .opacity(showSkipConfirm ? 0 : 1)
            // Opacity alone still accepts taps: the confirm card is shorter
            // than this one, so the invisible Menu / Try again buttons would
            // stay live in the gap around it.
            .allowsHitTesting(!showSkipConfirm)

            if showSkipConfirm {
                skipConfirmCard
            }
        }
        .onChange(of: listener?.levelLivesRemaining) { previous, current in
            guard let previous, let current else { return }
            livesEffect = LivesRowEffect.forTransition(from: previous, to: current)
        }
    }

    /// Lives on the left, spendable stars on the right, so every star price
    /// below can be read against the balance that pays it.
    private var statusRow: some View {
        HStack(spacing: 12) {
            if let lives = listener?.levelLivesRemaining {
                // The loss isn't booked until the player leaves it, so the heart
                // it would cost is still full — it pulses instead of breaking.
                LivesRow(
                    remaining: lives,
                    iconSize: 15,
                    effect: livesEffect,
                    onEffectFinished: { livesEffect = nil },
                    atRiskSlot: listener?.isLifeAtStake == true ? lives - 1 : nil
                )
            }
            Spacer(minLength: 0)
            StarBalanceChip(balance: listener?.starBalance ?? 0)
        }
    }

    private func offerBlock(_ offer: RescueOffer) -> some View {
        VStack(spacing: 10) {
            VStack(spacing: 4) {
                Text(offer.title)
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                if listener?.isLifeAtStake == true {
                    Text(Strings.loseLifeAtStake)
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundStyle(Color.textMuted)
                }
            }
            .multilineTextAlignment(.center)

            HStack(spacing: 10) {
                if offer.hasAd {
                    // Takes whatever width the price button leaves.
                    adButton { payWithAd(for: offer.kind) }
                        .layoutPriority(1)
                }
                if let starCost = offer.starCost {
                    starButton(cost: starCost, fillsRow: !offer.hasAd) { payWithStars(for: offer.kind) }
                }
            }
        }
    }

    /// Skip and Menu drop to text buttons: neither rescues this game, and Skip
    /// still asks for confirmation before it spends anything. Menu keeps its
    /// outlined button when it is the only way out of the modal.
    @ViewBuilder
    private var footer: some View {
        if offer == nil && !showsTryAgain && !showsSkip {
            Button(action: { listener?.tapOnGoToMenuAfterLose() }) {
                Text(Strings.goToMenu)
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundStyle(Color.primaryColor)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(
                        Capsule()
                            .strokeBorder(Color.primaryColor.opacity(0.4), lineWidth: 1.5)
                    )
            }
            .buttonStyle(.plain)
        } else {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 16) {
                    if showsSkip {
                        skipLink
                        Spacer(minLength: 0)
                    }
                    menuLink
                }
                VStack(spacing: 0) {
                    if showsSkip {
                        skipLink
                    }
                    menuLink
                }
            }
        }
    }

    private var skipLink: some View {
        Button(action: { listener?.tapOnSkipLevelPrompt() }) {
            HStack(spacing: 4) {
                Text(Strings.skipLevelFormat(LevelPowerUp.skipLevelCost))
                Image(systemName: "star.fill")
                    .font(.system(size: 12))
            }
            .font(.system(size: 15, weight: .semibold, design: .rounded))
            .foregroundStyle(Color.hardAmber)
            .lineLimit(1)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var menuLink: some View {
        Button(action: { listener?.tapOnGoToMenuAfterLose() }) {
            Text(Strings.goToMenu)
                .font(.system(size: 15, weight: .semibold, design: .rounded))
                .foregroundStyle(Color.primaryColor)
                .lineLimit(1)
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// Rewarded-ad payment. Filled amber — the free path, so it leads.
    private func adButton(action: @escaping () -> Void) -> some View {
        let isLoading = listener?.isRewardedAdInProgress == true
        return Button(action: action) {
            HStack(spacing: 6) {
                if !isLoading {
                    Image(systemName: "play.rectangle.fill")
                        .font(.system(size: 14))
                }
                // A long translation wraps rather than shrinking below the
                // price button's type size.
                Text(isLoading ? Strings.adLoading : Strings.loseOfferWatchAd)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
            }
            .font(.system(size: 17, weight: .bold, design: .rounded))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .padding(.horizontal, 12)
            .background(Capsule().fill(Color.hardAmber))
        }
        .buttonStyle(.plain)
        .disabled(isLoading)
    }

    /// Star payment. Outlined rather than filled so it reads as an alternative
    /// to the free ad path, not a replacement for it. Sized to its price when
    /// it shares the row, so the ad button keeps the width its label needs.
    private func starButton(cost: Int, fillsRow: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Text("\(cost)")
                Image(systemName: "star.fill")
                    .font(.system(size: 13))
            }
            .font(.system(size: 17, weight: .bold, design: .rounded))
            .foregroundStyle(Color.hardAmber)
            .frame(maxWidth: fillsRow ? .infinity : nil)
            .padding(.vertical, 16)
            .padding(.horizontal, 24)
            .background(
                Capsule().strokeBorder(Color.hardAmber.opacity(0.5), lineWidth: 1.5)
            )
            .fixedSize(horizontal: !fillsRow, vertical: false)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Strings.losePayStarsFormat(cost))
    }

    private var skipConfirmCard: some View {
        VStack(spacing: 0) {
            Text(Strings.skipLevelConfirmTitle)
                .font(.system(size: 20, weight: .heavy, design: .rounded))
                .foregroundStyle(Color.textPrimary)
                .multilineTextAlignment(.center)
                .padding(.bottom, 10)

            Text(Strings.skipLevelConfirmMessage)
                .font(.system(size: 14))
                .foregroundStyle(Color.textMuted)
                .multilineTextAlignment(.center)
                .padding(.bottom, 24)

            VStack(spacing: 12) {
                Button(action: { listener?.tapOnConfirmSkipLevel() }) {
                    HStack(spacing: 6) {
                        Text(Strings.skipLevelConfirmAction)
                        Text("\(LevelPowerUp.skipLevelCost)")
                        Image(systemName: "star.fill")
                            .font(.system(size: 13))
                    }
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Capsule().fill(Color.hardAmber))
                }
                .buttonStyle(.plain)

                Button(action: { listener?.tapOnCancelSkipLevel() }) {
                    Text(Strings.cancel)
                        .font(.system(size: 17, weight: .semibold, design: .rounded))
                        .foregroundStyle(Color.primaryColor)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(
                            Capsule()
                                .strokeBorder(Color.primaryColor.opacity(0.4), lineWidth: 1.5)
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(28)
        .background(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(Color.surfacePrimary)
                .overlay(
                    RoundedRectangle(cornerRadius: 28, style: .continuous)
                        .stroke(Color.surfaceBorder, lineWidth: 1)
                )
                .shadow(color: Color.shadowColor, radius: 24, x: 0, y: 8)
        )
        .frame(maxWidth: ContentWidth.modal)
        .padding(.horizontal, 32)
    }
}

/// A rescue the lose modal can sell. Either payment may be missing — no ad
/// filled, or not enough stars — and an offer with neither isn't shown.
private struct RescueOffer {
    enum Kind { case life, forgive, time }

    let kind: Kind
    let title: String
    let hasAd: Bool
    /// Nil when the offer can't be paid in stars, or the player can't afford it.
    let starCost: Int?

    var isAvailable: Bool { hasAd || starCost != nil }
}

/// The spendable balance, styled like the chip on the in-game power-up bar.
private struct StarBalanceChip: View {
    let balance: Int

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "star.fill")
                .font(.system(size: 13))
                .foregroundStyle(Color.hardAmber)
            Text("\(balance)")
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundStyle(Color.textPrimary)
                .contentTransition(.numericText())
                .animation(.default, value: balance)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(
            Capsule()
                .fill(Color.surfacePrimary)
                .overlay(Capsule().stroke(Color.surfaceBorder, lineWidth: 1))
        )
        .accessibilityElement()
        .accessibilityLabel(Strings.starBalanceFormat(balance))
    }
}

#Preview {
    LoseModal()
}
