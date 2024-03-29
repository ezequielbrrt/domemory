//
//  MemorizeViewModel.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 24/09/20.
//

import SwiftUI
import AppLovinSDK

class MemorizeViewModel: ObservableObject {
    @Published private(set) var model: MemoryGame<String> = MemorizeViewModel.createMemoryGame()
    @Published var showPauseView: Bool = false
    @Published var timeRemaining: Int = 0
    @Published var showQuitView: Bool = false
    @Published var showWinView: Bool = false
    @Published var timer = Timer.publish (every: 1, on: .main, in: .common).autoconnect()

    var memorama: Memorama?
    var closeView: Bool = false
    var shouldShowPie: Bool!
    
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
    }
    
    // MARK: - Intent(s)
    func choose(card: MemoryGame<String>.Card) {
        model.choose(card: card)
    }
    
    func resetGame() {
        guard let auxMemorama = memorama else { return }
        model = MemoryGame<String>(numbersOfPairsOfCards: auxMemorama.items.count) { partIndex in
            return auxMemorama.items[partIndex]
        }

    }
    
    func reconnectTime() {
        self.timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    }
}

extension MemorizeViewModel {
    private func restartGame() {
        self.showPauseView = false
        self.resetGame()
        self.timeRemaining = getRemainingTime()
        self.timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    }
    
    private func getDifficulty() -> Difficulty {
        guard let userSettings = UserManageObject().getUserSettings() else { return .medium }
        return Difficulty(rawValue: userSettings.dificulty ?? "medium") ?? .medium
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
        reconnectTime()
    }
    
    func tapOnReloadGame() {
        restartGame()
    }
}

extension MemorizeViewModel: LoseModalViewModelListener {
    func tapOnTryAgain() {
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
    }
}

extension MemorizeViewModel: WinModalListener {
    func tapOnContinue() {
        self.showWinView = false
    }
}
