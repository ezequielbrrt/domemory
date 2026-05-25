//
//  AppConfiguration.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI
import FirebaseAnalytics

enum AppConfiguration {
    static let unitGame = "e362a87fcadb4afb"
    static let unitHome = "a9ee7d214e9f5dab"
}

enum AnalyticsEvent {
    case screenView(name: String, screenClass: String)
    case difficultySelected(difficulty: String)
    case menuLoaded(difficulty: String)
    case gameListLoaded(difficulty: String, gameCount: Int, customCount: Int)
    case gameStarted(source: String, difficulty: String, cardsCount: Int, isCustom: Bool)
    case gameFinished(result: String, difficulty: String, cardsCount: Int, failedTries: Int, timeRemaining: Int, isCustom: Bool)
    case cardTapped(difficulty: String, cardsCount: Int, failedTries: Int)
    case pauseOpened(difficulty: String, timeRemaining: Int)
    case resumeTapped(difficulty: String, timeRemaining: Int)
    case quitConfirmed(difficulty: String, timeRemaining: Int, failedTries: Int)
    case retryTapped(difficulty: String, cardsCount: Int, source: String)
    case favoriteToggled(gameID: String, isFavorite: Bool)
    case customMemoramaCreated(gameID: String, difficulty: String, cardsCount: Int)
    case customMemoramaDeleted(gameID: String)
    case multiplayerRoomCreated(gameID: String, isCustom: Bool)
    case multiplayerRoomJoined
    case multiplayerGameStarted(gameID: String)
    case multiplayerGameFinished(result: String)
    case adLifecycle(placement: String, action: String)
    case whatsNewShown(version: String)
    case whatsNewDismissed(version: String)

    var name: String {
        switch self {
        case .screenView: return AnalyticsEventScreenView
        case .difficultySelected: return "difficulty_selected"
        case .menuLoaded: return "menu_loaded"
        case .gameListLoaded: return "game_list_loaded"
        case .gameStarted: return "game_started"
        case .gameFinished: return "game_finished"
        case .cardTapped: return "card_tapped"
        case .pauseOpened: return "pause_opened"
        case .resumeTapped: return "resume_tapped"
        case .quitConfirmed: return "quit_confirmed"
        case .retryTapped: return "retry_tapped"
        case .favoriteToggled: return "favorite_toggled"
        case .customMemoramaCreated: return "custom_memorama_created"
        case .customMemoramaDeleted: return "custom_memorama_deleted"
        case .multiplayerRoomCreated: return "multiplayer_room_created"
        case .multiplayerRoomJoined: return "multiplayer_room_joined"
        case .multiplayerGameStarted: return "multiplayer_game_started"
        case .multiplayerGameFinished: return "multiplayer_game_finished"
        case .adLifecycle: return "ad_lifecycle"
        case .whatsNewShown: return "whats_new_shown"
        case .whatsNewDismissed: return "whats_new_dismissed"
        }
    }

    var parameters: [String: Any] {
        switch self {
        case .screenView(let name, let screenClass):
            return [
                AnalyticsParameterScreenName: name,
                AnalyticsParameterScreenClass: screenClass
            ]
        case .difficultySelected(let difficulty):
            return ["difficulty": difficulty]
        case .menuLoaded(let difficulty):
            return ["difficulty": difficulty]
        case .gameListLoaded(let difficulty, let gameCount, let customCount):
            return [
                "difficulty": difficulty,
                "game_count": gameCount,
                "custom_count": customCount
            ]
        case .gameStarted(let source, let difficulty, let cardsCount, let isCustom):
            return [
                "source": source,
                "difficulty": difficulty,
                "cards_count": cardsCount,
                "is_custom": isCustom ? 1 : 0
            ]
        case .gameFinished(let result, let difficulty, let cardsCount, let failedTries, let timeRemaining, let isCustom):
            return [
                "result": result,
                "difficulty": difficulty,
                "cards_count": cardsCount,
                "failed_tries": failedTries,
                "time_remaining": timeRemaining,
                "is_custom": isCustom ? 1 : 0
            ]
        case .cardTapped(let difficulty, let cardsCount, let failedTries):
            return [
                "difficulty": difficulty,
                "cards_count": cardsCount,
                "failed_tries": failedTries
            ]
        case .pauseOpened(let difficulty, let timeRemaining):
            return [
                "difficulty": difficulty,
                "time_remaining": timeRemaining
            ]
        case .resumeTapped(let difficulty, let timeRemaining):
            return [
                "difficulty": difficulty,
                "time_remaining": timeRemaining
            ]
        case .quitConfirmed(let difficulty, let timeRemaining, let failedTries):
            return [
                "difficulty": difficulty,
                "time_remaining": timeRemaining,
                "failed_tries": failedTries
            ]
        case .retryTapped(let difficulty, let cardsCount, let source):
            return [
                "difficulty": difficulty,
                "cards_count": cardsCount,
                "source": source
            ]
        case .favoriteToggled(let gameID, let isFavorite):
            return [
                "game_id": gameID,
                "is_favorite": isFavorite ? 1 : 0
            ]
        case .customMemoramaCreated(let gameID, let difficulty, let cardsCount):
            return [
                "game_id": gameID,
                "difficulty": difficulty,
                "cards_count": cardsCount
            ]
        case .customMemoramaDeleted(let gameID):
            return ["game_id": gameID]
        case .multiplayerRoomCreated(let gameID, let isCustom):
            return [
                "game_id": gameID,
                "is_custom": isCustom ? 1 : 0
            ]
        case .multiplayerRoomJoined:
            return [:]
        case .multiplayerGameStarted(let gameID):
            return ["game_id": gameID]
        case .multiplayerGameFinished(let result):
            return ["result": result]
        case .adLifecycle(let placement, let action):
            return [
                "placement": placement,
                "action": action
            ]
        case .whatsNewShown(let version):
            return ["version": version]
        case .whatsNewDismissed(let version):
            return ["version": version]
        }
    }
}

enum AnalyticsService {
    static let cardTapSampleRate: Double = 0.2

    static func log(_ event: AnalyticsEvent) {
        Analytics.logEvent(event.name, parameters: event.parameters)
        if isVerboseLoggingEnabled {
            print("[Analytics] \(event.name): \(event.parameters)")
        }
    }

    static func shouldSample(_ rate: Double) -> Bool {
        Double.random(in: 0..<1) < rate
    }

    private static var isVerboseLoggingEnabled: Bool {
        #if DEBUG
        true
        #else
        false
        #endif
    }
}

enum AppTheme: String, CaseIterable, Identifiable {
    case system
    case light
    case dark

    var id: String { rawValue }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }

    var title: String {
        switch self {
        case .system: return Strings.themeSystem
        case .light: return Strings.themeLight
        case .dark: return Strings.themeDark
        }
    }
}


// MARK:- FONTS
extension UIFont {
    class func righteous(size: CGFloat) -> UIFont {
        return UIFont.systemFont(ofSize: size, weight: .heavy)
    }

    class func patrickHand(size: CGFloat) -> UIFont {
        return UIFont.systemFont(ofSize: size, weight: .semibold)
    }
}

extension Font {
    static func righteous(size: CGFloat) -> Font {
        return .system(size: size, weight: .heavy, design: .rounded)
    }

    static func patrickHand(size: CGFloat) -> Font {
        return .system(size: size, weight: .semibold, design: .rounded)
    }
}

// MARK:- COLORS
extension Color {
    static var primaryColor: Color    { dynamicColor(light: (75, 63, 200), dark: (142, 129, 255)) }
    static var secundaryColor: Color  { dynamicColor(light: (255, 99, 64), dark: (255, 134, 109)) }
    static var easyGreen: Color       { dynamicColor(light: (40, 182, 126), dark: (73, 214, 151)) }
    static var hardAmber: Color       { dynamicColor(light: (245, 166, 35), dark: (255, 193, 87)) }

    static var appBackground: Color   { dynamicColor(light: (247, 243, 237), dark: (17, 19, 31)) }
    static var surfacePrimary: Color  { dynamicColor(light: (255, 255, 255), dark: (30, 34, 52)) }
    static var surfaceSecondary: Color { dynamicColor(light: (239, 233, 227), dark: (40, 46, 69)) }
    static var textPrimary: Color     { dynamicColor(light: (28, 24, 48), dark: (244, 240, 255)) }
    static var textSecondary: Color   { dynamicColor(light: (122, 114, 145), dark: (164, 171, 196)) }
    static var surfaceBorder: Color   { dynamicColor(light: (225, 219, 235), dark: (65, 72, 98)) }
    static var shadowColor: Color     { dynamicColor(light: (28, 24, 48), dark: (0, 0, 0), lightOpacity: 0.08, darkOpacity: 0.32) }
    static var overlayBackdrop: Color { dynamicColor(light: (247, 243, 237), dark: (9, 11, 18), lightOpacity: 0.88, darkOpacity: 0.74) }

    // Backward-compatible aliases for existing call sites.
    static var grayBackground: Color  { appBackground }
    static var darkGrayColor: Color   { surfacePrimary }
    static var textMuted: Color       { textSecondary }

    static func make(_ red: Int, _ green: Int, _ blue: Int) -> Color {
        return Color(red: Double(red) / 255.0,
                     green: Double(green) / 255.0,
                     blue: Double(blue) / 255.0)
    }

    private static func dynamicColor(
        light: (Int, Int, Int),
        dark: (Int, Int, Int),
        lightOpacity: Double = 1,
        darkOpacity: Double = 1
    ) -> Color {
        Color(
            uiColor: UIColor { traitCollection in
                let palette = traitCollection.userInterfaceStyle == .dark ? dark : light
                let alpha = traitCollection.userInterfaceStyle == .dark ? darkOpacity : lightOpacity
                return UIColor(
                    red: CGFloat(palette.0) / 255.0,
                    green: CGFloat(palette.1) / 255.0,
                    blue: CGFloat(palette.2) / 255.0,
                    alpha: alpha
                )
            }
        )
    }
}
