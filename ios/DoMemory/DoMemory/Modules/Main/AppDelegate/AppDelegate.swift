//
//  AppDelegate.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/02/21.
//

import Firebase
import FirebaseAnalytics
import FirebaseMessaging
import GoogleMobileAds
import UserNotifications
import Foundation
import UIKit
class AppDelegate: NSObject {
    
    let gcmMessageIDKey = "gcm.message_id"

    private func setupFirebase(application: UIApplication) {
        FirebaseApp.configure()
        Analytics.setAnalyticsCollectionEnabled(true)

        // Notification authorization is requested in ContentView after ATT is resolved.
        // Requesting it here (during didFinishLaunching) causes the push-notification
        // dialog to appear while ContentView's .task is already waiting to show the ATT
        // dialog — iOS silently drops whichever comes second.
        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self

        application.registerForRemoteNotifications()
    }

    // MobileAds is started in ContentView after ATT authorization is resolved.
    // Starting it here (before ATT) causes Google to mark the request as non-personalized
    // even when the user later grants tracking permission.
    
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

}

extension AppDelegate: UIApplicationDelegate {
    
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        setupFirebase(application: application)
        return true
    }
    
}

extension AppDelegate: MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        print("Firebase registration token: \(fcmToken)")
    }
}

@available(iOS 10, *)
extension AppDelegate: UNUserNotificationCenterDelegate {
    
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        let userInfo = notification.request.content.userInfo
        
        // With swizzling disabled you must let Messaging know about the message, for Analytics
        // Messaging.messaging().appDidReceiveMessage(userInfo)
        
        // Print message ID.
        
        if let messageID = userInfo[gcmMessageIDKey] {
            print("Message ID: \(messageID)")
        }
        
        // Print full message.
        print(userInfo)
        
        completionHandler([.banner, .badge, .sound])
    }
    
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let userInfo = response.notification.request.content.userInfo
        // Print message ID.
        
        if let messageID = userInfo[gcmMessageIDKey] {
            print("Message ID: \(messageID)")
        }
        
        // Print full message.
        print(userInfo)
        
        completionHandler()
    }
    
}
