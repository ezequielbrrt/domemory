//
//  MemorizeViewModel.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 24/09/20.
//

import SwiftUI

class MemorizeViewModel: ObservableObject {
    @Published private(set) var model: MemoryGame<String> = MemorizeViewModel.createMemoryGame()
    @Published var showPauseView: Bool = false
    @Published var timeRemaining: Int = 0
    @Published var timer = Timer.publish (every: 1, on: .main, in: .common).autoconnect()

    var memorama: Memorama?
    
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
    }
        
    private static func createMemoryGame() -> MemoryGame<String> {
        return MemoryGame<String>()
    }
    
    // MARK: - Access the model
    var cards: Array<MemoryGame<String>.Card> {
        model.cards
    }
    
    func getRemainingTime() -> Int {
        120
    }
    
    func getPieRemainingTime() -> Int {
        0
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
    
}

// MARK: LISTENERS
extension MemorizeViewModel: PauseModalListener {
    func tapOnGoHome() {
        
    }
    
    func tapOnResumeGame() {
        self.showPauseView = false
        self.timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
        
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
