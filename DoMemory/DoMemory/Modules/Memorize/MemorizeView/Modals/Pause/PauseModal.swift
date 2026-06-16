//
//  PauseModal.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 19/10/20.
//

import SwiftUI

struct PauseModal: View {
    var listener: PauseModalListener?

    var body: some View {
        ZStack {
            Color.overlayBackdrop
                .ignoresSafeArea()
                .background(.ultraThinMaterial)

            VStack(spacing: 0) {
                Text("🧐")
                    .font(.system(size: 64))
                    .padding(.bottom, 12)

                Text(Strings.pause)
                    .font(.system(size: 36, weight: .heavy, design: .rounded))
                    .foregroundStyle(Color.primaryColor)
                    .padding(.bottom, 28)

                VStack(spacing: 12) {
                    if listener?.canOfferRewardedAds == true {
                        Button(action: { listener?.tapOnRewardedHint() }) {
                            Text(listener?.isRewardedAdInProgress == true ? Strings.adLoading : Strings.rewardedHint)
                                .font(.system(size: 17, weight: .bold, design: .rounded))
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 16)
                                .background(
                                    Capsule().fill(Color.hardAmber)
                                )
                        }
                        .buttonStyle(.plain)
                        .disabled(listener?.isRewardedAdInProgress == true)
                    }

                    if listener?.isDailyChallenge != true {
                        Button(action: { listener?.tapOnReloadGame() }) {
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

                    Button(action: { listener?.tapOnResumeGame() }) {
                        Text(Strings.continueGame)
                            .font(.system(size: 17, weight: .bold, design: .rounded))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(
                                Capsule().fill(Color.primaryColor)
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
    PauseModal(listener: nil)
}
