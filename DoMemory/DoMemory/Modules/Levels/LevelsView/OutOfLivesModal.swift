//
//  OutOfLivesModal.swift
//  DoMemory
//
//  Shown from the Levels map when tapping a level with no lives left today.
//  Mirrors QuitModal/PauseModal's backdrop+card structure.
//

import SwiftUI

struct OutOfLivesModal: View {
    let canWatchAd: Bool
    let isAdInProgress: Bool
    var canBuyWithStars: Bool = false
    var onWatchAd: () -> Void
    var onBuyWithStars: () -> Void = {}
    var onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color.overlayBackdrop
                .ignoresSafeArea()
                .background(.ultraThinMaterial)

            VStack(spacing: 0) {
                Text("💔")
                    .font(.system(size: 56))
                    .padding(.bottom, 12)

                Text(Strings.outOfLivesTitle)
                    .font(.system(size: 22, weight: .heavy, design: .rounded))
                    .foregroundStyle(Color.textPrimary)
                    .padding(.bottom, 12)

                LivesRow(remaining: 0)
                    .padding(.bottom, 12)

                Text(Strings.outOfLivesMessage)
                    .font(.system(size: 14, weight: .regular))
                    .foregroundStyle(Color.textMuted)
                    .multilineTextAlignment(.center)
                    .padding(.bottom, 28)

                VStack(spacing: 12) {
                    if canWatchAd {
                        Button(action: onWatchAd) {
                            Text(isAdInProgress ? Strings.adLoading : Strings.watchAdForLife)
                                .font(.system(size: 17, weight: .bold, design: .rounded))
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 16)
                                .background(
                                    Capsule().fill(Color.hardAmber)
                                )
                        }
                        .buttonStyle(.plain)
                        .disabled(isAdInProgress)
                    }

                    if canBuyWithStars {
                        Button(action: onBuyWithStars) {
                            HStack(spacing: 6) {
                                Text(Strings.buyLifeFormat(LevelPowerUp.lifeCost))
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

                    Button(action: onDismiss) {
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
}

#Preview {
    OutOfLivesModal(canWatchAd: true, isAdInProgress: false, onWatchAd: {}, onDismiss: {})
}
