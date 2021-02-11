//
//  Memorama.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

struct Memorama: Codable, Identifiable {
    var id: String
    var name: String
    var category: String
    var difficulty: String
    var description: String
    var publishedDate: String
    var items: [String]
    var itemType: String
    var isDoubleItem: Bool
}
