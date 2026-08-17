//
//  MemoryGame.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 24/09/20.
//

import Foundation

struct MemoryGame<CardContent> where CardContent: Equatable {
    private(set) var cards: Array<Card>
    private(set) var failedTries: Int = 0
    
    private var indexOfTheOneAndOnlyFaceUpCard: Int? {
        get { cards.indices.filter { cards[$0].isFaceUp }.only }
        set {
            for index in cards.indices {
                cards[index].isFaceUp = index == newValue
            }
        }
    }
    
    mutating func flipBackUnmatchedCards() {
        for index in cards.indices where cards[index].isFaceUp && !cards[index].isMatched {
            cards[index].isFaceUp = false
        }
    }

    mutating func hideMatchedFaceUpCards() {
        for index in cards.indices where cards[index].isFaceUp && cards[index].isMatched {
            cards[index].isFaceUp = false
        }
    }

    /// Refunds failed matches after a "forgive mistakes" rescue. Floors at zero.
    mutating func forgiveFailures(_ count: Int) {
        guard count > 0 else { return }
        failedTries = max(0, failedTries - count)
    }

    /// Flips every still-unmatched card face up for the Peek power-up. Ended by
    /// `flipBackUnmatchedCards()`, which also clears any half-made guess so the
    /// board returns to a clean state.
    mutating func revealAllUnmatchedForPeek() {
        for index in cards.indices where !cards[index].isMatched {
            cards[index].isFaceUp = true
        }
    }

    mutating func revealUnmatchedPairForHint() -> Bool {
        guard let itemID = cards.first(where: { !$0.isMatched })?.itemId else { return false }
        var revealedCount = 0
        for index in cards.indices {
            let shouldReveal = cards[index].itemId == itemID && !cards[index].isMatched
            cards[index].isFaceUp = shouldReveal
            if shouldReveal {
                revealedCount += 1
            }
        }
        return revealedCount >= 2
    }

    mutating func choose(card: Card) {
        if let chosenIndex = cards.firstIndex(matching: card), !cards[chosenIndex].isFaceUp, !cards[chosenIndex].isMatched {
            if let potencialMatchIndex = indexOfTheOneAndOnlyFaceUpCard {
                if cards[chosenIndex].itemId == cards[potencialMatchIndex].itemId {
                    cards[chosenIndex].isMatched = true
                    cards[potencialMatchIndex].isMatched = true
                } else {
                    failedTries += 1
                }
                self.cards[chosenIndex].isFaceUp = true
            } else {
                indexOfTheOneAndOnlyFaceUpCard = chosenIndex
            }
        }
    }
    
    init() {
        cards = Array<Card>()
    }
    
    init(numbersOfCards: Int, cardContentFactory: (Int) -> CardContent) {
        cards = Array<Card>()
        for pairIndex in 0..<numbersOfCards {
            
            let content = cardContentFactory(pairIndex)
            if pairIndex % 2 == 0 {
                cards.append(Card(content: content, id: pairIndex * 2, itemId: pairIndex))
            } else {
                cards.append(Card(content: content, id: pairIndex * 2 + 1, itemId: pairIndex - 1))
            }
        }
        cards.shuffle()
    }
    
    init(numbersOfPairsOfCards: Int, cardContentFactory: (Int) -> CardContent) {
        cards = Array<Card>()
        for pairIndex in 0..<numbersOfPairsOfCards {
            let content = cardContentFactory(pairIndex)
            cards.append(Card(content: content, id: pairIndex * 2, itemId: pairIndex))
            cards.append(Card(content: content, id: pairIndex * 2 + 1, itemId: pairIndex))
        }
        cards.shuffle()
    }
        
    struct Card: Identifiable {
        var isFaceUp: Bool = false {
            didSet {
                isFaceUp ? startUsingBonusTime() : stopUsingBonusTime()
            }
        }
        var isMatched: Bool = false {
            didSet {
                stopUsingBonusTime()
            }
        }
        var content: CardContent
        var id: Int
        var itemId: Int
        //var shouldShowPie: Bool
        
        // MARK: - Bonus
        var bonusTimeLimit: TimeInterval = 2
        
        private var faceUpTime: TimeInterval {
            if let lastFaceUpDate = self.lastFaceUpDate {
                return pastFaceUpTime + Date().timeIntervalSince(lastFaceUpDate)
            } else {
                return pastFaceUpTime
            }
        }
        
        var lastFaceUpDate: Date?
        var pastFaceUpTime: TimeInterval = 0
        
        var bonusTimeRemaining: TimeInterval {
            max(0, bonusTimeLimit - faceUpTime)
        }
        
        var bonusRemaining: Double {
            (bonusTimeLimit > 0 && bonusTimeRemaining > 0) ? bonusTimeRemaining/bonusTimeLimit : 0
        }
        
        var hasEarnedBounus: Bool {
            isMatched && bonusTimeRemaining > 0
        }
        
        var isConsumingBonusTime: Bool {
            isFaceUp && !isMatched && bonusTimeRemaining > 0
        }
        
        private mutating func startUsingBonusTime() {
            if isConsumingBonusTime, lastFaceUpDate == nil {
                lastFaceUpDate = Date()
            }
        }
        
        private mutating func stopUsingBonusTime() {
            pastFaceUpTime = faceUpTime
            self.lastFaceUpDate = nil
        }
    }
}
