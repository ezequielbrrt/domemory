//
//  SettingsView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 24/09/20.
//

import SwiftUI

struct SettingsView: View {
    @ObservedObject var viewModel: SettingsViewModel
    
    @State private var isToggle : Bool = false
    @State private var selectedStrength: Int = 0
    
    let impactMed = UIImpactFeedbackGenerator(style: .medium)
        
    
    init(listener: SettingsListener?) {
        self.viewModel = SettingsViewModel(listener: listener)

    }

    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                gradient: Gradient(colors: [Color.grayBackground, Color.darkGrayColor]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            // Decorative circles
            ZStack {
                Circle()
                    .fill(Color.secundaryColor.opacity(0.08))
                    .frame(width: 260, height: 260)
                    .offset(x: -130, y: -240)
                Circle()
                    .fill(Color.primaryColor.opacity(0.06))
                    .frame(width: 200, height: 200)
                    .offset(x: 140, y: 220)
            }
            .allowsHitTesting(false)

            VStack(spacing: 24) {
                // Header
                VStack(spacing: 8) {
                    Text(Strings.difficulty)
                        .font(.righteous(size: 40))
                        .foregroundColor(.secundaryColor)
                        .shadow(color: .black.opacity(0.2), radius: 6, x: 0, y: 4)

                    Text(Strings.askDifficulty)
                        .font(.patrickHand(size: 20))
                        .foregroundColor(.primaryColor.opacity(0.9))
                }
                .padding(.top, 20)

                // Difficulty selection cards
                VStack(spacing: 14) {
                    SelectableDifficultyCard(
                        title: Strings.easy,
                        subtitle: "Relaxed start",
                        color: .green.opacity(0.85),
                        systemImage: "leaf.fill",
                        isSelected: viewModel.difficulty == 0
                    ) {
                        impactMed.impactOccurred()
                        viewModel.saveDifficulty(difficultyIndex: 0)
                    }

                    SelectableDifficultyCard(
                        title: Strings.medium,
                        subtitle: "A fair challenge",
                        color: .blue.opacity(0.85),
                        systemImage: "circle.grid.2x2.fill",
                        isSelected: viewModel.difficulty == 1
                    ) {
                        impactMed.impactOccurred()
                        viewModel.saveDifficulty(difficultyIndex: 1)
                    }

                    SelectableDifficultyCard(
                        title: Strings.hard,
                        subtitle: "Sharpen your memory",
                        color: .orange.opacity(0.9),
                        systemImage: "flame.fill",
                        isSelected: viewModel.difficulty == 2
                    ) {
                        impactMed.impactOccurred()
                        viewModel.saveDifficulty(difficultyIndex: 2)
                    }

                    SelectableDifficultyCard(
                        title: Strings.veryHard,
                        subtitle: "Only for experts",
                        color: .red.opacity(0.9),
                        systemImage: "bolt.trianglebadge.exclamationmark.fill",
                        isSelected: viewModel.difficulty == 3
                    ) {
                        impactMed.impactOccurred()
                        viewModel.saveDifficulty(difficultyIndex: 3)
                    }
                }
                .padding(.horizontal, 20)

                Spacer(minLength: 20)
            }
            .padding(.bottom, 24)
        }
        .onAppear {
            self.viewModel.difficulty = self.viewModel.getCurrentDifficulty()
        }
        .onDisappear {
            self.viewModel.viewWillDissapear()
        }
    }
}

private struct SelectableDifficultyCard: View {
    let title: String
    let subtitle: String
    let color: Color
    let systemImage: String
    let isSelected: Bool
    let action: () -> Void

    @State private var isPressed: Bool = false

    var body: some View {
        Button(action: trigger) {
            HStack(spacing: 16) {
                ZStack {
                    Circle()
                        .fill(color.opacity(0.2))
                        .frame(width: 52, height: 52)
                    Image(systemName: systemImage)
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundColor(color)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.patrickHand(size: 24))
                        .foregroundColor(.primaryColor)
                    Text(subtitle)
                        .font(.system(size: 13, weight: .regular))
                        .foregroundColor(.primaryColor.opacity(0.7))
                }

                Spacer()

                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(color)
                        .font(.system(size: 20, weight: .semibold))
                        .transition(.scale)
                } else {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.primaryColor.opacity(0.6))
                }
            }
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Color.darkGrayColor.opacity(0.6))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .strokeBorder(isSelected ? color.opacity(0.6) : color.opacity(0.25), lineWidth: isSelected ? 2 : 1)
                    )
            )
            .shadow(color: .black.opacity(isPressed ? 0.1 : 0.25), radius: isPressed ? 2 : 6, x: 0, y: isPressed ? 1 : 4)
            .scaleEffect(isPressed ? 0.98 : 1.0)
            .animation(.spring(response: 0.28, dampingFraction: 0.8), value: isPressed)
        }
        .buttonStyle(.plain)
        .onLongPressGesture(minimumDuration: .infinity, pressing: { pressing in
            withAnimation { isPressed = pressing }
        }, perform: {})
    }

    private func trigger() {
        #if os(iOS)
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.impactOccurred()
        #endif
        action()
    }
}

struct SettingsView_Previews: PreviewProvider {
    static var previews: some View {
        SettingsView(listener: nil)
    }
}
