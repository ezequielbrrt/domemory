//
//  WinModalViewModel.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 17/02/21.
//

import Foundation

@MainActor
protocol WinModalListener {
    func tapOnContinue()
}

struct WinModalViewModel {
    var listener: WinModalListener?
}

