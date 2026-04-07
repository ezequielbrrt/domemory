//
//  LoseModal.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 19/10/20.
//

import SwiftUI

struct LoseModal: View {
    var listener: LoseModalViewModelListener?

    var body: some View {
        ZStack {
            Color.grayBackground.opacity(0.88)
                .ignoresSafeArea()
                .background(.ultraThinMaterial)

            VStack(spacing: 0) {
                Text("😳")
                    .font(.system(size: 64))
                    .padding(.bottom, 12)

                // "Time's up" pill chip
                HStack(spacing: 6) {
                    Image(systemName: "timer")
                        .font(.system(size: 13, weight: .semibold))
                    Text(Strings.youLose)
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                }
                .foregroundStyle(Color.secundaryColor)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(
                    Capsule().fill(Color.secundaryColor.opacity(0.1))
                )
                .padding(.bottom, 28)

                VStack(spacing: 12) {
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

                    Button(action: { listener?.tapOnTryAgain() }) {
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
                    .fill(Color.darkGrayColor)
                    .shadow(color: Color.textPrimary.opacity(0.12), radius: 24, x: 0, y: 8)
            )
            .padding(.horizontal, 32)
        }
    }
}

#Preview {
    LoseModal()
}
