//
//  LaunchScreenView.swift
//  DoMemory
//
//  Launch / splash screen — a faithful echo of the new app icon.
//  Exported from the Paper proposal "Launch Screen (Light/Dark)".
//
//  Note: iOS shows the system `UILaunchScreen` (currently a blank frame)
//  during the cold launch. This SwiftUI view is meant to be presented as a
//  brief splash overlay on top of `ContentView` right after launch — see the
//  wiring example at the bottom of this file.
//

import SwiftUI

struct LaunchScreenView: View {
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        ZStack {
            background
                .ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()

                cardMark

                Spacer()

                Text("DoMemory")
                    // Closest no-dependency match to the Nunito Black wordmark.
                    // Swap to `.custom("Nunito", size: 34).weight(.black)`
                    // if you add Nunito to UIAppFonts.
                    .font(.system(size: 34, weight: .black, design: .rounded))
                    .foregroundStyle(.white)
                    .padding(.bottom, 96)
            }
        }
    }

    // MARK: - Card mark (two overlapping memory cards)

    private var cardMark: some View {
        // Light: white card in front (indigo "?"), orange card behind.
        // Dark:  orange card in front (white "?"),  indigo card behind.
        let frontFill: Color = colorScheme == .dark ? .brandOrange : .white
        let backFill: Color = colorScheme == .dark ? .brandIndigo : .brandOrange
        let questionColor: Color = colorScheme == .dark ? .white : .brandIndigo

        return ZStack {
            // Back card
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(backFill)
                .frame(width: 150, height: 188)
                .rotationEffect(.degrees(-9))
                .offset(x: -19, y: 22)
                .shadow(color: .black.opacity(0.30), radius: 25, x: 0, y: 24)

            // Front card with the question mark — grouped, then transformed once.
            ZStack {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(frontFill)
                    .shadow(color: .black.opacity(colorScheme == .dark ? 0.55 : 0.35),
                            radius: 30, x: 0, y: 30)

                Text("?")
                    .font(.system(size: 120, weight: .black, design: .rounded))
                    .foregroundStyle(questionColor)
            }
            .frame(width: 158, height: 198)
            .rotationEffect(.degrees(5))
            .offset(x: 15, y: -19)
        }
        .frame(width: 188, height: 236)
    }

    // MARK: - Background

    private var background: some View {
        let stops: [Color] = colorScheme == .dark
            ? [Color(hex: 0x2A2156), Color(hex: 0x201A45), Color(hex: 0x14102E)]
            : [Color(hex: 0x5346D6), Color(hex: 0x4B3FC8), Color(hex: 0x36298F)]

        return RadialGradient(
            gradient: Gradient(colors: stops),
            center: UnitPoint(x: 0.5, y: 0.38),
            startRadius: 0,
            endRadius: 520
        )
    }
}

// MARK: - Brand colors

private extension Color {
    static let brandIndigo = Color(hex: 0x4B3FC8)
    static let brandOrange = Color(hex: 0xFF6340)

    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

#Preview("Light") {
    LaunchScreenView()
        .preferredColorScheme(.light)
}

#Preview("Dark") {
    LaunchScreenView()
        .preferredColorScheme(.dark)
}

// MARK: - Wiring example (splash overlay)
//
// In ContentView, fade the splash out after a beat:
//
//     @State private var showLaunch = true
//
//     var body: some View {
//         ZStack {
//             // ...existing MenuView / HomeView content...
//
//             if showLaunch {
//                 LaunchScreenView()
//                     .transition(.opacity)
//                     .task {
//                         try? await Task.sleep(for: .seconds(1.2))
//                         withAnimation(.easeOut(duration: 0.4)) { showLaunch = false }
//                     }
//             }
//         }
//     }
//
