//
//  ShareResultCard.swift
//  DoMemory
//

import SwiftUI
import UIKit

struct ResultShareData {
    let pairs: Int
    let timeRemaining: Int
    let failedTries: Int
    let difficultyTitle: String
    let isDailyChallenge: Bool
    let streak: Int
}

/// Spoiler-free Wordle-style grid: one square per pair (green), plus a row of
/// amber squares for mistakes. Reveals nothing about the actual emojis.
private func resultGridString(pairs: Int, failedTries: Int) -> String {
    let greens = String(repeating: "🟩", count: max(0, min(pairs, 12)))
    let ambers = failedTries > 0 ? "\n" + String(repeating: "🟧", count: min(failedTries, 12)) : ""
    return greens + ambers
}

/// The rendered card shared as an image.
struct ShareResultCardView: View {
    let data: ResultShareData
    private let brand = Color.primaryColor

    var body: some View {
        VStack(spacing: 14) {
            Text(Strings.appName)
                .font(.righteous(size: 26))
                .foregroundStyle(.white)

            Text("😎")
                .font(.system(size: 60))

            Text(data.isDailyChallenge ? Strings.dailyChallengeTitle : Strings.youWin)
                .font(.system(size: 22, weight: .heavy, design: .rounded))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)

            Text(gridText)
                .font(.system(size: 20))
                .multilineTextAlignment(.center)
                .lineSpacing(2)

            VStack(spacing: 6) {
                row(Strings.difficulty, data.difficultyTitle)
                row(Strings.pairs, "\(data.pairs)")
                row(Strings.remaining, "\(data.timeRemaining)s")
                row(Strings.errors, "\(data.failedTries)")
                if data.isDailyChallenge && data.streak > 0 {
                    row("🔥", "\(data.streak)")
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(.white.opacity(0.16))
            )
        }
        .padding(24)
        .frame(width: 320, height: 440)
        .background(brand)
    }

    private var gridText: String {
        resultGridString(pairs: data.pairs, failedTries: data.failedTries)
    }

    private func row(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundStyle(.white.opacity(0.85))
            Spacer()
            Text(value)
                .font(.system(size: 15, weight: .heavy, design: .rounded))
                .foregroundStyle(.white)
        }
    }
}

enum ResultShare {
    @MainActor
    static func renderCard(_ data: ResultShareData) -> UIImage? {
        let renderer = ImageRenderer(content: ShareResultCardView(data: data))
        renderer.scale = UIScreen.main.scale
        return renderer.uiImage
    }

    static func caption(_ data: ResultShareData) -> String {
        let headline = String(
            format: NSLocalizedString("share_result_caption", comment: "Share caption, %d = pairs"),
            data.pairs
        )
        let grid = resultGridString(pairs: data.pairs, failedTries: data.failedTries)
        return "\(headline)\n\(grid)\n\(InviteLink.appStoreURL.absoluteString)"
    }
}

/// Presents a `UIActivityViewController` with the rendered card image + caption.
struct ResultActivityView: UIViewControllerRepresentable {
    let data: ResultShareData
    var onComplete: (() -> Void)?

    func makeUIViewController(context: Context) -> UIActivityViewController {
        var items: [Any] = [ResultShare.caption(data)]
        if let image = ResultShare.renderCard(data) {
            items.insert(image, at: 0)
        }
        let controller = UIActivityViewController(activityItems: items, applicationActivities: nil)
        controller.completionWithItemsHandler = { _, completed, _, _ in
            if completed { onComplete?() }
        }
        return controller
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
