//
//  Strings.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import Foundation

enum Strings {
    static let appName = NSLocalizedString("common_app_name", comment: "Application display name")
    static let easy = NSLocalizedString("difficulty_easy", comment: "Easy difficulty level")
    static let easySubtitle = NSLocalizedString("difficulty_easy_subtitle", comment: "Subtitle for easy difficulty")
    static let medium = NSLocalizedString("difficulty_medium", comment: "Medium difficulty level")
    static let mediumSubtitle = NSLocalizedString("difficulty_medium_subtitle", comment: "Subtitle for medium difficulty")
    static let hard = NSLocalizedString("difficulty_hard", comment: "Hard difficulty level")
    static let hardSubtitle = NSLocalizedString("difficulty_hard_subtitle", comment: "Subtitle for hard difficulty")
    static let difficulty = NSLocalizedString("home_difficulty_title", comment: "Difficulty section title")
    static let askDifficulty = NSLocalizedString("home_select_difficulty_prompt", comment: "Prompt to select difficulty")
    static let veryHard = NSLocalizedString("difficulty_very_hard", comment: "Very hard difficulty level")
    static let veryHardSubtitle = NSLocalizedString("difficulty_very_hard_subtitle", comment: "Subtitle for very hard difficulty")
    static let settingsTitle = NSLocalizedString("settings_title", comment: "Settings screen title")
    static let settingsDescription = NSLocalizedString("settings_description", comment: "Settings screen subtitle")
    static let settingsSelectDifficulty = NSLocalizedString("settings_select_difficulty", comment: "Button to select difficulty")
    static let settingsThemeTitle = NSLocalizedString("settings_theme_title", comment: "Theme setting title")
    static let settingsThemePrompt = NSLocalizedString("settings_theme_prompt", comment: "Theme picker subtitle")
    static let settingsSelectTheme = NSLocalizedString("settings_select_theme", comment: "Button to select theme")
    static let settingsRemoveAdsTitle = NSLocalizedString("settings_remove_ads_title", comment: "Remove ads setting title")
    static let settingsRemoveAdsDescription = NSLocalizedString("settings_remove_ads_description", comment: "Remove ads setting description")
    static let settingsRemoveAdsActionFormat = NSLocalizedString("settings_remove_ads_action_format", comment: "Remove ads purchase action with localized price")
    static let settingsRemoveAdsPurchased = NSLocalizedString("settings_remove_ads_purchased", comment: "Remove ads purchased state")
    static let settingsRemoveAdsPurchasedDescription = NSLocalizedString("settings_remove_ads_purchased_description", comment: "Remove ads purchased description")
    static let settingsRemoveAdsLoading = NSLocalizedString("settings_remove_ads_loading", comment: "Remove ads loading state")
    static let settingsRemoveAdsUnavailable = NSLocalizedString("settings_remove_ads_unavailable", comment: "Remove ads unavailable message")
    static let settingsRemoveAdsPending = NSLocalizedString("settings_remove_ads_pending", comment: "Remove ads pending message")
    static let settingsRemoveAdsNoRestore = NSLocalizedString("settings_remove_ads_no_restore", comment: "No restorable remove ads purchase message")
    static let settingsRestorePurchasesTitle = NSLocalizedString("settings_restore_purchases_title", comment: "Restore purchases setting title")
    static let settingsRestorePurchasesDescription = NSLocalizedString("settings_restore_purchases_description", comment: "Restore purchases setting description")
    static let settingsRestorePurchasesAction = NSLocalizedString("settings_restore_purchases_action", comment: "Restore purchases button title")
    static let settingsPurchaseSuccessTitle = NSLocalizedString("settings_purchase_success_title", comment: "Purchase successful alert title")
    static let settingsPurchaseSuccessMessage = NSLocalizedString("settings_purchase_success_message", comment: "Purchase successful alert message")
    static let settingsRestoreSuccessTitle = NSLocalizedString("settings_restore_success_title", comment: "Restore successful alert title")
    static let settingsRestoreSuccessMessage = NSLocalizedString("settings_restore_success_message", comment: "Restore successful alert message")
    static let themeSystem = NSLocalizedString("theme_system", comment: "System theme option")
    static let themeLight = NSLocalizedString("theme_light", comment: "Light theme option")
    static let themeDark = NSLocalizedString("theme_dark", comment: "Dark theme option")
    static let yourPoints = NSLocalizedString("game_points_label", comment: "Points label")
    static let time = NSLocalizedString("game_time_label", comment: "Time label")
    static let pause = NSLocalizedString("game_pause_title", comment: "Pause modal title")
    static let quit = NSLocalizedString("game_quit_confirmation", comment: "Quit confirmation prompt")
    static let ok = NSLocalizedString("common_ok", comment: "OK action")
    static let cancel = NSLocalizedString("common_cancel", comment: "Cancel action")
    static let accept = NSLocalizedString("common_accept", comment: "Accept action")
    static let youLose = NSLocalizedString("game_lose_message", comment: "Lose modal description")
    static let tryAgain = NSLocalizedString("game_try_again", comment: "Try again action")
    static let continueGame = NSLocalizedString("game_continue", comment: "Continue game action")
    static let goToMenu = NSLocalizedString("game_go_to_menu", comment: "Go to menu action")
    static let pairs = NSLocalizedString("game_pairs_label", comment: "Pairs stat label")
    static let remaining = NSLocalizedString("game_remaining_label", comment: "Remaining time stat label")
    static let errors = NSLocalizedString("game_errors_label", comment: "Errors stat label")
    static let youWin = NSLocalizedString("game_win_title", comment: "Win modal title")
    static let youWinDescription = NSLocalizedString("game_win_description", comment: "Win modal description")

    // Menu tabs
    static let tabAll = NSLocalizedString("menu_tab_all", comment: "Tab showing all games")
    static let tabMine = NSLocalizedString("menu_tab_mine", comment: "Tab showing user-created games")

    // Create memorama sheet
    static let createTitle = NSLocalizedString("menu_create_title", comment: "Sheet title for creating a new memorama")
    static let createName = NSLocalizedString("menu_create_name_label", comment: "Label for memorama name field")
    static let createNamePlaceholder = NSLocalizedString("menu_create_name_placeholder", comment: "Placeholder for memorama name field")
    static let createEmojisCount = NSLocalizedString("menu_create_emojis_label", comment: "Label prefix for emoji count")
    static let createMinimum = NSLocalizedString("menu_create_minimum_hint", comment: "Hint that at least 2 emojis are required")
    static let createAddEmoji = NSLocalizedString("menu_create_add_emoji", comment: "Button to add an emoji")
    static let createAdd = NSLocalizedString("menu_create_add", comment: "Confirm adding an emoji")
    static let createSave = NSLocalizedString("menu_create_save", comment: "Save button in create memorama sheet")

    // My memoramas empty state
    static let emptyMyGames = NSLocalizedString("menu_empty_mine_message", comment: "Empty state message for user-created games")
    static let emptyMyGamesAction = NSLocalizedString("menu_empty_mine_action", comment: "CTA button in empty state")

    // Game stats
    static let statsPlayed = NSLocalizedString("stats_played_label", comment: "Played count label")
    static let statsWon = NSLocalizedString("stats_won_label", comment: "Won count label")

    // Delete action
    static let delete = NSLocalizedString("common_delete", comment: "Delete action in context menu")
}
