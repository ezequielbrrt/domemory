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
    // Warm Light palette
    static var primaryColor: Color    { Color.make(75, 63, 200) }   // Indigo #4B3FC8
    static var secundaryColor: Color  { Color.make(255, 99, 64) }   // Coral  #FF6340
    static var grayBackground: Color  { Color.make(247, 243, 237) } // Cream  #F7F3ED
    static var darkGrayColor: Color   { .white }                    // Surface

    // Additional semantic tokens
    static var textPrimary: Color     { Color.make(28, 24, 48) }    // #1C1830
    static var textMuted: Color       { Color.make(28, 24, 48).opacity(0.45) }
    static var easyGreen: Color       { Color.make(40, 182, 126) }  // #28B67E
    static var hardAmber: Color       { Color.make(245, 166, 35) }  // #F5A623

    static func make(_ red: Int, _ green: Int, _ blue: Int) -> Color {
        return Color(red: Double(red) / 255.0,
                     green: Double(green) / 255.0,
                     blue: Double(blue) / 255.0)
    }
}
