//
//  MemorizeViewModel.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 24/09/20.
//

import SwiftUI
import Observation

@Observable
class MemorizeViewModel {
    private(set) var model: MemoryGame<String> = MemorizeViewModel.createMemoryGame()
    var showPauseView: Bool = false
    var timeRemaining: Int = 0
    var showQuitView: Bool = false
    var showWinView: Bool = false

    var memorama: Memorama?
    var closeView: Bool = false
    var shouldShowPie: Bool!

    private var timerTask: Task<Void, Never>?
    private var flipBackTask: Task<Void, Never>?
    private var hasLoggedGameFinished = false

    init(memorama: Memorama?) {
        self.memorama = memorama
        guard let auxMemorama = memorama else { return }
        if auxMemorama.isDoubleItem {
            self.model = MemoryGame<String>(numbersOfPairsOfCards: auxMemorama.items.count) { partIndex in
                return auxMemorama.items[partIndex]
            }
        } else {
            self.model = MemoryGame<String>(numbersOfCards: auxMemorama.items.count) { partIndex in
                return auxMemorama.items[partIndex]
            }
        }
        self.shouldShowPie = shouldShowPieByDifficulty()
    }

    private static func createMemoryGame() -> MemoryGame<String> {
        return MemoryGame<String>()
    }

    // MARK: - Access the model
    var cards: Array<MemoryGame<String>.Card> {
        model.cards
    }

    var failedTries: Int {
        model.failedTries
    }

    func getRemainingTime() -> Int {
        let difficulty = getDifficulty()
        switch difficulty {
        case .easy: return 110
        case .medium: return 60
        case .hard: return 60
        case .veryHard: return 70
        }
    }

    func getPieRemainingTime() -> Int {
        0
    }

    func getIfAllAreMatched() {
        let count = model.cards.count
        let matchedCount = self.model.cards.filter { $0.isMatched }
        showWinView = count == matchedCount.count
        if showWinView {
            logGameFinishedIfNeeded(result: "win")
        }
    }

    // MARK: - Intent(s)
    func choose(card: MemoryGame<String>.Card) {
        flipBackTask?.cancel()
        flipBackTask = nil
        model.choose(card: card)
        if AnalyticsService.shouldSample(AnalyticsService.cardTapSampleRate) {
            AnalyticsService.log(
                .cardTapped(
                    difficulty: gameDifficulty().rawValue,
                    cardsCount: model.cards.count,
                    failedTries: model.failedTries
                )
            )
        }
        scheduleFlipBackIfNeeded()
    }

    private func scheduleFlipBackIfNeeded() {
        let faceUpUnmatched = model.cards.filter { $0.isFaceUp && !$0.isMatched }
        guard faceUpUnmatched.count == 2 else { return }
        flipBackTask = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            withAnimation(.easeInOut(duration: 0.5)) {
                self?.model.flipBackUnmatchedCards()
            }
        }
    }

    func resetGame() {
        guard let auxMemorama = memorama else { return }
        model = MemoryGame<String>(numbersOfPairsOfCards: auxMemorama.items.count) { partIndex in
            return auxMemorama.items[partIndex]
        }
    }

    // MARK: - Timer
    func startTimer() {
        stopTimer()
        timerTask = Task { @MainActor [weak self] in
            while let self, !Task.isCancelled, self.timeRemaining > 0 {
                try? await Task.sleep(for: .seconds(1))
                guard !Task.isCancelled else { break }
                if self.timeRemaining > 0 {
                    self.timeRemaining -= 1
                    if self.timeRemaining == 0 {
                        self.logGameFinishedIfNeeded(result: "lose")
                    }
                }
            }
        }
    }

    func stopTimer() {
        timerTask?.cancel()
        timerTask = nil
        flipBackTask?.cancel()
        flipBackTask = nil
    }

    func reconnectTime() {
        startTimer()
    }

    func trackGameStarted(source: String) {
        hasLoggedGameFinished = false
        Task { @MainActor in
            AdsService.shared.loadInterstitial(for: .gameFinishedInterstitial)
        }
        AnalyticsService.log(
            .gameStarted(
                source: source,
                difficulty: gameDifficulty().rawValue,
                cardsCount: model.cards.count,
                isCustom: memorama?.id.hasPrefix("custom_") ?? false
            )
        )
    }

    func tapOnPause() {
        stopTimer()
        showPauseView.toggle()
        AnalyticsService.log(
            .pauseOpened(
                difficulty: gameDifficulty().rawValue,
                timeRemaining: timeRemaining
            )
        )
    }

    func tapOnQuitPrompt() {
        stopTimer()
        showQuitView.toggle()
    }

    private func logGameFinishedIfNeeded(result: String) {
        guard !hasLoggedGameFinished else { return }
        hasLoggedGameFinished = true
        if let memoramaID = memorama?.id {
            GameStatsService.shared.recordGameFinished(memoramaID: memoramaID, didWin: result == "win")
        }
        AnalyticsService.log(
            .gameFinished(
                result: result,
                difficulty: gameDifficulty().rawValue,
                cardsCount: model.cards.count,
                failedTries: model.failedTries,
                timeRemaining: timeRemaining,
                isCustom: memorama?.id.hasPrefix("custom_") ?? false
            )
        )
        Task { @MainActor in
            AdsService.shared.presentInterstitialEvery(3, for: .gameFinishedInterstitial)
        }
    }
}

extension MemorizeViewModel {
    private func restartGame() {
        self.showPauseView = false
        self.resetGame()
        self.timeRemaining = getRemainingTime()
        startTimer()
        trackGameStarted(source: "retry")
    }

    private func getDifficulty() -> Difficulty {
        guard let userSettings = UserManageObject().getUserSettings() else { return .medium }
        return Difficulty(rawValue: userSettings.dificulty ?? "medium") ?? .medium
    }

    private func gameDifficulty() -> Difficulty {
        if let memoramaDifficulty = memorama?.difficulty, let parsed = Difficulty(rawValue: memoramaDifficulty) {
            return parsed
        }
        return getDifficulty()
    }

    private func shouldShowPieByDifficulty() -> Bool {
        let difficulty = getDifficulty()
        switch difficulty {
        case .easy: return false
        case .medium: return false
        case .hard: return true
        case .veryHard: return true
        }
    }
}

// MARK: LISTENERS
extension MemorizeViewModel: PauseModalListener {
    func tapOnGoHome() {

    }

    func tapOnResumeGame() {
        self.showPauseView = false
        AnalyticsService.log(
            .resumeTapped(
                difficulty: gameDifficulty().rawValue,
                timeRemaining: timeRemaining
            )
        )
        startTimer()
    }

    func tapOnReloadGame() {
        AnalyticsService.log(
            .retryTapped(
                difficulty: gameDifficulty().rawValue,
                cardsCount: model.cards.count,
                source: "pause_modal"
            )
        )
        restartGame()
    }
}

extension MemorizeViewModel: LoseModalViewModelListener {
    func tapOnTryAgain() {
        AnalyticsService.log(
            .retryTapped(
                difficulty: gameDifficulty().rawValue,
                cardsCount: model.cards.count,
                source: "lose_modal"
            )
        )
        restartGame()
    }
}

extension MemorizeViewModel: QuitModalListener {
    func tapOnCancel() {
        self.showQuitView = false
        self.closeView = false
    }

    func tapOnExit() {
        self.showQuitView = false
        self.closeView = true
        AnalyticsService.log(
            .quitConfirmed(
                difficulty: gameDifficulty().rawValue,
                timeRemaining: timeRemaining,
                failedTries: model.failedTries
            )
        )
    }
}

extension MemorizeViewModel: WinModalListener {
    func tapOnContinue() {
        self.showWinView = false
        Task { @MainActor in
            ReviewRequestService.shared.registerSuccessfulGameWin()
        }
    }
}
