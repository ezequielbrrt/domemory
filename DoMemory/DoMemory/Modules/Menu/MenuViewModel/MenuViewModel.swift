//
//  MenuViewModel.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI
import Observation
import FirebaseCore
import FirebaseDatabase
import FirebaseAuth

@Observable
class MenuViewModel {
    enum VisibleTab {
        case all
        case mine
    }

    var memoramaArray: [Memorama] = []
    var isLoading: Bool = false
    private(set) var favoriteIDs: Set<String> = []
    private var customMemoramas: [Memorama] = []

    init() {
        ref = Database.database().reference()
        isLoading = true
        getPoints()
        difficulty = getDifficulty()
        loadFavorites()
        loadCustomMemoramas()
        AnalyticsService.log(.menuLoaded(difficulty: currentDifficulty))
        getData()
    }

    public func getUserPoints() -> String {
        return String(points)
    }

    var currentDifficulty: String {
        difficulty?.rawValue ?? Difficulty.medium.rawValue
    }

    func isFavorite(id: String) -> Bool {
        favoriteIDs.contains(id)
    }

    func toggleFavorite(id: String) {
        if favoriteIDs.contains(id) {
            favoriteIDs.remove(id)
        } else {
            favoriteIDs.insert(id)
        }
        UserDefaults.standard.set(Array(favoriteIDs), forKey: UserDefaultsKeys.favoriteIDs)
        AnalyticsService.log(.favoriteToggled(gameID: id, isFavorite: favoriteIDs.contains(id)))
        applySort()
    }

    func stats(for memoramaID: String) -> GameStats {
        GameStatsService.shared.stats(for: memoramaID)
    }

    func randomGame(for tab: VisibleTab) -> Memorama? {
        switch tab {
        case .all:
            return memoramaArray.filter { !$0.id.hasPrefix("custom_") }.randomElement()
        case .mine:
            return memoramaArray.filter { $0.id.hasPrefix("custom_") }.randomElement()
        }
    }

    // MARK: Private
    private var ref: DatabaseReference!
    private var difficulty: Difficulty!
    private var points: Int!
    private var allGames: [Memorama] = []
}

// MARK: Custom Memoramas
extension MenuViewModel {
    func addCustomMemorama(_ memorama: Memorama) {
        customMemoramas.append(memorama)
        saveCustomMemoramas()
        rebuildMemoramaArray()
        AnalyticsService.log(
            .customMemoramaCreated(
                gameID: memorama.id,
                difficulty: memorama.difficulty,
                cardsCount: memorama.items.count
            )
        )
    }

    func deleteCustomMemorama(id: String) {
        customMemoramas.removeAll { $0.id == id }
        GameStatsService.shared.resetStats(for: id)
        saveCustomMemoramas()
        rebuildMemoramaArray()
        AnalyticsService.log(.customMemoramaDeleted(gameID: id))
    }

    private func loadCustomMemoramas() {
        guard let data = UserDefaults.standard.data(forKey: UserDefaultsKeys.customMemoramas),
              let memoramas = try? JSONDecoder().decode([Memorama].self, from: data) else { return }
        customMemoramas = memoramas
    }

    private func saveCustomMemoramas() {
        guard let data = try? JSONEncoder().encode(customMemoramas) else { return }
        UserDefaults.standard.set(data, forKey: UserDefaultsKeys.customMemoramas)
    }

    private func rebuildMemoramaArray() {
        let filteredFirebase = allGames.filter { memorama in
            let difficultyEnum = Difficulty(rawValue: memorama.difficulty) ?? .medium
            return difficultyEnum == difficulty
        }
        memoramaArray = filteredFirebase + customMemoramas
        applySort()
    }
}

// MARK: Favorites
extension MenuViewModel {
    private func loadFavorites() {
        let stored = UserDefaults.standard.stringArray(forKey: UserDefaultsKeys.favoriteIDs) ?? []
        favoriteIDs = Set(stored)
    }

    private func applySort() {
        memoramaArray = memoramaArray.sorted { a, b in
            let aFav = favoriteIDs.contains(a.id)
            let bFav = favoriteIDs.contains(b.id)
            if aFav == bFav { return false }
            return aFav && !bFav
        }
    }
}

extension MenuViewModel: SettingsListener {
    func onCloseView() {
        if self.difficulty == getDifficulty() { return }
        self.difficulty = getDifficulty()
        self.isLoading = true
        self.memoramaArray = []
        self.allGames = []
        getData()
    }
}

// MARK: INTERNAL DATA
extension MenuViewModel {
    private func getPoints() {
        guard let userSettings = UserManageObject().getUserSettings() else { return  }
        points = Int(userSettings.points)
    }

    private func getDifficulty() -> Difficulty {
        guard let userSettings = UserManageObject().getUserSettings() else { return .medium }
        return Difficulty(rawValue: userSettings.dificulty ?? "medium") ?? .medium
    }
}

// MARK: External Data
extension MenuViewModel {
    private func getData() {
        guard FirebaseApp.app() != nil else {
            isLoading = false
            rebuildMemoramaArray()
            print("Firebase is not configured. Skipping remote memorama fetch.")
            return
        }

        Auth.auth().signInAnonymously() { [weak self] (authResult, error) in
            guard let self else { return }

            if let error {
                print("Firebase anonymous sign-in failed")
                print(error)
                self.isLoading = false
                return
            }

            if authResult != nil {
                self.ref.child("data").observeSingleEvent(of: .value) { [weak self] snapshot in
                    guard let self else { return }

                    do {
                        guard let array = snapshot.value as? [[String: Any]] else {
                            self.allGames = []
                            self.rebuildMemoramaArray()
                            self.isLoading = false
                            return
                        }

                        let data = try JSONSerialization.data(withJSONObject: array, options: [])
                        var memoramaArrayAux = try JSONDecoder().decode([Memorama].self, from: data)
                        memoramaArrayAux.shuffle()
                        self.allGames = memoramaArrayAux
                        self.rebuildMemoramaArray()
                        AnalyticsService.log(
                            .gameListLoaded(
                                difficulty: self.currentDifficulty,
                                gameCount: self.memoramaArray.count,
                                customCount: self.customMemoramas.count
                            )
                        )
                    } catch let error {
                        print("error firebas")
                        print(error)
                    }
                    self.isLoading = false
                }
            } else {
                self.isLoading = false
            }
        }
    }
}
