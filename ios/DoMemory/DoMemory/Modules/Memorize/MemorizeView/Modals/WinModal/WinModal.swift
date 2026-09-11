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
    var difficultyTitle: String = ""
    var isDailyChallenge: Bool = false
    var streak: Int = 0
    var levelNumber: Int? = nil
    var starsEarned: Int = 0
    /// Whether another level follows this one. Endless Levels always has one;
    /// a finite season does not on its last board, where offering "Next Level"
    /// would promise a level that does not exist.
    var hasNextLevel: Bool = true

    @State private var showShareSheet = false
    /// How many of the earned stars have been given the go-ahead to start
    /// their pop-in, and how many have actually finished it. Kept separate so
    /// a star can be *playing* its animation (started, not yet completed)
    /// while the next one is still waiting its turn — that gap is what makes
    /// the row read as a staggered sequence instead of one simultaneous pop.
    @State private var startedStarCount = 0
    @State private var completedStarCount = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// A level was cleared *and* there is another to play.
    private var offersNextLevel: Bool { levelNumber != nil && hasNextLevel }

    private var shareData: ResultShareData {
        ResultShareData(
            pairs: pairsCount,
            timeRemaining: timeRemaining,
            failedTries: failedTries,
            difficultyTitle: difficultyTitle,
            isDailyChallenge: isDailyChallenge,
            streak: streak
        )
    }

    private func primaryAction() {
        if offersNextLevel {
            listener?.tapOnNextLevel()
        } else {
            listener?.tapOnContinue()
        }
    }

    /// On a season's final level the only sensible move is back to the map, so
    /// the primary button takes over the secondary's label instead of adding a
    /// new string. Celebratory season-complete copy is a later phase's job.
    private var primaryTitle: String {
        if offersNextLevel { return Strings.nextLevel }
        return levelNumber != nil ? Strings.backToLevels : Strings.goToMenu
    }

    /// One slot of the stars-earned row.
    ///
    /// A slot beyond `starsEarned` is always the static dim outline — it never
    /// earned a star, so there is nothing to animate. An earned slot starts
    /// dim too, waiting its turn; `revealStars()` flips it to the Lottie pop
    /// once it starts, and its own completion callback flips it to the final
    /// static filled state. Reduce-motion players skip straight to filled.
    @ViewBuilder
    private func starView(for index: Int) -> some View {
        if index >= starsEarned {
            Image(systemName: "star")
                .foregroundStyle(Color.textMuted.opacity(0.3))
        } else if reduceMotion || index < completedStarCount {
            Image(systemName: "star.fill")
                .foregroundStyle(Color.hardAmber)
        } else if index < startedStarCount {
            LottieView(name: "star-pop", loopMode: .playOnce) {
                completedStarCount = max(completedStarCount, index + 1)
            }
            .frame(width: 28, height: 28)
        } else {
            Image(systemName: "star")
                .foregroundStyle(Color.textMuted.opacity(0.3))
        }
    }

    /// Starts each earned star's pop-in a beat after the previous one, so the
    /// row reads as a small staggered sequence of rewards instead of three
    /// stars popping at once. Each star's own Lottie completion callback (see
    /// `starView(for:)`) is what actually marks it done; this loop only paces
    /// when the *next* one gets its go-ahead.
    private func revealStars() async {
        guard !reduceMotion, starsEarned > 0 else { return }
        for index in 0..<starsEarned {
            startedStarCount = index + 1
            try? await Task.sleep(nanoseconds: 180_000_000)
        }
    }

    var body: some View {
        ZStack {
            Color.overlayBackdrop
                .ignoresSafeArea()
                .background(.ultraThinMaterial)

            // A one-shot celebratory burst behind the card. Skipped for
            // reduce-motion players, who see the modal with no motion at all
            // rather than a burst that plays regardless of the setting.
            if !reduceMotion {
                LottieView(name: "confetti-burst", loopMode: .playOnce)
                    .allowsHitTesting(false)
                    .frame(width: 400, height: 400)
            }

            VStack(spacing: 6) {
                Text("😎")
                    .font(.system(size: 68))
                    .padding(.bottom, 8)

                Text(levelNumber != nil ? Strings.levelCleared : Strings.youWin)
                    .font(.system(size: 36, weight: .heavy, design: .rounded))
                    .foregroundStyle(Color.primaryColor)
                    .multilineTextAlignment(.center)

                if let levelNumber {
                    Text(Strings.levelTitle(levelNumber))
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.textMuted)

                    HStack(spacing: 6) {
                        ForEach(0..<3, id: \.self) { index in
                            starView(for: index)
                        }
                    }
                    .font(.system(size: 24))
                    .padding(.top, 4)
                    .padding(.bottom, 8)
                    .task { await revealStars() }
                } else {
                    Text(Strings.youWinDescription)
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundStyle(Color.textMuted)
                        .multilineTextAlignment(.center)
                        .lineSpacing(4)
                        .padding(.bottom, 8)
                }

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

                Button(action: { showShareSheet = true }) {
                    Label(Strings.shareResult, systemImage: "square.and.arrow.up")
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.primaryColor)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(
                            Capsule().strokeBorder(Color.primaryColor.opacity(0.45), lineWidth: 1.5)
                        )
                }
                .buttonStyle(.plain)
                .hapticTap()
                .padding(.bottom, 10)

                Button(action: { primaryAction() }) {
                    Text(primaryTitle)
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

                // Hidden on a season's final level: the primary button already
                // says "Back to Levels" there, and two buttons doing the same
                // thing reads as a bug.
                if offersNextLevel {
                    Button(action: { listener?.tapOnContinue() }) {
                        Text(Strings.backToLevels)
                            .font(.system(size: 15, weight: .semibold, design: .rounded))
                            .foregroundStyle(Color.textMuted)
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 8)
                }
            }
            .sheet(isPresented: $showShareSheet) {
                ResultActivityView(data: shareData) {
                    AnalyticsService.log(.resultShared(source: isDailyChallenge ? "daily_challenge" : "win"))
                }
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

#Preview("Free play") {
    WinModal()
}

#Preview("Level cleared, 3 stars") {
    WinModal(levelNumber: 12, starsEarned: 3)
}
