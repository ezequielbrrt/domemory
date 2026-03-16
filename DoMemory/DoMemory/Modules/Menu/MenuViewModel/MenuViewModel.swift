//
//  MenuViewModel.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI
import FirebaseDatabase
import FirebaseAuth
// import CodableFirebase

class MenuViewModel: ObservableObject {
    @Published var memoramaArray: [Memorama] = []
    @Published var isLoading: Bool = false
    private(set) var favoriteIDs: Set<String> = []

    init() {
        ref = Database.database().reference()
        isLoading = true
        getPoints()
        difficulty = getDifficulty()
        loadFavorites()
        getData()
    }

    public func getUserPoints() -> String {
        return String(points)
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
        applySort()
    }

    // MARK: Private
    private var ref: DatabaseReference!
    private var difficulty: Difficulty!
    private var points: Int!
    private var allGames: [Memorama] = []
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
        Auth.auth().signInAnonymously() { [weak self] (authResult, error) in
            if let _ = authResult {
                self?.ref.child("data").observeSingleEvent(of: .value) { [self] snapshot in
                    do {

                        let array = snapshot.value as? [[String: Any]]
                        let data = try JSONSerialization.data(withJSONObject: array, options: [])
                        var memoramaArrayAux = try JSONDecoder().decode([Memorama].self, from: data)
                        memoramaArrayAux.shuffle()
                        self?.allGames = memoramaArrayAux
                        self?.memoramaArray = memoramaArrayAux.filter { memorama in
                            let difficultyEnum = Difficulty(rawValue: memorama.difficulty) ?? .medium
                            return difficultyEnum == self?.difficulty
                        }
                        self?.applySort()
                    } catch let error {
                        print("error firebas")
                        print(error)
                    }
                    self?.isLoading = false
                }
            }
            else {
                self?.isLoading = false
            }
            // Error do something
        }
    }
}

