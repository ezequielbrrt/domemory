//
//  MenuView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI
import WaterfallGrid

struct MenuView: View {
    @ObservedObject var viewModel = MenuViewModel()
    @State var showNewView = false
    @State var showBanner = false

    init() {
        UINavigationBar.appearance().largeTitleTextAttributes = [
            .foregroundColor: Color.secundaryColor.uiColor(), .font: UIFont.righteous(size: 35)]
                
        UINavigationBar.appearance().titleTextAttributes = [ .foregroundColor: Color.secundaryColor.uiColor(), .font: UIFont.righteous(size: 19)]
        
        UINavigationBar.appearance().backgroundColor = Color.grayBackground.uiColor()
        
    }
    
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
                NavigationView {
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
                                    .foregroundColor(.secundaryColor)
                                    .shadow(color: .black.opacity(0.2), radius: 6, x: 0, y: 4)
                                Spacer()
                                Button(action: { self.showNewView = true }) {
                                    Image(systemName: "gearshape.fill")
                                        .font(.system(size: 18, weight: .semibold))
                                        .foregroundColor(.primaryColor)
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

                            NavigationLink(
                                destination: SettingsView(listener: viewModel),
                                isActive: $showNewView
                            ) { EmptyView() }.isDetailLink(false)

                            // Grid
                            ScrollView {
                                WaterfallGrid(viewModel.memoramaArray) { memorama in
                                    NavigationLink(
                                        destination: MemorizeView(viewModel: MemorizeViewModel(memorama: memorama))
                                            .navigationBarTitle("")
                                            .navigationBarHidden(true)
                                    ) {
                                        MemoramaCard(memorama: memorama)
                                    }
                                    .isDetailLink(true)
                                }
                                .gridStyle(
                                    columns: 2,
                                    spacing: 14,
                                    animation: .spring(response: 0.35, dampingFraction: 0.85)
                                )
                                .padding(.horizontal)
                                .padding(.bottom, 16)
                            }
                        }
                    }
                    .navigationBarHidden(true)
                }
                .navigationViewStyle(.stack)
            }
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
                    .foregroundColor(.white)
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)

                HStack(spacing: 8) {
                    let difficultyEnum = Difficulty(rawValue: memorama.difficulty) ?? .medium
                    Image(systemName: "brain.head.profile")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.white.opacity(0.9))
                    Text(difficultyEnum.rawValue.capitalized)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.white.opacity(0.9))
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

struct MenuView_Previews: PreviewProvider {
    static var previews: some View {
        MenuView()
    }
}
