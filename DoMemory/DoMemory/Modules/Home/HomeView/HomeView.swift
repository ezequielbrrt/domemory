//
//  HomeView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI

struct HomeView: View {
    @ObservedObject var showingView: ShowingView

    var viewModel: HomeViewModel
    init(showingView: ShowingView) {
        self.viewModel = HomeViewModel(showingView: showingView)
        self.showingView = showingView
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
                    .frame(width: 280, height: 280)
                    .offset(x: -140, y: -260)
                Circle()
                    .fill(Color.primaryColor.opacity(0.06))
                    .frame(width: 220, height: 220)
                    .offset(x: 160, y: 220)
            }
            .allowsHitTesting(false)

            VStack(spacing: 24) {
                // Header
                VStack(spacing: 8) {
                    Text("DoMemory")
                        .font(.righteous(size: 56))
                        .foregroundColor(.secundaryColor)
                        .shadow(color: .black.opacity(0.2), radius: 6, x: 0, y: 4)

                    Text(Strings.askDifficulty)
                        .font(.patrickHand(size: 22))
                        .foregroundColor(.primaryColor.opacity(0.9))
                }
                .padding(.top, 40)

                // Difficulty Cards Grid
                VStack(spacing: 16) {
                    DifficultyCard(title: Strings.easy, subtitle: "Relaxed start", color: .green.opacity(0.85), systemImage: "leaf.fill") {
                        viewModel.showMenuViewWithDifficulty(difficulty: .easy)
                    }

                    DifficultyCard(title: Strings.medium, subtitle: "A fair challenge", color: .blue.opacity(0.85), systemImage: "circle.grid.2x2.fill") {
                        viewModel.showMenuViewWithDifficulty(difficulty: .medium)
                    }

                    DifficultyCard(title: Strings.hard, subtitle: "Sharpen your memory", color: .orange.opacity(0.9), systemImage: "flame.fill") {
                        viewModel.showMenuViewWithDifficulty(difficulty: .hard)
                    }

                    DifficultyCard(title: Strings.veryHard, subtitle: "Only for experts", color: .red.opacity(0.9), systemImage: "bolt.trianglebadge.exclamationmark.fill") {
                        viewModel.showMenuViewWithDifficulty(difficulty: .veryHard)
                    }
                }
                .padding(.horizontal, 20)

                Spacer(minLength: 20)
            }
            .padding(.bottom, 24)
        }
    }
}

private struct DifficultyCard: View {
    let title: String
    let subtitle: String
    let color: Color
    let systemImage: String
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

                Image(systemName: "chevron.right")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.primaryColor.opacity(0.6))
            }
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Color.darkGrayColor.opacity(0.6))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .strokeBorder(color.opacity(0.25), lineWidth: 1)
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

struct HomeView_Previews: PreviewProvider {
    static var previews: some View {
        HomeView(showingView: ShowingView(showingView: .mainAppView))
    }
}
