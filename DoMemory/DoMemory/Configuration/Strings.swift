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
    static let dailyChallengeTitle = NSLocalizedString("daily_challenge_title", comment: "Daily challenge feature title")
    static let dailyChallengeSubtitle = NSLocalizedString("daily_challenge_subtitle", comment: "Daily challenge subtitle / call to action")
    static let dailyChallengeCompleted = NSLocalizedString("daily_challenge_completed", comment: "Daily challenge completed state for today")
    static func dailyChallengeStreak(_ days: Int) -> String {
        String(format: NSLocalizedString("daily_challenge_streak_format", comment: "Streak badge, %d = number of days"), days)
    }
    static let achievementsTitle = NSLocalizedString("achievements_title", comment: "Achievements screen title")
    static let achievementsSubtitle = NSLocalizedString("achievements_subtitle", comment: "Achievements settings row subtitle")
    static let achievementsStatsSection = NSLocalizedString("achievements_stats_section", comment: "Stats section header")
    static let achievementsBadgesSection = NSLocalizedString("achievements_badges_section", comment: "Badges section header")
    static let statTotalGames = NSLocalizedString("stat_total_games", comment: "Total games stat label")
    static let statTotalWins = NSLocalizedString("stat_total_wins", comment: "Total wins stat label")
    static let statWinRate = NSLocalizedString("stat_win_rate", comment: "Win rate stat label")
    static let statPerfectGames = NSLocalizedString("stat_perfect_games", comment: "Perfect games stat label")
    static let statLongestStreak = NSLocalizedString("stat_longest_streak", comment: "Longest streak stat label")
    static let statMultiplayerWins = NSLocalizedString("stat_multiplayer_wins", comment: "Multiplayer wins stat label")
    static let settingsTitle = NSLocalizedString("settings_title", comment: "Settings screen title")
    static let settingsDescription = NSLocalizedString("settings_description", comment: "Settings screen subtitle")
    static let settingsThemeTitle = NSLocalizedString("settings_theme_title", comment: "Theme setting title")
    static let settingsThemePrompt = NSLocalizedString("settings_theme_prompt", comment: "Theme picker subtitle")
    static let settingsRemoveAdsTitle = NSLocalizedString("settings_remove_ads_title", comment: "Remove ads setting title")
    static let settingsRemoveAdsDescription = NSLocalizedString("settings_remove_ads_description", comment: "Remove ads setting description")
    static let settingsRemoveAdsActionFormat = NSLocalizedString("settings_remove_ads_action_format", comment: "Remove ads purchase action with localized price")
    static let settingsRemoveAdsPurchased = NSLocalizedString("settings_remove_ads_purchased", comment: "Remove ads purchased state")
    static let settingsRemoveAdsPurchasedDescription = NSLocalizedString("settings_remove_ads_purchased_description", comment: "Remove ads purchased description")
    static let settingsRemoveAdsLoading = NSLocalizedString("settings_remove_ads_loading", comment: "Remove ads loading state")
    static let settingsRemoveAdsUnavailable = NSLocalizedString("settings_remove_ads_unavailable", comment: "Remove ads unavailable message")
    static let settingsRemoveAdsPending = NSLocalizedString("settings_remove_ads_pending", comment: "Remove ads pending message")
    static let settingsRemoveAdsNoRestore = NSLocalizedString("settings_remove_ads_no_restore", comment: "No restorable remove ads purchase message")
    static let settingsRewardedRemoveAdsTitle = NSLocalizedString("settings_rewarded_remove_ads_title", comment: "Rewarded temporary remove ads setting title")
    static let settingsRewardedRemoveAdsDescription = NSLocalizedString("settings_rewarded_remove_ads_description", comment: "Rewarded temporary remove ads setting description")
    static let settingsRewardedRemoveAdsAction = NSLocalizedString("settings_rewarded_remove_ads_action", comment: "Rewarded temporary remove ads button title")
    static let settingsRewardedRemoveAdsActive = NSLocalizedString("settings_rewarded_remove_ads_active", comment: "Rewarded temporary remove ads active state")
    static let settingsRewardedRemoveAdsActiveFormat = NSLocalizedString("settings_rewarded_remove_ads_active_format", comment: "Rewarded temporary remove ads active state with expiry time")
    static let settingsRewardedRemoveAdsSuccessTitle = NSLocalizedString("settings_rewarded_remove_ads_success_title", comment: "Rewarded temporary remove ads success alert title")
    static let settingsRewardedRemoveAdsSuccessMessage = NSLocalizedString("settings_rewarded_remove_ads_success_message", comment: "Rewarded temporary remove ads success alert message")
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
    static let rewardedExtraTime = NSLocalizedString("game_rewarded_extra_time", comment: "Rewarded ad action for extra game time")
    static let rewardedHint = NSLocalizedString("game_rewarded_hint", comment: "Rewarded ad action for a game hint")
    static let adLoading = NSLocalizedString("ads_loading", comment: "Ad loading state")
    static let adLabel = NSLocalizedString("ads_label", comment: "Ad disclosure label")
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
    static let tabLevels = NSLocalizedString("menu_tab_levels", comment: "Tab showing the endless levels mode")

    // Levels mode
    static let levelsScreenTitle = NSLocalizedString("levels_screen_title", comment: "Levels map screen title")
    static func levelsCurrentLevelFormat(_ level: Int) -> String {
        String(format: NSLocalizedString("levels_current_level_format", comment: "Current level summary, %d = level number"), level)
    }
    static func levelTitle(_ level: Int) -> String {
        String(format: NSLocalizedString("level_title_format", comment: "Level board name / win-modal subtitle, %d = level number"), level)
    }
    static let levelCleared = NSLocalizedString("level_cleared", comment: "Win modal title when clearing a level")
    static let nextLevel = NSLocalizedString("level_next", comment: "Win modal primary action to advance to the next level")
    static let backToLevels = NSLocalizedString("level_back_to_levels", comment: "Win modal secondary action returning to the levels map")

    // Levels lives
    static let outOfLivesTitle = NSLocalizedString("levels_out_of_lives_title", comment: "Title shown when the player has no lives left today")
    static let outOfLivesMessage = NSLocalizedString("levels_out_of_lives_message", comment: "Message explaining how to get another life")
    /// Shown when no rewarded ad is available — Remove-Ads purchasers, or an
    /// unfilled ad slot. The default copy tells them to watch an ad they will
    /// never be offered.
    static let outOfLivesMessageNoAd = NSLocalizedString("levels_out_of_lives_message_no_ad", comment: "Out-of-lives message when no rewarded ad is available")
    static let watchAdForLife = NSLocalizedString("levels_watch_ad_for_life", comment: "Button to watch a rewarded ad for +1 life")
    static func livesRemainingFormat(_ remaining: Int, _ total: Int) -> String {
        String(format: NSLocalizedString("levels_lives_remaining_format", comment: "Accessibility label, %d = lives remaining, %d = max lives"), remaining, total)
    }

    // Star economy
    static let powerUpExtraTime = NSLocalizedString("levels_powerup_extra_time", comment: "Power-up that adds seconds to the clock")
    static let powerUpPeek = NSLocalizedString("levels_powerup_peek", comment: "Power-up that briefly reveals the whole board")
    static let powerUpFreeze = NSLocalizedString("levels_powerup_freeze", comment: "Power-up that pauses the countdown")
    static let powerUpRevealPair = NSLocalizedString("levels_powerup_reveal_pair", comment: "Power-up that reveals one matching pair")
    static func starBalanceFormat(_ balance: Int) -> String {
        String(format: NSLocalizedString("levels_star_balance_format", comment: "Accessibility label for the spendable star balance, %d = stars"), balance)
    }
    static func powerUpCostFormat(_ title: String, _ cost: Int) -> String {
        String(format: NSLocalizedString("levels_powerup_cost_format", comment: "Accessibility label, %@ = power-up name, %d = star cost"), title, cost)
    }
    static func buyLifeFormat(_ cost: Int) -> String {
        String(format: NSLocalizedString("levels_buy_life_format", comment: "Button to buy one life with stars, %d = star cost"), cost)
    }
    static func skipLevelFormat(_ cost: Int) -> String {
        String(format: NSLocalizedString("levels_skip_level_format", comment: "Button to skip the current level with stars, %d = star cost"), cost)
    }
    static let skipLevelConfirmTitle = NSLocalizedString("levels_skip_level_confirm_title", comment: "Confirmation title before skipping a level")
    static let skipLevelConfirmMessage = NSLocalizedString("levels_skip_level_confirm_message", comment: "Confirmation body explaining that a skipped level earns no stars")
    static let skipLevelConfirmAction = NSLocalizedString("levels_skip_level_confirm_action", comment: "Confirm action for skipping a level")

    // Mistake budget
    static let loseTooManyMistakes = NSLocalizedString("levels_lose_too_many_mistakes", comment: "Lose modal message when the mistake budget is spent")
    static func forgiveAdFormat(_ amount: Int) -> String {
        String(format: NSLocalizedString("levels_forgive_ad_format", comment: "Rewarded ad action forgiving mistakes, %d = mistakes forgiven"), amount)
    }
    static func forgiveStarsFormat(_ amount: Int, _ cost: Int) -> String {
        String(format: NSLocalizedString("levels_forgive_stars_format", comment: "Star-priced action forgiving mistakes, %1$d = mistakes forgiven, %2$d = star cost"), amount, cost)
    }
    static func timerFrozenFormat(_ seconds: Int) -> String {
        String(format: NSLocalizedString("levels_timer_frozen_format", comment: "Accessibility label while the Freeze power-up holds the clock, %d = seconds left"), seconds)
    }
    static func mistakesRemainingFormat(_ used: Int, _ total: Int) -> String {
        String(format: NSLocalizedString("levels_mistakes_remaining_format", comment: "Accessibility label, %1$d = mistakes made, %2$d = mistakes allowed"), used, total)
    }

    // Levels intro
    static let levelsIntroProgressTitle = NSLocalizedString("levels_intro_progress_title", comment: "Levels intro slide 1 title: endless progression")
    static let levelsIntroProgressSubtitle = NSLocalizedString("levels_intro_progress_subtitle", comment: "Levels intro slide 1 subtitle: clear a level to unlock the next")
    static let levelsIntroStarsTitle = NSLocalizedString("levels_intro_stars_title", comment: "Levels intro slide 2 title: earning and spending stars")
    static let levelsIntroStarsSubtitle = NSLocalizedString("levels_intro_stars_subtitle", comment: "Levels intro slide 2 subtitle: up to three stars per level, spendable")
    static let levelsIntroLivesTitle = NSLocalizedString("levels_intro_lives_title", comment: "Levels intro slide 3 title: daily lives")
    static func levelsIntroLivesSubtitle(_ maxLives: Int) -> String {
        String(format: NSLocalizedString("levels_intro_lives_subtitle", comment: "Levels intro slide 3 subtitle, %d = lives granted per day"), maxLives)
    }
    static let levelsIntroMistakesTitle = NSLocalizedString("levels_intro_mistakes_title", comment: "Levels intro slide 4 title: the mistake budget")
    static let levelsIntroMistakesSubtitle = NSLocalizedString("levels_intro_mistakes_subtitle", comment: "Levels intro slide 4 subtitle: limited mistakes per level, forgivable")
    static let levelsIntroDone = NSLocalizedString("levels_intro_done", comment: "Button dismissing the Levels intro on its last slide")
    static let levelsIntroInfoAccessibility = NSLocalizedString("levels_intro_info_accessibility", comment: "Accessibility label for the button that reopens the Levels intro")

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
    static let menuRandomGame = NSLocalizedString("menu_random_game", comment: "Button title for starting a random game")

    // Game stats
    static let statsPlayed = NSLocalizedString("stats_played_label", comment: "Played count label")
    static let statsWon = NSLocalizedString("stats_won_label", comment: "Won count label")

    // Delete action
    static let delete = NSLocalizedString("common_delete", comment: "Delete action in context menu")

    // Multiplayer
    static let multiplayerTitle = NSLocalizedString("multiplayer_title", comment: "Multiplayer screen title")
    static let multiplayerCreateRoom = NSLocalizedString("multiplayer_create_room", comment: "Create multiplayer room action")
    static let multiplayerJoinRoom = NSLocalizedString("multiplayer_join_room", comment: "Join multiplayer room action")
    static let multiplayerInviteFriend = NSLocalizedString("multiplayer_invite_friend", comment: "Share invite link to challenge a friend")
    static let shareResult = NSLocalizedString("share_result", comment: "Share game result action")
    static let introMultiplayerTitle = NSLocalizedString("intro_multiplayer_title", comment: "Onboarding intro: multiplayer title")
    static let introMultiplayerSubtitle = NSLocalizedString("intro_multiplayer_subtitle", comment: "Onboarding intro: multiplayer subtitle")
    static let introCustomTitle = NSLocalizedString("intro_custom_title", comment: "Onboarding intro: custom games title")
    static let introCustomSubtitle = NSLocalizedString("intro_custom_subtitle", comment: "Onboarding intro: custom games subtitle")
    static let introDailyTitle = NSLocalizedString("intro_daily_title", comment: "Onboarding intro: daily challenge title")
    static let introDailySubtitle = NSLocalizedString("intro_daily_subtitle", comment: "Onboarding intro: daily challenge subtitle")
    static let introSkip = NSLocalizedString("intro_skip", comment: "Onboarding intro: skip")
    static let introNext = NSLocalizedString("intro_next", comment: "Onboarding intro: next")
    static let introGetStarted = NSLocalizedString("intro_get_started", comment: "Onboarding intro: get started")
    static let multiplayerJoinDescription = NSLocalizedString("multiplayer_join_description", comment: "Join room instructions")
    static let multiplayerCodePlaceholder = NSLocalizedString("multiplayer_code_placeholder", comment: "Room code placeholder")
    static let multiplayerQRCode = NSLocalizedString("multiplayer_qr_code", comment: "QR code accessibility label")
    static let multiplayerStartGame = NSLocalizedString("multiplayer_start_game", comment: "Start multiplayer game action")
    static let multiplayerWaitingForPlayer = NSLocalizedString("multiplayer_waiting_for_player", comment: "Waiting for guest player state")
    static let multiplayerReadyToStart = NSLocalizedString("multiplayer_ready_to_start", comment: "Ready state for host")
    static let multiplayerWaitingForHost = NSLocalizedString("multiplayer_waiting_for_host", comment: "Ready state for guest")
    static let multiplayerYourTurn = NSLocalizedString("multiplayer_your_turn", comment: "Current user turn state")
    static let multiplayerOpponentTurn = NSLocalizedString("multiplayer_opponent_turn", comment: "Opponent turn state")
    static let multiplayerReconnecting = NSLocalizedString("multiplayer_reconnecting", comment: "Reconnect grace state")
    static let multiplayerYouWon = NSLocalizedString("multiplayer_you_won", comment: "Winner state")
    static let multiplayerYouLost = NSLocalizedString("multiplayer_you_lost", comment: "Loser state")
    static let multiplayerDraw = NSLocalizedString("multiplayer_draw", comment: "Draw state")
    static let multiplayerRoomClosed = NSLocalizedString("multiplayer_room_closed", comment: "Closed room state")
    static let multiplayerYou = NSLocalizedString("multiplayer_you", comment: "Current player label")
    static let multiplayerOpponent = NSLocalizedString("multiplayer_opponent", comment: "Opponent player label")
    static let multiplayerFinalScore = NSLocalizedString("multiplayer_final_score", comment: "Final score label")
    static let multiplayerPlayAgain = NSLocalizedString("multiplayer_play_again", comment: "Restart multiplayer game action")
    static let multiplayerWaitingForRematch = NSLocalizedString("multiplayer_waiting_for_rematch", comment: "Guest waiting for host rematch state")
    static let multiplayerChooseAnotherGame = NSLocalizedString("multiplayer_choose_another_game", comment: "Choose another multiplayer game action")
    static let multiplayerHostName = NSLocalizedString("multiplayer_host_name", comment: "Default host name")
    static let multiplayerGuestName = NSLocalizedString("multiplayer_guest_name", comment: "Default guest name")
    static let multiplayerGenericError = NSLocalizedString("multiplayer_generic_error", comment: "Generic multiplayer error")
    static let multiplayerErrorFirebaseUnavailable = NSLocalizedString("multiplayer_error_firebase_unavailable", comment: "Firebase unavailable error")
    static let multiplayerErrorAuthentication = NSLocalizedString("multiplayer_error_authentication", comment: "Authentication error")
    static let multiplayerErrorRoomNotFound = NSLocalizedString("multiplayer_error_room_not_found", comment: "Room not found error")
    static let multiplayerErrorRoomFull = NSLocalizedString("multiplayer_error_room_full", comment: "Room full error")
    static let multiplayerErrorRoomUnavailable = NSLocalizedString("multiplayer_error_room_unavailable", comment: "Room unavailable error")
    static let multiplayerErrorInvalidMove = NSLocalizedString("multiplayer_error_invalid_move", comment: "Invalid move error")
    static let multiplayerErrorPermissionDenied = NSLocalizedString("multiplayer_error_permission_denied", comment: "Firebase rules denied multiplayer access")
    static let multiplayerErrorEncoding = NSLocalizedString("multiplayer_error_encoding", comment: "Encoding error")
    static let multiplayerErrorDecoding = NSLocalizedString("multiplayer_error_decoding", comment: "Decoding error")

    // Notifications settings
    static let settingsHapticsTitle = NSLocalizedString("settings_haptics_title", comment: "Haptic feedback setting title")
    static let settingsHapticsDescriptionOn = NSLocalizedString("settings_haptics_description_on", comment: "Haptics enabled description")
    static let settingsHapticsDescriptionOff = NSLocalizedString("settings_haptics_description_off", comment: "Haptics disabled description")
    static let settingsNotificationsTitle = NSLocalizedString("settings_notifications_title", comment: "Notifications setting title")
    static let settingsNotificationsDescriptionOn = NSLocalizedString("settings_notifications_description_on", comment: "Notifications enabled description")
    static let settingsNotificationsDescriptionOff = NSLocalizedString("settings_notifications_description_off", comment: "Notifications disabled description")
    static let settingsNotificationsDeniedTitle = NSLocalizedString("settings_notifications_denied_title", comment: "Alert title when notification permission denied")
    static let settingsNotificationsDeniedMessage = NSLocalizedString("settings_notifications_denied_message", comment: "Alert message when notification permission denied")
    static let settingsNotificationsOpenSettings = NSLocalizedString("settings_notifications_open_settings", comment: "Button to open iOS Settings")

    // Settings sections
    static let settingsSectionGame = NSLocalizedString("settings_section_game", comment: "Settings section: game")
    static let settingsSectionPreferences = NSLocalizedString("settings_section_preferences", comment: "Settings section: preferences")
    static let settingsSectionPurchases = NSLocalizedString("settings_section_purchases", comment: "Settings section: purchases")
    static let settingsSectionAbout = NSLocalizedString("settings_section_about", comment: "Settings section: about")

    // Notification permission primer
    static let notificationPrimerTitle = NSLocalizedString("notification_primer_title", comment: "Notification primer headline")
    static let notificationPrimerMessage = NSLocalizedString("notification_primer_message", comment: "Notification primer subtitle introducing the benefits")
    static let notificationPrimerBenefitStreak = NSLocalizedString("notification_primer_benefit_streak", comment: "Notification primer benefit: streak about to end")
    static let notificationPrimerBenefitInactive = NSLocalizedString("notification_primer_benefit_inactive", comment: "Notification primer benefit: haven't played in a while")
    static let notificationPrimerBenefitNoSpam = NSLocalizedString("notification_primer_benefit_no_spam", comment: "Notification primer benefit: reminders stay rare")
    static let notificationPrimerPreviewTitle = NSLocalizedString("notification_primer_preview_title", comment: "Title of the sample notification shown in the primer")
    static let notificationPrimerPreviewMessage = NSLocalizedString("notification_primer_preview_message", comment: "Body of the sample notification shown in the primer")
    static let notificationPrimerEnable = NSLocalizedString("notification_primer_enable", comment: "Notification primer primary button")
    static let notificationPrimerLater = NSLocalizedString("notification_primer_later", comment: "Notification primer secondary button")

    // Review and What's New settings rows
    static let settingsReviewTitle = NSLocalizedString("settings_review_title", comment: "Write a review setting title")
    static let settingsReviewDescription = NSLocalizedString("settings_review_description", comment: "Write a review setting description")
    static let settingsWhatsNewTitle = NSLocalizedString("settings_whats_new_title", comment: "What's New setting title")
    static let settingsWhatsNewDescription = NSLocalizedString("settings_whats_new_description", comment: "What's New setting description")

    // What's New sheet (v3.0.0)
    static let whatsNewTitle = NSLocalizedString("whats_new_title", comment: "What's New sheet headline")
    static let whatsNewButton = NSLocalizedString("whats_new_button", comment: "What's New dismiss button")
    static let whatsNewMultiplayerTitle = NSLocalizedString("whats_new_multiplayer_title", comment: "Multiplayer feature title in What's New")
    static let whatsNewMultiplayerDescription = NSLocalizedString("whats_new_multiplayer_description", comment: "Multiplayer feature description in What's New")
    static let whatsNewRemindersTitle = NSLocalizedString("whats_new_reminders_title", comment: "Reminders feature title in What's New")
    static let whatsNewRemindersDescription = NSLocalizedString("whats_new_reminders_description", comment: "Reminders feature description in What's New")
    static let whatsNewThemeTitle = NSLocalizedString("whats_new_theme_title", comment: "Theme feature title in What's New")
    static let whatsNewThemeDescription = NSLocalizedString("whats_new_theme_description", comment: "Theme feature description in What's New")
    static let whatsNewHintTitle = NSLocalizedString("whats_new_hint_title", comment: "Hint feature title in What's New")
    static let whatsNewHintDescription = NSLocalizedString("whats_new_hint_description", comment: "Hint feature description in What's New")

    // What's New sheet (v3.1.0)
    static let whatsNewGamesTitle = NSLocalizedString("whats_new_games_title", comment: "New games feature title in What's New")
    static let whatsNewGamesDescription = NSLocalizedString("whats_new_games_description", comment: "New games feature description in What's New")

    // What's New sheet (v4.0.0)
    static let whatsNewLevelsTitle = NSLocalizedString("whats_new_levels_title", comment: "Levels mode title in What's New")
    static let whatsNewLevelsDescription = NSLocalizedString("whats_new_levels_description", comment: "Levels mode description in What's New")
    static let whatsNewStarsTitle = NSLocalizedString("whats_new_stars_title", comment: "Star rating title in What's New")
    static let whatsNewStarsDescription = NSLocalizedString("whats_new_stars_description", comment: "Star rating description in What's New")
    static let whatsNewPowerUpsTitle = NSLocalizedString("whats_new_powerups_title", comment: "Power-ups title in What's New")
    static let whatsNewPowerUpsDescription = NSLocalizedString("whats_new_powerups_description", comment: "Power-ups description in What's New")
    static let whatsNewLivesTitle = NSLocalizedString("whats_new_lives_title", comment: "Daily lives and mistake budget title in What's New")
    static let whatsNewLivesDescription = NSLocalizedString("whats_new_lives_description", comment: "Daily lives and mistake budget description in What's New")
}
