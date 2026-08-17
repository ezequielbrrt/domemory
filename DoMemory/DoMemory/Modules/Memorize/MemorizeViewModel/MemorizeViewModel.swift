//
//  MemorizeViewModel.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 24/09/20.
//

import SwiftUI
import Observation

/// What a MemorizeView instance is playing: a freely-picked board, today's
/// Daily Challenge, or a numbered Levels-mode board.
enum GameMode: Equatable {
    case free
    case dailyChallenge
    case level(Int)
}

/// Why the current game ended in a loss. Drives which rescue the LoseModal
/// offers — extra time is useless when you busted the mistake budget.
enum LoseReason: Equatable {
    case outOfTime
    case tooManyMistakes
}

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
    private(set) var mode: GameMode
    var isDailyChallenge: Bool { mode == .dailyChallenge }
    var levelNumber: Int? {
        if case .level(let number) = mode { return number }
        return nil
    }
    private(set) var lastEarnedStars: Int = 0
    /// Lives left today for Levels mode; nil outside of level play.
    private(set) var levelLivesRemaining: Int?
    /// Spendable star balance, mirrored from StarWalletService so the view can
    /// observe it. Only meaningful during level play.
    private(set) var starBalance: Int = 0
    var showSkipLevelConfirm = false
    /// Set when the game has been lost; nil while it's still playable. Replaces
    /// the old `timeRemaining == 0` check so a second lose reason can exist.
    private(set) var loseReason: LoseReason?
    var hasLost: Bool { loseReason != nil }
    /// Mistake budget for the current level; nil outside of level play.
    var maxFailures: Int? {
        guard let levelNumber else { return nil }
        return LevelCurve.maxFailures(for: levelNumber)
    }
    /// True in the last two mistakes of the budget, so the HUD can warn before
    /// the limit actually bites.
    var isNearFailureLimit: Bool {
        guard let maxFailures else { return false }
        return maxFailures - model.failedTries <= 2
    }
    var canWatchAdForLife: Bool {
        AdsService.shared.isRewardedConfigured(for: .levelsRewardedLife)
    }
    var closeView: Bool = false
    var shouldShowPie: Bool!

    private var timerTask: Task<Void, Never>?
    private var flipBackTask: Task<Void, Never>?
    private var hideMatchedTask: Task<Void, Never>?
    private var peekTask: Task<Void, Never>?
    private var mistakeLossTask: Task<Void, Never>?
    /// While set and in the future, the countdown holds. Kept as a deadline
    /// rather than cancelling `timerTask`, so freezing doesn't tear down the
    /// card flip-back scheduling the way `stopTimer()` would.
    private var frozenUntil: Date? {
        didSet { isFrozen = frozenUntil != nil }
    }
    /// Mirrors `frozenUntil` for the HUD. A separate observable flag rather than
    /// a computed property because nothing else changes while the clock is held
    /// — `timeRemaining` stops ticking — so the view would have no signal to
    /// re-render when the freeze starts or expires.
    private(set) var isFrozen = false
    private var hasLoggedGameFinished = false
    private var gameStartedAt: Date?
    private var lastRewardedAdDate: Date?

    var canOfferRewardedAds: Bool {
        AdsService.shared.isRewardedConfigured(for: .gameRewardedExtraTime)
            || AdsService.shared.isRewardedConfigured(for: .gameRewardedHint)
    }

    init(memorama: Memorama?, mode: GameMode = .free) {
        self.memorama = memorama
        self.mode = mode
        if case .level = mode {
            self.levelLivesRemaining = LevelLivesService.shared.livesRemaining()
            self.starBalance = StarWalletService.shared.balance
        }
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

    convenience init(memorama: Memorama?, isDailyChallenge: Bool) {
        self.init(memorama: memorama, mode: isDailyChallenge ? .dailyChallenge : .free)
    }

    convenience init(level: Int) {
        self.init(memorama: LevelProgressService.shared.board(for: level), mode: .level(level))
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
        if case .level(let levelNumber) = mode {
            return LevelCurve.seconds(for: levelNumber)
        }
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
        scheduleMistakeLossIfNeeded()
    }

    /// Ends the level once the mistake budget is spent. Deferred briefly so the
    /// player sees the mismatched pair that finished them off instead of having
    /// the modal slam over it.
    private func scheduleMistakeLossIfNeeded() {
        guard let maxFailures, loseReason == nil, mistakeLossTask == nil else { return }
        guard model.failedTries >= maxFailures else { return }
        mistakeLossTask = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .seconds(0.8))
            guard !Task.isCancelled, let self else { return }
            self.mistakeLossTask = nil
            // The clock can reach zero inside the delay above. Whichever
            // failure landed first is the real one, so never overwrite it —
            // otherwise a timeout would be offered the mistake rescue.
            guard self.loseReason == nil else { return }
            self.stopTimer()
            self.loseReason = .tooManyMistakes
            if let levelNumber = self.levelNumber {
                AnalyticsService.log(
                    .levelFailedByMistakes(
                        level: levelNumber,
                        maxFailures: maxFailures,
                        timeRemaining: self.timeRemaining
                    )
                )
            }
        }
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
                if let frozenUntil = self.frozenUntil {
                    guard Date() >= frozenUntil else { continue }
                    self.frozenUntil = nil
                }
                if self.timeRemaining > 0 {
                    self.timeRemaining -= 1
                }
                if self.timeRemaining == 0, self.loseReason == nil {
                    self.loseReason = .outOfTime
                }
            }
        }
        // stopTimer() above cancelled any pending mistake loss; re-arm it so
        // pausing mid-delay can't be used to play on past the budget.
        scheduleMistakeLossIfNeeded()
    }

    func stopTimer() {
        timerTask?.cancel()
        timerTask = nil
        flipBackTask?.cancel()
        flipBackTask = nil
        hideMatchedTask?.cancel()
        hideMatchedTask = nil
        mistakeLossTask?.cancel()
        mistakeLossTask = nil
        endPeekIfActive()
    }

    /// Ends an in-flight Peek, flipping the board back. Without this, pausing
    /// during a peek would cancel the scheduled flip-back and leave every card
    /// revealed for free.
    private func endPeekIfActive() {
        guard peekTask != nil else { return }
        peekTask?.cancel()
        peekTask = nil
        model.flipBackUnmatchedCards()
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
            if case .level = mode {
                AdsService.shared.loadRewardedAd(for: .levelsRewardedLife)
                AdsService.shared.loadRewardedAd(for: .levelsRewardedForgive)
            }
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
        if case .level(let levelNumber) = mode {
            AnalyticsService.log(.levelStarted(level: levelNumber))
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

    /// - Parameter allowInterstitial: pass `false` when the player has just
    ///   paid for the outcome. Charging 15★ to skip a level and then serving an
    ///   ad on the way out is the worst moment in the app to show one.
    private func logGameFinishedIfNeeded(result: String, allowInterstitial: Bool = true) {
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
        if case .level(let levelNumber) = mode {
            let progressService = LevelProgressService.shared
            let alreadyUnlockedNext = progressService.isUnlocked(levelNumber + 1)
            let earnedStars = progressService.recordCompletion(
                level: levelNumber,
                didWin: didWin,
                timeRemaining: timeRemaining,
                totalTime: getRemainingTime(),
                failedTries: model.failedTries
            )
            lastEarnedStars = earnedStars
            starBalance = StarWalletService.shared.balance
            AnalyticsService.log(.levelFinished(level: levelNumber, result: result, stars: earnedStars))
            if didWin && !alreadyUnlockedNext {
                AnalyticsService.log(.levelUnlocked(level: levelNumber + 1))
            }
            if !didWin {
                let remaining = LevelLivesService.shared.consumeLife()
                levelLivesRemaining = remaining
                AnalyticsService.log(.levelLifeConsumed(livesRemaining: remaining))
            }
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
        if allowInterstitial, shouldPresentCompletionInterstitial {
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
        self.frozenUntil = nil
        self.loseReason = nil
        self.starBalance = StarWalletService.shared.balance
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
        if case .level(let levelNumber) = mode {
            return levelNumber >= 25
        }
        let difficulty = getDifficulty()
        switch difficulty {
        case .easy: return false
        case .medium: return false
        case .hard: return true
        case .veryHard: return true
        }
    }

    /// Swaps in the next level's board in place (no navigation), with a fresh
    /// timer and card layout. Only valid when currently playing a level.
    fileprivate func advanceToNextLevel() {
        guard case .level(let currentLevel) = mode else { return }
        let nextLevel = currentLevel + 1
        mode = .level(nextLevel)
        memorama = LevelProgressService.shared.board(for: nextLevel)
        levelLivesRemaining = LevelLivesService.shared.livesRemaining()
        starBalance = StarWalletService.shared.balance
        frozenUntil = nil
        loseReason = nil
        resetGame()
        shouldShowPie = shouldShowPieByDifficulty()
        showWinView = false
        timeRemaining = getRemainingTime()
        startTimer()
        trackGameStarted(source: "next_level")
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

// MARK: - Star power-ups
extension MemorizeViewModel {
    /// Power-ups are a Levels-mode feature: stars are earned there, so they're
    /// spent there too.
    var canUsePowerUps: Bool { levelNumber != nil }

    func canAfford(_ powerUp: LevelPowerUp) -> Bool {
        starBalance >= powerUp.cost
    }

    /// Buys and immediately applies a power-up. No confirmation step — costs
    /// are small and the clock is running, so a modal here would cost more than
    /// a misfire does.
    func use(_ powerUp: LevelPowerUp) {
        guard let levelNumber, canAfford(powerUp) else { return }
        guard StarWalletService.shared.spend(powerUp.cost) else { return }
        starBalance = StarWalletService.shared.balance

        switch powerUp {
        case .extraTime:
            timeRemaining += LevelPowerUp.extraTimeSeconds
        case .peek:
            startPeek()
        case .freeze:
            frozenUntil = Date().addingTimeInterval(LevelPowerUp.freezeDuration)
        case .revealPair:
            revealHintPair()
        }

        AnalyticsService.log(
            .levelPowerUpUsed(
                powerUp: powerUp.rawValue,
                level: levelNumber,
                cost: powerUp.cost,
                balanceAfter: starBalance
            )
        )
    }

    private func startPeek() {
        peekTask?.cancel()
        flipBackTask?.cancel()
        flipBackTask = nil
        withAnimation(.easeInOut(duration: 0.3)) {
            model.revealAllUnmatchedForPeek()
        }
        peekTask = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .seconds(LevelPowerUp.peekDuration))
            guard !Task.isCancelled, let self else { return }
            self.peekTask = nil
            withAnimation(.easeInOut(duration: 0.4)) {
                self.model.flipBackUnmatchedCards()
            }
        }
    }
}

// MARK: - Star purchases from the lose modal
extension MemorizeViewModel {
    var canBuyLifeWithStars: Bool {
        levelNumber != nil
            && !PurchaseService.shared.hasRemovedAds
            && starBalance >= LevelPowerUp.lifeCost
    }

    var canSkipLevelWithStars: Bool {
        levelNumber != nil && starBalance >= LevelPowerUp.skipLevelCost
    }

    func tapOnBuyLifeWithStars() {
        guard canBuyLifeWithStars else { return }
        guard StarWalletService.shared.spend(LevelPowerUp.lifeCost) else { return }
        starBalance = StarWalletService.shared.balance
        levelLivesRemaining = LevelLivesService.shared.addLife()
        AnalyticsService.log(
            .levelLifePurchasedWithStars(cost: LevelPowerUp.lifeCost, balanceAfter: starBalance)
        )
        restartGame()
    }

    var canForgiveWithStars: Bool {
        levelNumber != nil && starBalance >= LevelPowerUp.forgiveCost
    }

    var canWatchAdToForgive: Bool {
        AdsService.shared.isRewardedConfigured(for: .levelsRewardedForgive)
    }

    func tapOnWatchAdToForgive() {
        presentRewardedAd(for: .levelsRewardedForgive) { [weak self] in
            self?.forgiveMistakes(LevelPowerUp.forgiveAmount, source: "ad")
        }
    }

    func tapOnForgiveWithStars() {
        guard canForgiveWithStars else { return }
        guard StarWalletService.shared.spend(LevelPowerUp.forgiveCost) else { return }
        starBalance = StarWalletService.shared.balance
        forgiveMistakes(LevelPowerUp.forgiveAmount, source: "stars")
    }

    /// Refunds mistakes and resumes the *same* board — matched pairs stay
    /// matched. Deliberately does not call `logGameFinishedIfNeeded`: the game
    /// isn't over, and recording it would consume a life and log a loss.
    private func forgiveMistakes(_ count: Int, source: String) {
        guard let levelNumber else { return }
        model.forgiveFailures(count)
        loseReason = nil
        // Guarantee a playable clock. `startTimer()`'s loop exits immediately
        // when `timeRemaining` is already 0, which would resume the board with
        // a permanently frozen countdown and no way to lose.
        timeRemaining = max(timeRemaining, LevelPowerUp.forgiveMinimumSeconds)
        startTimer()
        AnalyticsService.log(
            .levelMistakesForgiven(level: levelNumber, amount: count, source: source)
        )
    }

    func tapOnSkipLevelPrompt() {
        showSkipLevelConfirm = true
    }

    func tapOnCancelSkipLevel() {
        showSkipLevelConfirm = false
    }

    func tapOnConfirmSkipLevel() {
        guard let levelNumber, canSkipLevelWithStars else { return }
        guard StarWalletService.shared.spend(LevelPowerUp.skipLevelCost) else { return }
        starBalance = StarWalletService.shared.balance
        showSkipLevelConfirm = false
        // The attempt still counts as a loss — skipping buys the unlock, not a
        // clean record. Idempotent, so this is a no-op if it already fired.
        logGameFinishedIfNeeded(result: "lose", allowInterstitial: false)
        LevelProgressService.shared.skipLevel(levelNumber)
        AnalyticsService.log(
            .levelSkipped(level: levelNumber, cost: LevelPowerUp.skipLevelCost, balanceAfter: starBalance)
        )
        AnalyticsService.log(.levelUnlocked(level: levelNumber + 1))
        // Return to the map rather than auto-starting the next level, so the
        // lives gate there still decides whether they can play on.
        closeView = true
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
        guard levelNumber == nil || LevelLivesService.shared.hasLivesRemaining() else {
            // Out of lives: stay on LoseModal, which re-renders into its
            // out-of-lives state now that levelLivesRemaining is 0.
            return
        }
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
            // The modal is driven by `loseReason` now, not by the clock, so
            // adding time alone would leave it on screen.
            self.loseReason = nil
            self.startTimer()
        }
    }

    func tapOnWatchAdForLife() {
        presentRewardedAd(for: .levelsRewardedLife) { [weak self] in
            guard let self else { return }
            let updated = LevelLivesService.shared.addLife()
            self.levelLivesRemaining = updated
            AnalyticsService.log(.levelLifeGrantedFromAd(livesRemaining: updated))
            self.restartGame()
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
        self.closeView = true
        Task { @MainActor in
            ReviewRequestService.shared.registerSuccessfulGameWin()
        }
    }

    func tapOnNextLevel() {
        advanceToNextLevel()
    }
}
