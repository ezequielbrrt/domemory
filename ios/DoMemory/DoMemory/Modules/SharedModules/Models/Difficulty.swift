//
//  Difficulty.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

enum Difficulty: String, CaseIterable, Identifiable {
    var id: Int {
        switch self {
        case .easy: return 0
        case .medium: return 1
        case .hard:  return 2
        case .veryHard: return 3
        }
    }
    
    case easy
    case medium
    case hard
    case veryHard
}
