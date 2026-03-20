//
//  SettingsViewModel.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 24/09/20.
//

import SwiftUI
import Observation

protocol SettingsListener: AnyObject {
    func onCloseView()
}

@Observable
class SettingsViewModel {
    weak var listener: SettingsListener?
    var difficulty: Int = 0

    init(listener: SettingsListener?) {
        self.listener = listener
    }
}

// MARK: - HELPERS
extension SettingsViewModel {
    func viewWillDissapear() {
        listener?.onCloseView()
    }

    func getCurrentDifficulty() -> Int {
        let userSettings = UserManageObject().getUserSettings()
        guard let difficulty = userSettings?.dificulty else { return 0 }
        guard let currentDifficulty = Difficulty(rawValue: difficulty) else { return 0 }
        return currentDifficulty.id
    }

    func saveDifficulty(difficultyIndex: Int) {
        var difficulty: Difficulty?

        switch difficultyIndex {
        case 0:
            difficulty = .easy
        case 1:
            difficulty = .medium
        case 2:
            difficulty = .hard
        case 3:
            difficulty = .veryHard
        default:
            difficulty = .easy
        }

        self.difficulty = difficultyIndex
        UserManageObject().updateDifficulty(withDifficulty: difficulty ?? .easy)
    }
}
