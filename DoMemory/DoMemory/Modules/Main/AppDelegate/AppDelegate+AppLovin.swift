//
//  AppDelegate+AppLovin.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 30/08/22.
//

import AdSupport
import AppLovinSDK
import Foundation

extension AppDelegate {

    func setupAppLovin() {
        let settings = ALSdkSettings()
        settings.consentFlowSettings.isEnabled = true
        settings.consentFlowSettings.privacyPolicyURL = URL(string: "https://ezequielbrrt.github.io/spacegram/export.pdf")
        
        let sdk = ALSdk.shared(with: settings)!

        // Please make sure to set the mediation provider value to "max" to ensure proper functionality
        sdk.mediationProvider = "max"
        sdk.initializeSdk { (configuration: ALSdkConfiguration) in
            print(configuration)
            NotificationCenter.default.post(name: .UserConfigureMax, object: nil)
        }
    }
}
