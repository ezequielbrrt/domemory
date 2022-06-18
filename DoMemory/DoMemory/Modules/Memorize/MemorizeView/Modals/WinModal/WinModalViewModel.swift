//
//  WinModalViewModel.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 17/02/21.
//

import Foundation

protocol WinModalListener {
    func tapOnContinue()
}

struct WinModalViewModel {
    var listener: WinModalListener?
}


