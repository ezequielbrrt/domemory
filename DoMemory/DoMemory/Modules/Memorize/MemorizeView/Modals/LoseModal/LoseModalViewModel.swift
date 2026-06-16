//
//  LoseModalViewModel.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 05/11/20.
//

import SwiftUI

@MainActor
protocol LoseModalViewModelListener {
    var canOfferRewardedAds: Bool { get }
    var isRewardedAdInProgress: Bool { get }
    var isDailyChallenge: Bool { get }

    func tapOnTryAgain()
    func tapOnGoToMenuAfterLose()
    func tapOnRewardedExtraTime()
}

struct LoseModalViewModel {
    var listener: LoseModalViewModelListener?
}


// MARK: PUBLIC
extension LoseModalViewModel {
    @MainActor
    func tapOnTryAgain() {
        listener?.tapOnTryAgain()
    }
}
