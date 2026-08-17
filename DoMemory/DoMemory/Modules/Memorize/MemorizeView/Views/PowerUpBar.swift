//
//  PowerUpBar.swift
//  DoMemory
//
//  Row of star-priced assists shown under the HUD during Levels play.
//  Styling follows the HUD chips in MemorizeView.
//

import SwiftUI

struct PowerUpBar: View {
    let balance: Int
    var onUse: (LevelPowerUp) -> Void

    var body: some View {
        HStack(spacing: 8) {
            balanceChip

            Spacer(minLength: 4)

            ForEach(LevelPowerUp.allCases) { powerUp in
                button(for: powerUp)
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }

    private var balanceChip: some View {
        HStack(spacing: 4) {
            Image(systemName: "star.fill")
                .font(.system(size: 13))
                .foregroundStyle(Color.hardAmber)
            Text("\(balance)")
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundStyle(Color.textPrimary)
                .contentTransition(.numericText())
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(chipBackground)
        .accessibilityElement()
        .accessibilityLabel(Strings.starBalanceFormat(balance))
    }

    private func button(for powerUp: LevelPowerUp) -> some View {
        let affordable = balance >= powerUp.cost

        return Button {
            onUse(powerUp)
        } label: {
            VStack(spacing: 1) {
                Image(systemName: powerUp.systemImage)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(affordable ? Color.primaryColor : Color.textMuted)
                HStack(spacing: 2) {
                    Text("\(powerUp.cost)")
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                    Image(systemName: "star.fill")
                        .font(.system(size: 8))
                }
                .foregroundStyle(affordable ? Color.hardAmber : Color.textMuted)
            }
            .frame(width: 48, height: 44)
            .background(chipBackground)
            .opacity(affordable ? 1 : 0.45)
        }
        .buttonStyle(.plain)
        .disabled(!affordable)
        .accessibilityLabel(Strings.powerUpCostFormat(powerUp.title, powerUp.cost))
    }

    private var chipBackground: some View {
        Capsule()
            .fill(Color.surfacePrimary)
            .overlay(
                Capsule().stroke(Color.surfaceBorder, lineWidth: 1)
            )
            .shadow(color: Color.shadowColor, radius: 6, x: 0, y: 3)
    }
}

#Preview {
    VStack(spacing: 20) {
        PowerUpBar(balance: 12, onUse: { _ in })
        PowerUpBar(balance: 4, onUse: { _ in })
        PowerUpBar(balance: 0, onUse: { _ in })
    }
    .padding(.vertical)
    .background(Color.appBackground)
}
