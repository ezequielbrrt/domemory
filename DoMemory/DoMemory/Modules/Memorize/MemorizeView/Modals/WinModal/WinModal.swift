//
//  WinModal.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 19/10/20.
//

import SwiftUI

struct WinModal: View {
    var listener: WinModalListener?
    var pairsCount: Int = 12
    var timeRemaining: Int = 42
    var failedTries: Int = 2

    var body: some View {
        ZStack {
            Color.overlayBackdrop
                .ignoresSafeArea()
                .background(.ultraThinMaterial)

            Circle()
                .fill(Color.primaryColor.opacity(0.25))
                .frame(width: 12, height: 12)
                .offset(x: -150, y: -340)

            Circle()
                .fill(Color.secundaryColor.opacity(0.3))
                .frame(width: 8, height: 8)
                .offset(x: 130, y: -300)

            Circle()
                .fill(Color.easyGreen.opacity(0.35))
                .frame(width: 6, height: 6)
                .offset(x: -132, y: -220)

            Circle()
                .fill(Color.hardAmber.opacity(0.3))
                .frame(width: 10, height: 10)
                .offset(x: 150, y: 220)

            Circle()
                .fill(Color.primaryColor.opacity(0.2))
                .frame(width: 7, height: 7)
                .offset(x: -145, y: 260)

            VStack(spacing: 6) {
                Text("😎")
                    .font(.system(size: 68))
                    .padding(.bottom, 8)

                Text(Strings.youWin)
                    .font(.system(size: 36, weight: .heavy, design: .rounded))
                    .foregroundStyle(Color.primaryColor)
                    .multilineTextAlignment(.center)

                Text(Strings.youWinDescription)
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundStyle(Color.textMuted)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
                    .padding(.bottom, 8)

                HStack(spacing: 10) {
                    WinStatView(
                        value: "\(pairsCount)",
                        label: Strings.pairs,
                        valueColor: Color.primaryColor,
                        backgroundColor: Color.primaryColor.opacity(0.08)
                    )

                    WinStatView(
                        value: "\(timeRemaining)s",
                        label: Strings.remaining,
                        valueColor: Color.easyGreen,
                        backgroundColor: Color.easyGreen.opacity(0.08)
                    )

                    WinStatView(
                        value: "\(failedTries)",
                        label: Strings.errors,
                        valueColor: Color.secundaryColor,
                        backgroundColor: Color.secundaryColor.opacity(0.08)
                    )
                }
                .padding(.top, 8)
                .padding(.bottom, 16)

                Button(action: { listener?.tapOnContinue() }) {
                    Text(Strings.goToMenu)
                        .font(.system(size: 17, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(
                            Capsule().fill(Color.primaryColor)
                        )
                        .shadow(color: Color.primaryColor.opacity(0.35), radius: 16, x: 0, y: 4)
                }
                .buttonStyle(.plain)
            }
            .padding(.top, 40)
            .padding(.horizontal, 28)
            .padding(.bottom, 32)
            .background(
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(Color.surfacePrimary)
                    .overlay(
                        RoundedRectangle(cornerRadius: 28, style: .continuous)
                            .stroke(Color.surfaceBorder, lineWidth: 1)
                    )
                    .shadow(color: Color.shadowColor.opacity(1.2), radius: 60, x: 0, y: 20)
            )
            .padding(.horizontal, 32)
        }
    }
}

private struct WinStatView: View {
    let value: String
    let label: String
    let valueColor: Color
    let backgroundColor: Color

    var body: some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.system(size: 20, weight: .heavy, design: .rounded))
                .foregroundStyle(valueColor)
                .lineLimit(1)
                .minimumScaleFactor(0.7)

            Text(label)
                .font(.system(size: 11, weight: .semibold, design: .rounded))
                .foregroundStyle(Color.textMuted)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(minWidth: 61)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(backgroundColor)
        )
    }
}

#Preview {
    WinModal()
}
