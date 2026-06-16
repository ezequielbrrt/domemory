//
//  MemorizeViewModel.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 24/09/20.
//

import SwiftUI
import Observation

@Observable
@MainActor
class MemorizeViewModel {
    private(set) var model: MemoryGame<String> = MemorizeViewModel.createMemoryGame()
    var showPauseView: Bool = false
    var timeRemaining: Int = 0
    var showQuitView: Bool = false
    var showWinView: Bool = false
    var isRewardedAdInProgress: Bool = false

    var memorama: Memorama?
    let isDailyChallenge: Bool
    var closeView: Bool = false
    var shouldShowPie: Bool!

    private var timerTask: Task<Void, Never>?
    private var flipBackTask: Task<Void, Never>?
    private var hideMatchedTask: Task<Void, Never>?
    private var hasLoggedGameFinished = false
    private var gameStartedAt: Date?
    private var lastRewardedAdDate: Date?

    var canOfferRewardedAds: Bool {
        AdsService.shared.isRewardedConfigured(for: .gameRewardedExtraTime)
            || AdsService.shared.isRewardedConfigured(for: .gameRewardedHint)
    }

    init(memorama: Memorama?, isDailyChallenge: Bool = false) {
        self.memorama = memorama
        self.isDailyChallenge = isDailyChallenge
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

    var difficultyDisplayTitle: String {
        switch gameDifficulty() {
        case .easy: return Strings.easy
        case .medium: return Strings.medium
        case .hard: return Strings.hard
        case .veryHard: return Strings.veryHard
        }
    }

    var dailyChallengeStreak: Int {
        isDailyChallenge ? DailyChallengeService.shared.currentStreak : 0
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
        scheduleMatchedHideIfNeeded()
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

    private func scheduleMatchedHideIfNeeded() {
        let faceUpMatched = model.cards.filter { $0.isFaceUp && $0.isMatched }
        guard !faceUpMatched.isEmpty else { return }
        hideMatchedTask?.cancel()
        hideMatchedTask = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .seconds(1))
            guard !Task.isCancelled else { return }
            withAnimation(.easeInOut(duration: 0.35)) {
                self?.model.hideMatchedFaceUpCards()
            }
            self?.getIfAllAreMatched()
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
                }
            }
        }
    }

    func stopTimer() {
        timerTask?.cancel()
        timerTask = nil
        flipBackTask?.cancel()
        flipBackTask = nil
        hideMatchedTask?.cancel()
        hideMatchedTask = nil
    }

    func reconnectTime() {
        startTimer()
    }

    func trackGameStarted(source: String) {
        hasLoggedGameFinished = false
        gameStartedAt = Date()
        Task { @MainActor in
            AdsService.shared.loadInterstitial(for: .gameFinishedInterstitial)
            AdsService.shared.loadRewardedAd(for: .gameRewardedExtraTime)
            AdsService.shared.loadRewardedAd(for: .gameRewardedHint)
        }
        AnalyticsService.log(
            .gameStarted(
                source: source,
                difficulty: gameDifficulty().rawValue,
                cardsCount: model.cards.count,
                isCustom: memorama?.id.hasPrefix("custom_") ?? false
            )
        )
        if isDailyChallenge {
            AnalyticsService.log(.dailyChallengeStarted(streak: DailyChallengeService.shared.currentStreak))
        }
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
        let didWin = result == "win"
        if let memoramaID = memorama?.id {
            GameStatsService.shared.recordGameFinished(memoramaID: memoramaID, didWin: didWin)
        }
        ProfileStatsService.shared.recordGameFinished(
            didWin: didWin,
            isPerfect: didWin && model.failedTries == 0,
            difficulty: gameDifficulty(),
            timeRemaining: timeRemaining
        )
        if isDailyChallenge {
            let streak = DailyChallengeService.shared.recordCompletion(didWin: didWin)
            AnalyticsService.log(.dailyChallengeFinished(result: result, streak: streak))
            if didWin, [3, 7, 14, 30, 100].contains(streak) {
                AnalyticsService.log(.streakMilestone(days: streak))
            }
            NotificationService.shared.refreshStreakAtRiskReminder()
        }
        NotificationService.shared.scheduleInactivityReminder()
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
        if shouldPresentCompletionInterstitial {
            let frequency = interstitialFrequency()
            Task { @MainActor in
                AdsService.shared.presentInterstitialEvery(frequency, for: .gameFinishedInterstitial)
            }
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

    private var shouldPresentCompletionInterstitial: Bool {
        guard let gameStartedAt else { return false }
        guard Date().timeIntervalSince(gameStartedAt) >= 20 else { return false }
        if let lastRewardedAdDate,
           Date().timeIntervalSince(lastRewardedAdDate) < 60 {
            return false
        }
        return true
    }

    private func interstitialFrequency() -> Int {
        switch gameDifficulty() {
        case .easy, .medium: return 3
        case .hard, .veryHard: return 2
        }
    }

    private func presentRewardedAd(for placement: AdPlacement, reward: @escaping () -> Void) {
        guard !isRewardedAdInProgress else { return }
        isRewardedAdInProgress = true
        AnalyticsService.log(.adLifecycle(placement: placement.rawValue, action: "requested"))
        AdsService.shared.presentRewardedAd(
            for: placement,
            rewardHandler: { [weak self] in
                guard let self else { return }
                self.lastRewardedAdDate = Date()
                AnalyticsService.log(.adLifecycle(placement: placement.rawValue, action: "reward_earned"))
                reward()
            },
            completion: { [weak self] didEarnReward in
                guard let self else { return }
                self.isRewardedAdInProgress = false
                AnalyticsService.log(.adLifecycle(placement: placement.rawValue, action: didEarnReward ? "dismissed_rewarded" : "dismissed_unrewarded"))
            }
        )
    }

    private func revealHintPair() {
        guard model.revealUnmatchedPairForHint() else { return }
        scheduleFlipBackIfNeeded()
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

    func tapOnRewardedHint() {
        presentRewardedAd(for: .gameRewardedHint) { [weak self] in
            guard let self else { return }
            self.showPauseView = false
            self.revealHintPair()
            self.startTimer()
        }
    }
}

extension MemorizeViewModel: LoseModalViewModelListener {
    func tapOnTryAgain() {
        logGameFinishedIfNeeded(result: "lose")
        AnalyticsService.log(
            .retryTapped(
                difficulty: gameDifficulty().rawValue,
                cardsCount: model.cards.count,
                source: "lose_modal"
            )
        )
        restartGame()
    }

    func tapOnGoToMenuAfterLose() {
        logGameFinishedIfNeeded(result: "lose")
        closeView = true
    }

    func tapOnRewardedExtraTime() {
        presentRewardedAd(for: .gameRewardedExtraTime) { [weak self] in
            guard let self else { return }
            self.timeRemaining = max(self.timeRemaining, 0) + 30
            self.startTimer()
        }
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
