//
//  PauseModalViewModel.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 21/10/20.
//

import Foundation

@MainActor
protocol PauseModalListener {
    var canOfferRewardedAds: Bool { get }
    var isRewardedAdInProgress: Bool { get }
    var isDailyChallenge: Bool { get }

    func tapOnResumeGame()
    func tapOnGoHome()
    func tapOnReloadGame()
    func tapOnRewardedHint()
}

struct PauseModalViewModel {
    var listener: PauseModalListener?
}
