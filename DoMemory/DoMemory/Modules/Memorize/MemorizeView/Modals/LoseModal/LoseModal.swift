//
//  LoseModal.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 19/10/20.
//

import SwiftUI

struct LoseModal: View {
    var listener: LoseModalViewModelListener?

    private var isOutOfLives: Bool {
        (listener?.levelLivesRemaining ?? -1) == 0
    }

    private var lostToMistakes: Bool {
        listener?.loseReason == .tooManyMistakes
    }

    private var showSkipConfirm: Bool {
        listener?.showSkipLevelConfirm == true
    }

    var body: some View {
        ZStack {
            Color.overlayBackdrop
                .ignoresSafeArea()
                .background(.ultraThinMaterial)

            VStack(spacing: 0) {
                Text("😳")
                    .font(.system(size: 64))
                    .padding(.bottom, 12)

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

                if let lives = listener?.levelLivesRemaining {
                    LivesRow(remaining: lives, iconSize: 15)
                        .padding(.bottom, 8)

                    if isOutOfLives {
                        Text(Strings.outOfLivesMessage)
                            .font(.system(size: 13, weight: .semibold, design: .rounded))
                            .foregroundStyle(Color.textMuted)
                            .multilineTextAlignment(.center)
                            .padding(.bottom, 8)
                    }
                }

                Color.clear.frame(height: 16)

                VStack(spacing: 12) {
                    if isOutOfLives {
                        if listener?.canWatchAdForLife == true {
                            adButton(title: Strings.watchAdForLife) { listener?.tapOnWatchAdForLife() }
                        }
                    } else if lostToMistakes {
                        // Extra time is meaningless here — the clock wasn't the problem.
                        if listener?.canWatchAdToForgive == true {
                            adButton(title: Strings.forgiveAdFormat(LevelPowerUp.forgiveAmount)) {
                                listener?.tapOnWatchAdToForgive()
                            }
                        }
                    } else if listener?.canOfferRewardedAds == true {
                        adButton(title: Strings.rewardedExtraTime) { listener?.tapOnRewardedExtraTime() }
                    }

                    if isOutOfLives, listener?.canBuyLifeWithStars == true {
                        starButton(
                            title: Strings.buyLifeFormat(LevelPowerUp.lifeCost),
                            action: { listener?.tapOnBuyLifeWithStars() }
                        )
                    }

                    if !isOutOfLives, lostToMistakes, listener?.canForgiveWithStars == true {
                        starButton(
                            title: Strings.forgiveStarsFormat(LevelPowerUp.forgiveAmount, LevelPowerUp.forgiveCost),
                            action: { listener?.tapOnForgiveWithStars() }
                        )
                    }

                    if listener?.isDailyChallenge != true && !isOutOfLives {
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

                    if listener?.canSkipLevelWithStars == true {
                        starButton(
                            title: Strings.skipLevelFormat(LevelPowerUp.skipLevelCost),
                            action: { listener?.tapOnSkipLevelPrompt() }
                        )
                    }

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
    }

    /// Rewarded-ad action. Filled amber — the free path, so it leads.
    private func adButton(title: String, action: @escaping () -> Void) -> some View {
        let isLoading = listener?.isRewardedAdInProgress == true
        return Button(action: action) {
            Text(isLoading ? Strings.adLoading : title)
                .font(.system(size: 17, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(Capsule().fill(Color.hardAmber))
        }
        .buttonStyle(.plain)
        .disabled(isLoading)
    }

    /// Star-priced secondary action. Outlined rather than filled so it reads as
    /// an alternative to the free ad path, not a replacement for it.
    private func starButton(title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Text(title)
                Image(systemName: "star.fill")
                    .font(.system(size: 13))
            }
            .font(.system(size: 16, weight: .bold, design: .rounded))
            .foregroundStyle(Color.hardAmber)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(
                Capsule().strokeBorder(Color.hardAmber.opacity(0.5), lineWidth: 1.5)
            )
        }
        .buttonStyle(.plain)
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
        .padding(.horizontal, 32)
    }
}

#Preview {
    LoseModal()
}
