//
//  Dictionary+Extensions.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import Foundation
extension Dictionary {
    var JSON: Data {
        do {
            return try JSONSerialization.data(withJSONObject: self, options: .prettyPrinted)
        } catch {
            return Data()
        }
    }
}
