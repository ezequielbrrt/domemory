//
//  QuitModal.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 19/10/20.
//

import SwiftUI

struct QuitModal: View {
    var listener: QuitModalListener?

    var body: some View {
        ZStack {
            Color.overlayBackdrop
                .ignoresSafeArea()
                .background(.ultraThinMaterial)

            VStack(spacing: 0) {
                Text("🥺")
                    .font(.system(size: 56))
                    .padding(.bottom, 12)

                Text(Strings.quit)
                    .font(.system(size: 18, weight: .heavy, design: .rounded))
                    .foregroundStyle(Color.textPrimary)
                    .multilineTextAlignment(.center)
                    .padding(.bottom, 6)

                Text(Strings.youWinDescription)
                    .font(.system(size: 13, weight: .regular))
                    .foregroundStyle(Color.textMuted)
                    .multilineTextAlignment(.center)
                    .padding(.bottom, 28)

                HStack(spacing: 12) {
                    Button(action: { listener?.tapOnCancel() }) {
                        Text(Strings.cancel)
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .foregroundStyle(Color.secundaryColor)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(
                                Capsule()
                                    .fill(Color.secundaryColor.opacity(0.1))
                            )
                    }
                    .buttonStyle(.plain)

                    Button(action: { listener?.tapOnExit() }) {
                        Text(Strings.accept)
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
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
    QuitModal()
}
