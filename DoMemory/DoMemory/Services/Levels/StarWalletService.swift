//
//  StarWalletService.swift
//  DoMemory
//
//  Spendable half of the Levels star economy. Stars earned by clearing levels
//  are credited here by LevelProgressService and spent on power-ups, extra
//  lives and level skips. Kept separate from the lifetime star total so
//  spending never walks the player's mastery score backwards.
//

import Foundation

final class StarWalletService {
    static let shared = StarWalletService()

    private let defaults = UserDefaults.standard
    private init() {}

    private enum Key {
        static let balance = "levels.wallet.balance"
    }

    /// Stars available to spend right now.
    var balance: Int {
        LevelProgressService.shared.migrateStarTotalsIfNeeded()
        return defaults.integer(forKey: Key.balance)
    }

    func canAfford(_ cost: Int) -> Bool {
        balance >= cost
    }

    /// Adds stars to the wallet. Returns the new balance.
    @discardableResult
    func credit(_ amount: Int) -> Int {
        guard amount > 0 else { return balance }
        let updated = balance + amount
        defaults.set(updated, forKey: Key.balance)
        return updated
    }

    /// Deducts `amount` if the player can afford it. Returns false and leaves
    /// the balance untouched when they can't, so callers can treat a `false`
    /// return as "purchase did not happen".
    @discardableResult
    func spend(_ amount: Int) -> Bool {
        guard amount > 0 else { return true }
        let current = balance
        guard current >= amount else { return false }
        defaults.set(current - amount, forKey: Key.balance)
        return true
    }

    /// Seeds the opening balance during migration. Only used by
    /// `LevelProgressService.migrateStarTotalsIfNeeded()`.
    func seedBalance(_ amount: Int) {
        defaults.set(max(0, amount), forKey: Key.balance)
    }
}
