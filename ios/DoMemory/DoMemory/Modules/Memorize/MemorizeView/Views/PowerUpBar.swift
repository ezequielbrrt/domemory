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
    /// Peek or Freeze already running — disabled until it ends.
    var isActive: (LevelPowerUp) -> Bool = { _ in false }
    /// A free use, earned by a match streak, is waiting on this power-up.
    var isCharged: (LevelPowerUp) -> Bool = { _ in false }
    /// Which power-ups the board offers; a moves level drops the clock ones.
    var powerUps: [LevelPowerUp] = LevelPowerUp.allCases
    var onUse: (LevelPowerUp) -> Void

    var body: some View {
        HStack(spacing: 8) {
            balanceChip

            Spacer(minLength: 4)

            ForEach(powerUps) { powerUp in
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
        let charged = isCharged(powerUp)
        let available = (charged || balance >= powerUp.cost) && !isActive(powerUp)

        return Button {
            onUse(powerUp)
        } label: {
            VStack(spacing: 1) {
                Image(systemName: powerUp.systemImage)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(available ? Color.primaryColor : Color.textMuted)
                    .symbolEffect(.bounce, value: charged)
                // A charged power-up shows what it is instead of what it costs:
                // the price is the one thing that no longer applies.
                if charged {
                    Text(Strings.powerUpFree)
                        .font(.system(size: 9, weight: .heavy, design: .rounded))
                        .foregroundStyle(Color.hardAmber)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                } else {
                    HStack(spacing: 2) {
                        Text("\(powerUp.cost)")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                        Image(systemName: "star.fill")
                            .font(.system(size: 8))
                    }
                    .foregroundStyle(available ? Color.hardAmber : Color.textMuted)
                }
            }
            .frame(width: 48, height: 44)
            .background(chipBackground(highlighted: charged))
            .opacity(available ? 1 : 0.45)
        }
        .buttonStyle(.plain)
        .disabled(!available)
        .animation(.spring(duration: 0.35, bounce: 0.4), value: charged)
        .accessibilityLabel(charged
            ? Strings.powerUpChargedAccessibilityFormat(powerUp.title)
            : Strings.powerUpCostFormat(powerUp.title, powerUp.cost))
    }

    private var chipBackground: some View {
        chipBackground(highlighted: false)
    }

    /// The HUD chip, or its amber-ringed form for a power-up with a free use
    /// waiting — lit from the same colour as the stars it saves.
    private func chipBackground(highlighted: Bool) -> some View {
        Capsule()
            .fill(highlighted ? Color.hardAmber.opacity(0.14) : Color.surfacePrimary)
            .overlay(
                Capsule().stroke(highlighted ? Color.hardAmber.opacity(0.7) : Color.surfaceBorder, lineWidth: highlighted ? 1.5 : 1)
            )
            .shadow(color: highlighted ? Color.hardAmber.opacity(0.35) : Color.shadowColor, radius: 6, x: 0, y: 3)
    }

}

#Preview {
    VStack(spacing: 20) {
        PowerUpBar(balance: 12, onUse: { _ in })
        PowerUpBar(balance: 4, onUse: { _ in })
        PowerUpBar(balance: 0, onUse: { _ in })
        PowerUpBar(balance: 0, isCharged: { $0 == .peek || $0 == .freeze }, onUse: { _ in })
    }
    .padding(.vertical)
    .background(Color.appBackground)
}
