//
//  GameStatsService.swift
//  DoMemory
//
//  Created by OpenAI on 06/04/26.
//

import Foundation

struct GameStats {
    let playedCount: Int
    let wonCount: Int
}

final class GameStatsService {
    static let shared = GameStatsService()

    private let keyPrefix = "stats"
    private let playedSuffix = "played"
    private let wonSuffix = "won"

    private init() {}

    func stats(for memoramaID: String) -> GameStats {
        GameStats(
            playedCount: UserDefaults.standard.integer(forKey: key(for: memoramaID, suffix: playedSuffix)),
            wonCount: UserDefaults.standard.integer(forKey: key(for: memoramaID, suffix: wonSuffix))
        )
    }

    func recordGameFinished(memoramaID: String, didWin: Bool) {
        let playedKey = key(for: memoramaID, suffix: playedSuffix)
        let wonKey = key(for: memoramaID, suffix: wonSuffix)

        UserDefaults.standard.set(UserDefaults.standard.integer(forKey: playedKey) + 1, forKey: playedKey)

        if didWin {
            UserDefaults.standard.set(UserDefaults.standard.integer(forKey: wonKey) + 1, forKey: wonKey)
        }
    }

    func resetStats(for memoramaID: String) {
        UserDefaults.standard.removeObject(forKey: key(for: memoramaID, suffix: playedSuffix))
        UserDefaults.standard.removeObject(forKey: key(for: memoramaID, suffix: wonSuffix))
    }

    private func key(for memoramaID: String, suffix: String) -> String {
        "\(keyPrefix).\(memoramaID).\(suffix)"
    }
}
