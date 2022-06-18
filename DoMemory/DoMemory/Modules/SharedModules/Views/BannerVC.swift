//
//  BannerVC.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 18/02/21.
//

import UIKit
import SwiftUI
import GoogleMobileAds

final private class BannerGame: UIViewControllerRepresentable  {

    func makeUIViewController(context: Context) -> UIViewController {
        let view = GADBannerView(adSize: kGADAdSizeBanner)

        let viewController = UIViewController()
        //view.adUnitID = "ca-app-pub-3940256099942544/2934735716"
        view.adUnitID = AppConfiguration.unitGame
        view.rootViewController = viewController
        viewController.view.addSubview(view)
        viewController.view.frame = CGRect(origin: .zero, size: kGADAdSizeBanner.size)
        view.load(GADRequest())

        return viewController
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

final private class BannerHome: UIViewControllerRepresentable  {

    func makeUIViewController(context: Context) -> UIViewController {
        let view = GADBannerView(adSize: kGADAdSizeBanner)

        let viewController = UIViewController()
        view.adUnitID = AppConfiguration.unitHome
        view.rootViewController = viewController
        viewController.view.addSubview(view)
        viewController.view.frame = CGRect(origin: .zero, size: kGADAdSizeBanner.size)
        view.load(GADRequest())

        return viewController
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct BannerG: View {
    var body: some View {
        HStack{
            Spacer()
            BannerGame().frame(width: 320, height: 50, alignment: .center)
            Spacer()
        }
    }
}

struct BannerH: View {
    var body: some View {
        HStack{
            Spacer()
            BannerHome().frame(width: 320, height: 50, alignment: .center)
            Spacer()
        }
    }
}

struct Banner_Previews: PreviewProvider {
    static var previews: some View {
        BannerG()
    }
}
