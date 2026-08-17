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
    var levelNumber: Int? { get }
    var levelLivesRemaining: Int? { get }
    var canWatchAdForLife: Bool { get }
    var canBuyLifeWithStars: Bool { get }
    var canSkipLevelWithStars: Bool { get }
    var showSkipLevelConfirm: Bool { get }
    var loseReason: LoseReason? { get }
    var canForgiveWithStars: Bool { get }
    var canWatchAdToForgive: Bool { get }

    func tapOnTryAgain()
    func tapOnGoToMenuAfterLose()
    func tapOnRewardedExtraTime()
    func tapOnWatchAdForLife()
    func tapOnBuyLifeWithStars()
    func tapOnWatchAdToForgive()
    func tapOnForgiveWithStars()
    func tapOnSkipLevelPrompt()
    func tapOnCancelSkipLevel()
    func tapOnConfirmSkipLevel()
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
