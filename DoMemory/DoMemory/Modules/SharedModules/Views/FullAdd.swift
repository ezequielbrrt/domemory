//
//  FullAdd.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 18/02/21.
//

import Foundation
import GoogleMobileAds
import UIKit
    
final class InterstitialAd : NSObject, GADInterstitialDelegate, ObservableObject {
    var interstitialAd: GADInterstitial? = nil
    @Published var isLoaded: Bool = false
    
    func LoadInterstitial() {
        interstitialAd = GADInterstitial(adUnitID: "ca-app-pub-3940256099942544/2934735716")
        let req = GADRequest()
        interstitialAd!.load(req)
        interstitialAd!.delegate = self
    }
    
    func showAd() {
        if let interstitialAd = self.interstitialAd, interstitialAd.isReady {
           let root = UIApplication.shared.windows.first?.rootViewController
           interstitialAd.present(fromRootViewController: root!)
           isLoaded = false
        }
    }
    
    func interstitialWillLeaveApplication(_ ad: GADInterstitial) {
        print("user clicked ad")
    }
    
    func interstitialDidReceiveAd(_ ad: GADInterstitial) {
        print("ad loaded")
        isLoaded = true
    }
}
