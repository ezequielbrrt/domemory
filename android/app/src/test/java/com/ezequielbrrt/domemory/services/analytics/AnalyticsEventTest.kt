package com.ezequielbrrt.domemory.services.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [AnalyticsEvent.name]/[AnalyticsEvent.parameters] for every case — the Android
 * counterpart of iOS's own analytics naming being pinned by construction (every iOS case's
 * `name`/`parameters` are computed properties on the same enum `AnalyticsService.log` reads).
 * Every string here is copied verbatim from `ios/DoMemory/DoMemory/Configuration/
 * AppConfiguration.swift`'s `enum AnalyticsEvent` — a mismatch here is a real cross-platform
 * regression (see `AnalyticsEvent`'s own class doc), not just an Android detail.
 *
 * Pure data-class construction only: no [AnalyticsService], no Firebase, no Android runtime —
 * this needs no Robolectric or instrumentation, matching every other test in this suite.
 */
class AnalyticsEventTest {

    @Test fun `screen view uses Firebase's own event name and parameter keys`() {
        val event = AnalyticsEvent.ScreenView(screenName = "menu", screenClass = "MenuScreen")
        assertEquals("screen_view", event.name)
        assertEquals(mapOf("screen_name" to "menu", "screen_class" to "MenuScreen"), event.parameters)
    }

    @Test fun `difficulty selected`() {
        val event = AnalyticsEvent.DifficultySelected(difficulty = "hard")
        assertEquals("difficulty_selected", event.name)
        assertEquals(mapOf("difficulty" to "hard"), event.parameters)
    }

    @Test fun `menu loaded`() {
        val event = AnalyticsEvent.MenuLoaded(difficulty = "medium")
        assertEquals("menu_loaded", event.name)
        assertEquals(mapOf("difficulty" to "medium"), event.parameters)
    }

    @Test fun `game list loaded`() {
        val event = AnalyticsEvent.GameListLoaded(difficulty = "easy", gameCount = 42, customCount = 3)
        assertEquals("game_list_loaded", event.name)
        assertEquals(
            mapOf("difficulty" to "easy", "game_count" to 42, "custom_count" to 3),
            event.parameters,
        )
    }

    @Test fun `game started encodes isCustom as 1 or 0, matching iOS`() {
        val custom = AnalyticsEvent.GameStarted(source = "menu_card", difficulty = "easy", cardsCount = 12, isCustom = true)
        assertEquals("game_started", custom.name)
        assertEquals(
            mapOf("source" to "menu_card", "difficulty" to "easy", "cards_count" to 12, "is_custom" to 1),
            custom.parameters,
        )

        val notCustom = AnalyticsEvent.GameStarted(source = "levels_tab", difficulty = "hard", cardsCount = 20, isCustom = false)
        assertEquals(0, notCustom.parameters["is_custom"])
    }

    @Test fun `game finished`() {
        val event = AnalyticsEvent.GameFinished(
            result = "win",
            difficulty = "veryHard",
            cardsCount = 24,
            failedTries = 2,
            timeRemaining = 15,
            isCustom = false,
        )
        assertEquals("game_finished", event.name)
        assertEquals(
            mapOf(
                "result" to "win",
                "difficulty" to "veryHard",
                "cards_count" to 24,
                "failed_tries" to 2,
                "time_remaining" to 15,
                "is_custom" to 0,
            ),
            event.parameters,
        )
    }

    @Test fun `card tapped`() {
        val event = AnalyticsEvent.CardTapped(difficulty = "medium", cardsCount = 16, failedTries = 1)
        assertEquals("card_tapped", event.name)
        assertEquals(mapOf("difficulty" to "medium", "cards_count" to 16, "failed_tries" to 1), event.parameters)
    }

    @Test fun `pause opened and resume tapped share the same parameter shape`() {
        val pause = AnalyticsEvent.PauseOpened(difficulty = "hard", timeRemaining = 42)
        assertEquals("pause_opened", pause.name)
        assertEquals(mapOf("difficulty" to "hard", "time_remaining" to 42), pause.parameters)

        val resume = AnalyticsEvent.ResumeTapped(difficulty = "hard", timeRemaining = 40)
        assertEquals("resume_tapped", resume.name)
        assertEquals(mapOf("difficulty" to "hard", "time_remaining" to 40), resume.parameters)
    }

    @Test fun `quit confirmed`() {
        val event = AnalyticsEvent.QuitConfirmed(difficulty = "easy", timeRemaining = 30, failedTries = 4)
        assertEquals("quit_confirmed", event.name)
        assertEquals(
            mapOf("difficulty" to "easy", "time_remaining" to 30, "failed_tries" to 4),
            event.parameters,
        )
    }

    @Test fun `retry tapped distinguishes pause and lose modal sources`() {
        val fromPause = AnalyticsEvent.RetryTapped(difficulty = "medium", cardsCount = 16, source = "pause_modal")
        assertEquals("retry_tapped", fromPause.name)
        assertEquals("pause_modal", fromPause.parameters["source"])

        val fromLose = AnalyticsEvent.RetryTapped(difficulty = "medium", cardsCount = 16, source = "lose_modal")
        assertEquals("lose_modal", fromLose.parameters["source"])
    }

    @Test fun `favorite toggled encodes isFavorite as 1 or 0`() {
        val favorited = AnalyticsEvent.FavoriteToggled(gameId = "board-1", isFavorite = true)
        assertEquals("favorite_toggled", favorited.name)
        assertEquals(mapOf("game_id" to "board-1", "is_favorite" to 1), favorited.parameters)

        val unfavorited = AnalyticsEvent.FavoriteToggled(gameId = "board-1", isFavorite = false)
        assertEquals(0, unfavorited.parameters["is_favorite"])
    }

    @Test fun `custom memorama created and deleted`() {
        val created = AnalyticsEvent.CustomMemoramaCreated(gameId = "custom_1", difficulty = "medium", cardsCount = 10)
        assertEquals("custom_memorama_created", created.name)
        assertEquals(
            mapOf("game_id" to "custom_1", "difficulty" to "medium", "cards_count" to 10),
            created.parameters,
        )

        val deleted = AnalyticsEvent.CustomMemoramaDeleted(gameId = "custom_1")
        assertEquals("custom_memorama_deleted", deleted.name)
        assertEquals(mapOf("game_id" to "custom_1"), deleted.parameters)
    }

    @Test fun `multiplayer room lifecycle`() {
        val created = AnalyticsEvent.MultiplayerRoomCreated(gameId = "board-1", isCustom = false)
        assertEquals("multiplayer_room_created", created.name)
        assertEquals(mapOf("game_id" to "board-1", "is_custom" to 0), created.parameters)

        assertEquals("multiplayer_room_joined", AnalyticsEvent.MultiplayerRoomJoined.name)
        assertEquals(emptyMap<String, Any>(), AnalyticsEvent.MultiplayerRoomJoined.parameters)

        val started = AnalyticsEvent.MultiplayerGameStarted(gameId = "board-1")
        assertEquals("multiplayer_game_started", started.name)
        assertEquals(mapOf("game_id" to "board-1"), started.parameters)

        val completed = AnalyticsEvent.MultiplayerGameFinished(result = "completed")
        assertEquals("multiplayer_game_finished", completed.name)
        assertEquals(mapOf("result" to "completed"), completed.parameters)

        val disconnect = AnalyticsEvent.MultiplayerGameFinished(result = "disconnect")
        assertEquals("disconnect", disconnect.parameters["result"])
    }

    @Test fun `multiplayer invite sent and opened`() {
        val sent = AnalyticsEvent.MultiplayerInviteSent(source = "lobby")
        assertEquals("multiplayer_invite_sent", sent.name)
        assertEquals(mapOf("source" to "lobby"), sent.parameters)

        assertEquals("multiplayer_invite_opened", AnalyticsEvent.MultiplayerInviteOpened.name)
        assertEquals(emptyMap<String, Any>(), AnalyticsEvent.MultiplayerInviteOpened.parameters)
    }

    @Test fun `ad lifecycle`() {
        val event = AnalyticsEvent.AdLifecycle(placement = "levels_rewarded_life", action = "reward_earned")
        assertEquals("ad_lifecycle", event.name)
        assertEquals(mapOf("placement" to "levels_rewarded_life", "action" to "reward_earned"), event.parameters)
    }

    @Test fun `whats new shown, dismissed and opened from settings`() {
        val shown = AnalyticsEvent.WhatsNewShown(version = "1.2.0")
        assertEquals("whats_new_shown", shown.name)
        assertEquals(mapOf("version" to "1.2.0"), shown.parameters)

        val dismissed = AnalyticsEvent.WhatsNewDismissed(version = "1.2.0")
        assertEquals("whats_new_dismissed", dismissed.name)
        assertEquals(mapOf("version" to "1.2.0"), dismissed.parameters)

        val fromSettings = AnalyticsEvent.WhatsNewOpenedFromSettings(version = "1.2.0")
        assertEquals("whats_new_opened_from_settings", fromSettings.name)
        assertEquals(mapOf("version" to "1.2.0"), fromSettings.parameters)
    }

    @Test fun `notification primer shown and completed`() {
        val shown = AnalyticsEvent.NotificationPrimerShown(source = "menu")
        assertEquals("notification_primer_shown", shown.name)
        assertEquals(mapOf("source" to "menu"), shown.parameters)

        val completed = AnalyticsEvent.NotificationPrimerCompleted(source = "menu", outcome = "authorized")
        assertEquals("notification_primer_completed", completed.name)
        assertEquals(mapOf("source" to "menu", "outcome" to "authorized"), completed.parameters)
    }

    @Test fun `review link opened`() {
        val event = AnalyticsEvent.ReviewLinkOpened(source = "settings")
        assertEquals("review_link_opened", event.name)
        assertEquals(mapOf("source" to "settings"), event.parameters)
    }

    @Test fun `daily challenge started, finished and streak milestone`() {
        val started = AnalyticsEvent.DailyChallengeStarted(streak = 4)
        assertEquals("daily_challenge_started", started.name)
        assertEquals(mapOf("streak" to 4), started.parameters)

        val finished = AnalyticsEvent.DailyChallengeFinished(result = "win", streak = 5)
        assertEquals("daily_challenge_finished", finished.name)
        assertEquals(mapOf("result" to "win", "streak" to 5), finished.parameters)

        val milestone = AnalyticsEvent.StreakMilestone(days = 7)
        assertEquals("streak_milestone", milestone.name)
        assertEquals(mapOf("days" to 7), milestone.parameters)
    }

    @Test fun `result shared`() {
        val fromWin = AnalyticsEvent.ResultShared(source = "win")
        assertEquals("result_shared", fromWin.name)
        assertEquals("win", fromWin.parameters["source"])

        val fromDaily = AnalyticsEvent.ResultShared(source = "daily_challenge")
        assertEquals("daily_challenge", fromDaily.parameters["source"])
    }

    @Test fun `onboarding intro completed and skipped carry no parameters`() {
        assertEquals("onboarding_intro_completed", AnalyticsEvent.OnboardingIntroCompleted.name)
        assertEquals(emptyMap<String, Any>(), AnalyticsEvent.OnboardingIntroCompleted.parameters)

        assertEquals("onboarding_intro_skipped", AnalyticsEvent.OnboardingIntroSkipped.name)
        assertEquals(emptyMap<String, Any>(), AnalyticsEvent.OnboardingIntroSkipped.parameters)
    }

    @Test fun `level started omits season_id for endless levels`() {
        val endless = AnalyticsEvent.LevelStarted(level = 12)
        assertEquals("level_started", endless.name)
        assertEquals(mapOf("level" to 12), endless.parameters)
        assertEquals(false, endless.parameters.containsKey("season_id"))
    }

    @Test fun `level started tags season_id when present`() {
        val season = AnalyticsEvent.LevelStarted(level = 3, seasonId = "spooky")
        assertEquals(mapOf("level" to 3, "season_id" to "spooky"), season.parameters)
    }

    @Test fun `level finished, endless and season`() {
        val endless = AnalyticsEvent.LevelFinished(level = 12, result = "win", stars = 3)
        assertEquals("level_finished", endless.name)
        assertEquals(mapOf("level" to 12, "result" to "win", "stars" to 3), endless.parameters)

        val season = AnalyticsEvent.LevelFinished(level = 5, result = "lose", stars = 0, seasonId = "spooky")
        assertEquals(
            mapOf("level" to 5, "result" to "lose", "stars" to 0, "season_id" to "spooky"),
            season.parameters,
        )
    }

    @Test fun `level unlocked, endless and season`() {
        val endless = AnalyticsEvent.LevelUnlocked(level = 13)
        assertEquals("level_unlocked", endless.name)
        assertEquals(mapOf("level" to 13), endless.parameters)

        val season = AnalyticsEvent.LevelUnlocked(level = 6, seasonId = "spooky")
        assertEquals(mapOf("level" to 6, "season_id" to "spooky"), season.parameters)
    }

    @Test fun `level life consumed and granted from ad`() {
        val consumed = AnalyticsEvent.LevelLifeConsumed(livesRemaining = 2)
        assertEquals("level_life_consumed", consumed.name)
        assertEquals(mapOf("lives_remaining" to 2), consumed.parameters)

        val granted = AnalyticsEvent.LevelLifeGrantedFromAd(livesRemaining = 3)
        assertEquals("level_life_granted_from_ad", granted.name)
        assertEquals(mapOf("lives_remaining" to 3), granted.parameters)
    }

    @Test fun `level out of lives shown distinguishes endless and season tiles`() {
        val endless = AnalyticsEvent.LevelOutOfLivesShown(source = "level_tile")
        assertEquals("level_out_of_lives_shown", endless.name)
        assertEquals("level_tile", endless.parameters["source"])

        val season = AnalyticsEvent.LevelOutOfLivesShown(source = "season_tile")
        assertEquals("season_tile", season.parameters["source"])
    }

    @Test fun `level power up used`() {
        val event = AnalyticsEvent.LevelPowerUpUsed(powerUp = "revealPair", level = 8, cost = 6, balanceAfter = 4)
        assertEquals("level_power_up_used", event.name)
        assertEquals(
            mapOf("power_up" to "revealPair", "level" to 8, "cost" to 6, "balance_after" to 4),
            event.parameters,
        )
    }

    @Test fun `level life purchased with stars`() {
        val event = AnalyticsEvent.LevelLifePurchasedWithStars(cost = 10, balanceAfter = 5)
        assertEquals("level_life_purchased_with_stars", event.name)
        assertEquals(mapOf("cost" to 10, "balance_after" to 5), event.parameters)
    }

    @Test fun `level skipped`() {
        val event = AnalyticsEvent.LevelSkipped(level = 9, cost = 15, balanceAfter = 0)
        assertEquals("level_skipped", event.name)
        assertEquals(mapOf("level" to 9, "cost" to 15, "balance_after" to 0), event.parameters)
    }

    @Test fun `level failed by mistakes`() {
        val event = AnalyticsEvent.LevelFailedByMistakes(level = 4, maxFailures = 3, timeRemaining = 12)
        assertEquals("level_failed_by_mistakes", event.name)
        assertEquals(mapOf("level" to 4, "max_failures" to 3, "time_remaining" to 12), event.parameters)
    }

    @Test fun `level mistakes forgiven distinguishes stars and ad sources`() {
        val stars = AnalyticsEvent.LevelMistakesForgiven(level = 4, amount = 3, source = "stars")
        assertEquals("level_mistakes_forgiven", stars.name)
        assertEquals("stars", stars.parameters["source"])

        val ad = AnalyticsEvent.LevelMistakesForgiven(level = 4, amount = 3, source = "ad")
        assertEquals("ad", ad.parameters["source"])
    }

    @Test fun `levels intro shown, completed and skipped`() {
        val shown = AnalyticsEvent.LevelsIntroShown(source = "launch")
        assertEquals("levels_intro_shown", shown.name)
        assertEquals(mapOf("source" to "launch"), shown.parameters)

        assertEquals("levels_intro_completed", AnalyticsEvent.LevelsIntroCompleted.name)
        assertEquals(emptyMap<String, Any>(), AnalyticsEvent.LevelsIntroCompleted.parameters)

        assertEquals("levels_intro_skipped", AnalyticsEvent.LevelsIntroSkipped.name)
        assertEquals(emptyMap<String, Any>(), AnalyticsEvent.LevelsIntroSkipped.parameters)
    }

    @Test fun `season levels entered`() {
        val event = AnalyticsEvent.SeasonLevelsEntered(seasonId = "spooky")
        assertEquals("season_levels_entered", event.name)
        assertEquals(mapOf("season_id" to "spooky"), event.parameters)
    }

    @Test fun `boolean-to-int encoding is 1 or 0, never a boolean literal`() {
        // Firebase Analytics has no boolean parameter type; every boolean flag anywhere in
        // AnalyticsEvent must already be an Int by the time it reaches `parameters`.
        val events = listOf(
            AnalyticsEvent.GameStarted(source = "menu_card", difficulty = "easy", cardsCount = 12, isCustom = true),
            AnalyticsEvent.GameFinished("win", "easy", 12, 0, 30, isCustom = true),
            AnalyticsEvent.FavoriteToggled(gameId = "x", isFavorite = true),
            AnalyticsEvent.MultiplayerRoomCreated(gameId = "x", isCustom = true),
        )
        events.forEach { event ->
            event.parameters.values.forEach { value -> assertEquals(false, value is Boolean) }
        }
    }
}
