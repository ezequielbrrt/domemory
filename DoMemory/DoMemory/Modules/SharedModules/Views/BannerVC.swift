//
//  BannerVC.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 18/02/21.
//

import UIKit
import SwiftUI
import AppLovinSDK

final private class BannerGame: UIViewControllerRepresentable {
    func makeCoordinator() -> Coordinator {
        Coordinator()
    }
    
    class Coordinator: NSObject, MAAdViewAdDelegate {
        func didExpand(_ ad: MAAd) {
            print(ad)
        }
        
        func didCollapse(_ ad: MAAd) {
            print(ad)
        }
        
        func didLoad(_ ad: MAAd) {
            print(ad)
        }
        
        func didFailToLoadAd(forAdUnitIdentifier adUnitIdentifier: String, withError error: MAError) {
            print(error)
        }
        
        func didDisplay(_ ad: MAAd) {
            print()
        }
        
        func didHide(_ ad: MAAd) {
            print(ad)
        }
        
        func didClick(_ ad: MAAd) {
            print(ad)
        }
        
        func didFail(toDisplay ad: MAAd, withError error: MAError) {
            print(error)
        }
    }
    
    func makeUIViewController(context: Context) -> UIViewController {
        let view = MAAdView(adUnitIdentifier: AppConfiguration.unitGame, adFormat: .banner)
        let height: CGFloat = (UIDevice.current.userInterfaceIdiom == .pad) ? 90 : 50
        let viewController = UIViewController()
        view.delegate = context.coordinator

        view.translatesAutoresizingMaskIntoConstraints = false

        viewController.view.addSubview(view)

        view.heightAnchor.constraint(equalToConstant: height).isActive = true
        view.widthAnchor.constraint(equalToConstant: 320).isActive = true
        view.centerXAnchor.constraint(equalTo: viewController.view.centerXAnchor).isActive = true
        view.bottomAnchor.constraint(equalTo: viewController.view.safeAreaLayoutGuide.bottomAnchor).isActive = true
        view.loadAd()

        return viewController
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

final private class BannerHome: UIViewControllerRepresentable  {
    func makeCoordinator() -> Coordinator {
        Coordinator()
    }
    
    class Coordinator: NSObject, MAAdViewAdDelegate {
        func didExpand(_ ad: MAAd) {
            print(ad)
        }
        
        func didCollapse(_ ad: MAAd) {
            print(ad)
        }
        
        func didLoad(_ ad: MAAd) {
            print(ad)
        }
        
        func didFailToLoadAd(forAdUnitIdentifier adUnitIdentifier: String, withError error: MAError) {
            print(error)
        }
        
        func didDisplay(_ ad: MAAd) {
            print()
        }
        
        func didHide(_ ad: MAAd) {
            print(ad)
        }
        
        func didClick(_ ad: MAAd) {
            print(ad)
        }
        
        func didFail(toDisplay ad: MAAd, withError error: MAError) {
            print(error)
        }
    }
    
    func makeUIViewController(context: Context) -> UIViewController {
        let view = MAAdView(adUnitIdentifier: AppConfiguration.unitHome, adFormat: .banner)

        let height: CGFloat = (UIDevice.current.userInterfaceIdiom == .pad) ? 90 : 50
        let viewController = UIViewController()
        view.delegate = context.coordinator

        view.translatesAutoresizingMaskIntoConstraints = false

        viewController.view.addSubview(view)

        view.heightAnchor.constraint(equalToConstant: height).isActive = true
        view.widthAnchor.constraint(equalToConstant: 320).isActive = true
        view.centerXAnchor.constraint(equalTo: viewController.view.centerXAnchor).isActive = true
        view.bottomAnchor.constraint(equalTo: viewController.view.safeAreaLayoutGuide.bottomAnchor).isActive = true
        view.loadAd()

        return viewController
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct BannerG: View {

    var body: some View {
        HStack{
            Spacer()
            BannerGame().frame(width: 320, height: (UIDevice.current.userInterfaceIdiom == .pad) ? 90 : 50, alignment: .center)
            Spacer()
        }
    }

}

struct BannerH: View {
    var body: some View {
        HStack{
            Spacer()
            BannerHome().frame(width: UIScreen.main.bounds.width,
                               height: (UIDevice.current.userInterfaceIdiom == .pad) ? 90 : 50,
                               alignment: .center)
            Spacer()
        }
    }
}
