//
//  MenuView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI
import WaterfallGrid

struct MenuView: View {
    @State private var viewModel = MenuViewModel()
    @State var showNewView = false
    @State var showBanner = false

    var body: some View {
        Group {
            if viewModel.isLoading {
                ZStack {
                    LinearGradient(
                        gradient: Gradient(colors: [Color.grayBackground, Color.darkGrayColor]),
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    .ignoresSafeArea()
                    LoadingView().padding()
                }
            } else {
                NavigationStack {
                    ZStack {
                        // Background gradient + decoration
                        LinearGradient(
                            gradient: Gradient(colors: [Color.grayBackground, Color.darkGrayColor]),
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                        .ignoresSafeArea()

                        ZStack {
                            Circle()
                                .fill(Color.secundaryColor.opacity(0.08))
                                .frame(width: 300, height: 300)
                                .offset(x: -150, y: -280)
                            Circle()
                                .fill(Color.primaryColor.opacity(0.06))
                                .frame(width: 220, height: 220)
                                .offset(x: 160, y: 260)
                        }
                        .allowsHitTesting(false)

                        VStack(spacing: 0) {
                            // Header Title
                            HStack {
                                Text("DoMemory")
                                    .font(.righteous(size: 34))
                                    .foregroundStyle(Color.secundaryColor)
                                    .shadow(color: .black.opacity(0.2), radius: 6, x: 0, y: 4)
                                Spacer()
                                Button(action: { self.showNewView = true }) {
                                    Image(systemName: "gearshape.fill")
                                        .font(.system(size: 18, weight: .semibold))
                                        .foregroundStyle(Color.primaryColor)
                                        .padding(10)
                                        .background(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .fill(Color.darkGrayColor.opacity(0.6))
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                            .padding(.horizontal)
                            .padding(.top, 8)

                            // Grid
                            ScrollView {
                                WaterfallGrid(viewModel.memoramaArray) { (memorama: Memorama) in
                                    MemoramaGridCell(
                                        memorama: memorama,
                                        isFavorite: viewModel.isFavorite(id: memorama.id),
                                        onToggleFavorite: { viewModel.toggleFavorite(id: memorama.id) }
                                    )
                                }
                                .gridStyle(
                                    columns: 2,
                                    spacing: 14,
                                    animation: Animation.spring(response: 0.35, dampingFraction: 0.85)
                                )
                                .padding(.horizontal)
                                .padding(.bottom, 16)
                            }
                        }
                    }
                    .navigationBarHidden(true)
                    .navigationDestination(isPresented: $showNewView) {
                        SettingsView(listener: viewModel)
                    }
                }
            }
        }
    }
}

private struct MemoramaGridCell: View {
    let memorama: Memorama
    let isFavorite: Bool
    let onToggleFavorite: () -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            NavigationLink {
                MemorizeView(viewModel: MemorizeViewModel(memorama: memorama))
            } label: {
                MemoramaCard(memorama: memorama)
            }

            Button(action: onToggleFavorite) {
                Image(systemName: isFavorite ? "heart.fill" : "heart")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(isFavorite ? Color.red : Color.white.opacity(0.7))
                    .padding(8)
            }
            .buttonStyle(.plain)
            .padding(.top, 4)
        }
    }
}

struct MemoramaCard: View {
    var memorama: Memorama

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(
                    LinearGradient(
                        gradient: Gradient(colors: [Color.primaryColor, Color.secundaryColor]),
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.12), lineWidth: 1)
                )
                .frame(height: 120)
                .shadow(color: .black.opacity(0.25), radius: 8, x: 0, y: 6)

            VStack(alignment: .leading, spacing: 6) {
                Text(memorama.name)
                    .font(.patrickHand(size: 28))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)

                HStack(spacing: 8) {
                    let difficultyEnum = Difficulty(rawValue: memorama.difficulty) ?? .medium
                    Image(systemName: "brain.head.profile")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.9))
                    Text(difficultyEnum.rawValue.capitalized)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.9))
                        .padding(.vertical, 4)
                        .padding(.horizontal, 8)
                        .background(Color.black.opacity(0.18))
                        .clipShape(Capsule())
                }
            }
            .padding(12)
        }
        .padding(.vertical, 4)
    }
}

private struct ScaleCardButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
            .animation(.spring(response: 0.25, dampingFraction: 0.8), value: configuration.isPressed)
    }
}

#Preview {
    MenuView()
}
