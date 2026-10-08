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
    /// Consecutive matches since the last mismatch. Grows on every match, and a
    /// mismatch drops it straight back to zero — that cliff is what gives each
    /// flip of a long run its tension. Drives the combo meter and the streak
    /// rewards (`StreakReward`); it never changes the win condition.
    private(set) var matchStreak: Int = 0
    /// The longest `matchStreak` reached on this board.
    private(set) var bestStreak: Int = 0
    /// Attempts charged or refunded by bombs: +1 for each mismatch that turned
    /// a bomb over, −1 for each bomb defused. Only a moves level reads it.
    private(set) var extraAttempts: Int = 0
    /// What the last `choose(card:)` did, for a caller that needs more than
    /// the before/after state can tell it — whether a bomb was involved.
    private(set) var lastOutcome: Outcome = .ignored

    enum Outcome: Equatable {
        /// The tap turned nothing: face up, matched, iced or locked already.
        case ignored
        /// The first card of a pair was turned over.
        case firstFlip
        case match(involvedBomb: Bool)
        case mismatch(involvedBomb: Bool)
    }

    var matchedPairs: Int { cards.filter(\.isMatched).count / 2 }

    /// Pairs turned over so far, bombs included: what a moves budget spends.
    var attempts: Int { matchedPairs + failedTries + extraAttempts }

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
    /// board returns to a clean state. Ice is opaque: a frozen card keeps its
    /// face hidden, otherwise Peek would make the blocker free to bypass.
    mutating func revealAllUnmatchedForPeek() {
        for index in cards.indices where !cards[index].isMatched && !cards[index].isCovered {
            cards[index].isFaceUp = true
        }
    }

    /// Prefers a pair with nothing covering it. When every remaining pair has
    /// an iced or locked card the hint still has to show *something*, so it
    /// clears that pair's cover as part of the reveal rather than failing.
    mutating func revealUnmatchedPairForHint() -> Bool {
        let unmatched = cards.filter { !$0.isMatched }
        let clearPair = unmatched.first { candidate in
            unmatched.allSatisfy { $0.itemId != candidate.itemId || !$0.isCovered }
        }
        guard let itemID = (clearPair ?? unmatched.first)?.itemId else { return false }
        var revealedCount = 0
        for index in cards.indices {
            let shouldReveal = cards[index].itemId == itemID && !cards[index].isMatched
            if shouldReveal {
                cards[index].isFrozen = false
                cards[index].lockedForMatches = 0
            }
            cards[index].isFaceUp = shouldReveal
            if shouldReveal {
                revealedCount += 1
            }
        }
        return revealedCount >= 2
    }

    // MARK: - Blockers

    /// Puts ice on `count` face-down cards. Never both cards of one pair, so a
    /// pair is at most one crack away from being playable and the blocker reads
    /// as "work around it", not "two dead taps in a row". Cards are already
    /// shuffled, so walking them in order is a random pick. Clamped to what the
    /// board can take under that rule.
    mutating func freezeCards(count: Int) {
        for index in pickBlockerIndices(count: count) {
            cards[index].isFrozen = true
        }
    }

    /// Arms `count` cards as bombs, under the same one-per-pair rule as ice.
    mutating func placeBombs(count: Int) {
        for index in pickBlockerIndices(count: count) {
            cards[index].isBomb = true
        }
    }

    /// Chains `count` cards shut for the next `matches` pairs found, one per
    /// pair like the others.
    mutating func lockCards(count: Int, forMatches matches: Int) {
        guard matches > 0 else { return }
        for index in pickBlockerIndices(count: count) {
            cards[index].lockedForMatches = matches
        }
    }

    /// Indices of up to `count` plain face-down cards, at most one per pair
    /// and never one that already carries a blocker, so modifiers don't stack
    /// on one card and no pair is doubly covered.
    private func pickBlockerIndices(count: Int) -> [Int] {
        guard count > 0 else { return [] }
        var picked: [Int] = []
        var takenPairs = Set(cards.filter(\.hasModifier).map(\.itemId))
        for index in cards.indices where picked.count < count {
            let card = cards[index]
            guard !card.isFaceUp, !card.isMatched, !card.hasModifier, !takenPairs.contains(card.itemId) else { continue }
            picked.append(index)
            takenPairs.insert(card.itemId)
        }
        return picked
    }

    /// A tap on ice cracks it instead of flipping the card. Returns whether
    /// anything changed, so a tap on a clear card stays silent here and is left
    /// to `choose(card:)`.
    @discardableResult
    mutating func crack(card: Card) -> Bool {
        guard let index = cards.firstIndex(matching: card), cards[index].isFrozen else { return false }
        cards[index].isFrozen = false
        return true
    }

    mutating func choose(card: Card) {
        lastOutcome = .ignored
        if let chosenIndex = cards.firstIndex(matching: card),
           !cards[chosenIndex].isFaceUp, !cards[chosenIndex].isMatched, !cards[chosenIndex].isCovered {
            if let potencialMatchIndex = indexOfTheOneAndOnlyFaceUpCard {
                let involvedBomb = cards[chosenIndex].isBomb || cards[potencialMatchIndex].isBomb
                if cards[chosenIndex].itemId == cards[potencialMatchIndex].itemId {
                    cards[chosenIndex].isMatched = true
                    cards[potencialMatchIndex].isMatched = true
                    matchStreak += 1
                    bestStreak = max(bestStreak, matchStreak)
                    if involvedBomb {
                        // Defused: the bomb is gone and the bet paid off.
                        cards[chosenIndex].isBomb = false
                        cards[potencialMatchIndex].isBomb = false
                        extraAttempts -= 1
                    }
                    // Every chain on the board loosens by one.
                    for index in cards.indices where cards[index].lockedForMatches > 0 {
                        cards[index].lockedForMatches -= 1
                    }
                    lastOutcome = .match(involvedBomb: involvedBomb)
                } else {
                    failedTries += 1
                    matchStreak = 0
                    if involvedBomb { extraAttempts += 1 }
                    lastOutcome = .mismatch(involvedBomb: involvedBomb)
                }
                self.cards[chosenIndex].isFaceUp = true
            } else {
                indexOfTheOneAndOnlyFaceUpCard = chosenIndex
                lastOutcome = .firstFlip
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
        /// Under ice: the card ignores `choose` and shows no face to Peek until a
        /// tap cracks it (`crack(card:)`). A level blocker, never set in free play.
        var isFrozen: Bool = false
        /// Armed: a mismatch that turns this card over costs the clock or a
        /// move; matching its pair defuses it for a bonus. Visible on the back,
        /// so flipping it is a choice.
        var isBomb: Bool = false
        /// Chained: ignores `choose` and Peek until this many more pairs have
        /// been matched anywhere on the board. Zero means free.
        var lockedForMatches: Int = 0

        var isLocked: Bool { lockedForMatches > 0 }
        /// Can't be turned over right now, by ice or by a chain.
        var isCovered: Bool { isFrozen || isLocked }
        var hasModifier: Bool { isFrozen || isBomb || isLocked }
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
