package com.ezequielbrrt.domemory.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * The typed DataStore Preferences surface for every key in spec 13.2 (`ANDROID_PLAN.md`
 * package layout calls this `data/prefs/`). One [DataStore] backs the whole app; the
 * only exception the spec itself calls out is a second, shared file for the Daily
 * Challenge widget (spec 13.2's closing Android note) — that file does not exist yet
 * (Phase 5), so [dailyStreakCurrent] and its siblings below live here for now. Nothing
 * in their shape prevents moving them into a second `DataStore<Preferences>` later:
 * they are plain top-level keys, not nested under anything this file owns exclusively.
 *
 * Every accessor is a [Flow] (or a suspend function) rather than a synchronous getter,
 * because DataStore itself is asynchronous — there is deliberately no `T` back door here,
 * only `Flow<T>` and `suspend fun set...`. Callers that need a synchronous value (like
 * Phase 1's `Phase1Root`) are a Phase 2+ menu-rebuild concern, not this layer's.
 *
 * Per-item keys (`stats.<boardId>.*`, `levels.stars.<n>`, `season.<id>.*`) are built by
 * private key functions below so no call site ever string-concatenates a key itself.
 */
class UserPreferences(private val dataStore: DataStore<Preferences>) {

    // -- Onboarding ----------------------------------------------------------------
    //
    // iOS marks "has onboarded" by the mere existence of a one-row CoreData record
    // (spec 13.2). D5 in ANDROID_PLAN.md locks Android to an explicit boolean instead
    // — this is that flag. It intentionally does not resurrect the misspelled legacy
    // `dificulty` Prefs key iOS also lists in 13.2 as "superseded by CoreData"; that key
    // is iOS-only CoreData-migration debris and has no Android equivalent here.

    val hasOnboarded: Flow<Boolean> = booleanFlow(Keys.HAS_ONBOARDED, default = false)

    suspend fun setHasOnboarded(value: Boolean) = setBoolean(Keys.HAS_ONBOARDED, value)

    // -- Favourites (13.4) -----------------------------------------------------------

    val favoriteIds: Flow<Set<String>> =
        dataStore.data.map { it[Keys.FAVORITE_IDS].orEmpty() }.distinctUntilChanged()

    suspend fun setFavorite(boardId: String, isFavorite: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_IDS].orEmpty()
            prefs[Keys.FAVORITE_IDS] = if (isFavorite) current + boardId else current - boardId
        }
    }

    suspend fun toggleFavorite(boardId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_IDS].orEmpty()
            prefs[Keys.FAVORITE_IDS] = if (boardId in current) current - boardId else current + boardId
        }
    }

    // -- Custom memoramas (13.3) ------------------------------------------------------
    //
    // Stored as a JSON array, decoded into the existing [Board] model — a custom board
    // already fits it (`Board.CUSTOM_ID_PREFIX`, spec 13.3), so no separate domain type
    // is introduced here. Decoding is tolerant, like `BoardDecoder`: a malformed entry
    // is dropped rather than corrupting the whole list, and a malformed *document*
    // (not valid JSON at all) reads back as an empty list rather than crashing.

    val customMemoramas: Flow<List<Board>> = dataStore.data.map { prefs ->
        decodeCustomBoards(prefs[Keys.CUSTOM_MEMORAMAS])
    }.distinctUntilChanged()

    suspend fun setCustomMemoramas(boards: List<Board>) {
        dataStore.edit { prefs -> prefs[Keys.CUSTOM_MEMORAMAS] = encodeCustomBoards(boards) }
    }

    /**
     * Adds a custom board. Returns `false` without writing anything if [board] has fewer
     * than two items — [decodeCustomBoard] silently drops such a board on read, so the
     * creation flow needs to know the add was rejected rather than watching it vanish.
     */
    suspend fun addCustomMemorama(board: Board): Boolean {
        if (board.items.size < 2) return false
        dataStore.edit { prefs ->
            val current = decodeCustomBoards(prefs[Keys.CUSTOM_MEMORAMAS])
            prefs[Keys.CUSTOM_MEMORAMAS] = encodeCustomBoards(current + board)
        }
        return true
    }

    /**
     * Also clears the board's own stats (spec 13.3: "deleting one also clears its stats")
     * and drops it from favourites — a deleted board must not leave an orphaned favourite
     * id behind.
     */
    suspend fun removeCustomMemorama(boardId: String) {
        dataStore.edit { prefs ->
            val current = decodeCustomBoards(prefs[Keys.CUSTOM_MEMORAMAS])
            prefs[Keys.CUSTOM_MEMORAMAS] = encodeCustomBoards(current.filterNot { it.id == boardId })
            prefs.remove(statsPlayedKey(boardId))
            prefs.remove(statsWonKey(boardId))
            prefs[Keys.FAVORITE_IDS] = prefs[Keys.FAVORITE_IDS].orEmpty() - boardId
        }
    }

    // -- Appearance --------------------------------------------------------------

    val themePreference: Flow<ThemePreference> = dataStore.data.map { prefs ->
        parseThemePreference(prefs[Keys.THEME_PREFERENCE])
    }.distinctUntilChanged()

    suspend fun setThemePreference(value: ThemePreference) {
        dataStore.edit { prefs -> prefs[Keys.THEME_PREFERENCE] = value.name.lowercase() }
    }

    // -- Haptics -------------------------------------------------------------------
    //
    // Must default to true when unset — read as "value or true", never "bool or false"
    // (see `ios/DoMemory/DoMemory/Services/Haptics/HapticsService.swift` for the iOS
    // version of this exact comment, and the risk register in `ANDROID_PLAN.md` §6,
    // which names this key specifically). Getting this backwards ships the whole
    // feature silently disabled for every player who has never touched the setting.

    val hapticsEnabled: Flow<Boolean> = booleanFlow(Keys.HAPTICS_ENABLED, default = true)

    suspend fun setHapticsEnabled(value: Boolean) = setBoolean(Keys.HAPTICS_ENABLED, value)

    // -- Notifications (11.2, 11.3) ------------------------------------------------

    val notificationsEnabled: Flow<Boolean> = booleanFlow(Keys.NOTIFICATIONS_ENABLED, default = false)

    suspend fun setNotificationsEnabled(value: Boolean) = setBoolean(Keys.NOTIFICATIONS_ENABLED, value)

    val notificationPrimerShown: Flow<Boolean> = booleanFlow(Keys.NOTIFICATION_PRIMER_SHOWN, default = false)

    suspend fun setNotificationPrimerShown(value: Boolean) = setBoolean(Keys.NOTIFICATION_PRIMER_SHOWN, value)

    // -- What's New (15.1) ----------------------------------------------------------

    val whatsNewLastSeenVersion: Flow<String?> =
        dataStore.data.map { it[Keys.WHATS_NEW_LAST_SEEN_VERSION] }.distinctUntilChanged()

    suspend fun setWhatsNewLastSeenVersion(version: String) {
        dataStore.edit { prefs -> prefs[Keys.WHATS_NEW_LAST_SEEN_VERSION] = version }
    }

    // -- One-shot intro carousels ----------------------------------------------------

    val onboardingIntroShown: Flow<Boolean> = booleanFlow(Keys.ONBOARDING_INTRO_SHOWN, default = false)

    suspend fun setOnboardingIntroShown(value: Boolean) = setBoolean(Keys.ONBOARDING_INTRO_SHOWN, value)

    val levelsIntroShown: Flow<Boolean> = booleanFlow(Keys.LEVELS_INTRO_SHOWN, default = false)

    suspend fun setLevelsIntroShown(value: Boolean) = setBoolean(Keys.LEVELS_INTRO_SHOWN, value)

    // -- Season catalog cache (9.7, 13.2) ---------------------------------------------
    //
    // Opaque cache of the raw `/seasons` payload. No `Season` model exists yet
    // (Phase 4), so this stores and returns the JSON text as-is rather than parsing it
    // — parsing belongs to `SeasonCatalogService` when that phase is built.

    val seasonCatalogJson: Flow<String?> =
        dataStore.data.map { it[Keys.SEASON_CATALOG] }.distinctUntilChanged()

    suspend fun setSeasonCatalogJson(json: String?) {
        dataStore.edit { prefs ->
            if (json == null) prefs.remove(Keys.SEASON_CATALOG) else prefs[Keys.SEASON_CATALOG] = json
        }
    }

    // -- Per-board stats (13.2) -------------------------------------------------------

    fun boardPlayedCount(boardId: String): Flow<Int> = intFlow(statsPlayedKey(boardId), default = 0)

    fun boardWonCount(boardId: String): Flow<Int> = intFlow(statsWonKey(boardId), default = 0)

    suspend fun recordBoardPlayed(boardId: String) = incrementInt(statsPlayedKey(boardId))

    suspend fun recordBoardWon(boardId: String) = incrementInt(statsWonKey(boardId))

    suspend fun clearBoardStats(boardId: String) {
        dataStore.edit { prefs ->
            prefs.remove(statsPlayedKey(boardId))
            prefs.remove(statsWonKey(boardId))
        }
    }

    // -- Lifetime profile aggregates (13.2) --------------------------------------------

    val totalPlayed: Flow<Int> = intFlow(Keys.PROFILE_TOTAL_PLAYED, default = 0)

    suspend fun incrementTotalPlayed() = incrementInt(Keys.PROFILE_TOTAL_PLAYED)

    val totalWon: Flow<Int> = intFlow(Keys.PROFILE_TOTAL_WON, default = 0)

    suspend fun incrementTotalWon() = incrementInt(Keys.PROFILE_TOTAL_WON)

    val perfectGames: Flow<Int> = intFlow(Keys.PROFILE_PERFECT_GAMES, default = 0)

    suspend fun incrementPerfectGames() = incrementInt(Keys.PROFILE_PERFECT_GAMES)

    val multiplayerWins: Flow<Int> = intFlow(Keys.PROFILE_MULTIPLAYER_WINS, default = 0)

    suspend fun incrementMultiplayerWins() = incrementInt(Keys.PROFILE_MULTIPLAYER_WINS)

    /** Null until a game has been recorded on that difficulty at all. */
    fun bestRemaining(difficulty: Difficulty): Flow<Int?> =
        dataStore.data.map { it[bestRemainingKey(difficulty)] }.distinctUntilChanged()

    suspend fun setBestRemaining(difficulty: Difficulty, remaining: Int) {
        dataStore.edit { prefs -> prefs[bestRemainingKey(difficulty)] = remaining }
    }

    // -- Endless Levels (7, 13.2) -------------------------------------------------------

    val levelsHighestUnlocked: Flow<Int> = intFlow(Keys.LEVELS_HIGHEST_UNLOCKED, default = 0)

    suspend fun setLevelsHighestUnlocked(level: Int) {
        dataStore.edit { prefs -> prefs[Keys.LEVELS_HIGHEST_UNLOCKED] = level }
    }

    /** Stars earned on a given level, 0 until played. */
    fun levelStars(level: Int): Flow<Int> = intFlow(levelStarsKey(level), default = 0)

    suspend fun setLevelStars(level: Int, stars: Int) {
        dataStore.edit { prefs -> prefs[levelStarsKey(level)] = stars }
    }

    val levelsLifetimeStars: Flow<Int> = intFlow(Keys.LEVELS_LIFETIME_STARS, default = 0)

    suspend fun addLifetimeStars(delta: Int) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.LEVELS_LIFETIME_STARS] ?: 0
            prefs[Keys.LEVELS_LIFETIME_STARS] = current + delta
        }
    }

    val levelsWalletBalance: Flow<Int> = intFlow(Keys.LEVELS_WALLET_BALANCE, default = 0)

    suspend fun addToWallet(delta: Int) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.LEVELS_WALLET_BALANCE] ?: 0
            prefs[Keys.LEVELS_WALLET_BALANCE] = current + delta
        }
    }

    /** Atomically debits the wallet; returns false (no write) if the balance is short. */
    suspend fun trySpendFromWallet(amount: Int): Boolean {
        var spent = false
        dataStore.edit { prefs ->
            val current = prefs[Keys.LEVELS_WALLET_BALANCE] ?: 0
            if (current >= amount) {
                prefs[Keys.LEVELS_WALLET_BALANCE] = current - amount
                spent = true
            }
        }
        return spent
    }

    // -- Daily lives (7.4, 13.2) ---------------------------------------------------------

    val levelsLivesRemaining: Flow<Int> = intFlow(Keys.LEVELS_LIVES_REMAINING, default = 0)

    suspend fun setLevelsLivesRemaining(remaining: Int) {
        dataStore.edit { prefs -> prefs[Keys.LEVELS_LIVES_REMAINING] = remaining }
    }

    /** The day key (`DayKey.of`) lives were last reset on; null before the first reset. */
    val levelsLivesLastResetDay: Flow<String?> =
        dataStore.data.map { it[Keys.LEVELS_LIVES_LAST_RESET_DAY] }.distinctUntilChanged()

    suspend fun setLevelsLivesLastResetDay(dayKey: String) {
        dataStore.edit { prefs -> prefs[Keys.LEVELS_LIVES_LAST_RESET_DAY] = dayKey }
    }

    // -- Season progress, per season id (9.6, 13.2) --------------------------------------

    fun seasonHighestUnlocked(seasonId: String): Flow<Int> =
        intFlow(seasonHighestUnlockedKey(seasonId), default = 0)

    suspend fun setSeasonHighestUnlocked(seasonId: String, level: Int) {
        dataStore.edit { prefs -> prefs[seasonHighestUnlockedKey(seasonId)] = level }
    }

    fun seasonStars(seasonId: String, level: Int): Flow<Int> =
        intFlow(seasonStarsKey(seasonId, level), default = 0)

    suspend fun setSeasonStars(seasonId: String, level: Int, stars: Int) {
        dataStore.edit { prefs -> prefs[seasonStarsKey(seasonId, level)] = stars }
    }

    // -- Daily Challenge (8, 13.2) --------------------------------------------------------
    //
    // Spec 13.2 marks these "Prefs (shared) ... shared with the widget". They stay in
    // this same DataStore file for now — see the class doc — until Phase 5 builds the
    // Glance widget and its cross-process file.

    val dailyStreakCurrent: Flow<Int> = intFlow(Keys.DAILY_STREAK_CURRENT, default = 0)

    suspend fun setDailyStreakCurrent(value: Int) {
        dataStore.edit { prefs -> prefs[Keys.DAILY_STREAK_CURRENT] = value }
    }

    val dailyStreakLongest: Flow<Int> = intFlow(Keys.DAILY_STREAK_LONGEST, default = 0)

    suspend fun setDailyStreakLongest(value: Int) {
        dataStore.edit { prefs -> prefs[Keys.DAILY_STREAK_LONGEST] = value }
    }

    val dailyLastCompletedDay: Flow<String?> =
        dataStore.data.map { it[Keys.DAILY_LAST_COMPLETED_DAY] }.distinctUntilChanged()

    suspend fun setDailyLastCompletedDay(dayKey: String) {
        dataStore.edit { prefs -> prefs[Keys.DAILY_LAST_COMPLETED_DAY] = dayKey }
    }

    val dailyLastAttemptDay: Flow<String?> =
        dataStore.data.map { it[Keys.DAILY_LAST_ATTEMPT_DAY] }.distinctUntilChanged()

    suspend fun setDailyLastAttemptDay(dayKey: String) {
        dataStore.edit { prefs -> prefs[Keys.DAILY_LAST_ATTEMPT_DAY] = dayKey }
    }

    // -- Purchases / entitlements (12.3, 13.2) ---------------------------------------------

    val hasRemovedAdsPurchased: Flow<Boolean> = booleanFlow(Keys.PURCHASES_HAS_REMOVED_ADS, default = false)

    suspend fun setHasRemovedAdsPurchased(value: Boolean) = setBoolean(Keys.PURCHASES_HAS_REMOVED_ADS, value)

    /** Epoch millis the rewarded "free ad-free day" expires at; null when none is active. */
    val rewardedRemoveAdsExpirationEpochMillis: Flow<Long?> =
        dataStore.data.map { it[Keys.PURCHASES_REWARDED_REMOVE_ADS_EXPIRATION] }.distinctUntilChanged()

    suspend fun setRewardedRemoveAdsExpirationEpochMillis(epochMillis: Long?) {
        dataStore.edit { prefs ->
            if (epochMillis == null) {
                prefs.remove(Keys.PURCHASES_REWARDED_REMOVE_ADS_EXPIRATION)
            } else {
                prefs[Keys.PURCHASES_REWARDED_REMOVE_ADS_EXPIRATION] = epochMillis
            }
        }
    }

    // -- Ads frequency capping (12.2, 13.2) -------------------------------------------------

    val gameFinishedInterstitialCompletionCount: Flow<Int> =
        intFlow(Keys.ADS_INTERSTITIAL_COMPLETION_COUNT, default = 0)

    suspend fun incrementInterstitialCompletionCount() = incrementInt(Keys.ADS_INTERSTITIAL_COMPLETION_COUNT)

    suspend fun resetInterstitialCompletionCount() {
        dataStore.edit { prefs -> prefs[Keys.ADS_INTERSTITIAL_COMPLETION_COUNT] = 0 }
    }

    // -- Shared helpers ------------------------------------------------------------------

    private fun booleanFlow(key: Preferences.Key<Boolean>, default: Boolean): Flow<Boolean> =
        dataStore.data.map { it[key] ?: default }.distinctUntilChanged()

    private fun intFlow(key: Preferences.Key<Int>, default: Int): Flow<Int> =
        dataStore.data.map { it[key] ?: default }.distinctUntilChanged()

    private suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { prefs -> prefs[key] = value }
    }

    private suspend fun incrementInt(key: Preferences.Key<Int>) {
        dataStore.edit { prefs -> prefs[key] = (prefs[key] ?: 0) + 1 }
    }

    private companion object {
        fun parseThemePreference(raw: String?): ThemePreference =
            ThemePreference.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: ThemePreference.SYSTEM

        fun encodeCustomBoards(boards: List<Board>): String {
            val array = JSONArray()
            boards.forEach { board -> array.put(encodeCustomBoard(board)) }
            return array.toString()
        }

        fun encodeCustomBoard(board: Board): JSONObject = JSONObject().apply {
            put("id", board.id)
            put("name", board.name)
            put("category", board.category)
            put("description", board.description)
            put("difficulty", board.difficulty)
            put("publishedDate", board.publishedDate)
            put("items", JSONArray(board.items))
            put("itemType", board.itemType)
            put("isDoubleItem", board.isDoubleItem)
        }

        fun decodeCustomBoards(raw: String?): List<Board> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let(::decodeCustomBoard)
                }
            }.getOrElse { emptyList() }
        }

        fun decodeCustomBoard(json: JSONObject): Board? {
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
            val items = json.optJSONArray("items")
                ?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) } }
                .orEmpty()
            // A board with fewer than two items cannot produce a pair (mirrors BoardDecoder).
            if (items.size < 2) return null

            return Board(
                id = id,
                name = json.optString("name", ""),
                category = json.optString("category", ""),
                description = json.optString("description", ""),
                difficulty = json.optString("difficulty").takeIf { it.isNotBlank() },
                publishedDate = json.optString("publishedDate").takeIf { it.isNotBlank() },
                items = items,
                itemType = json.optString("itemType", "String"),
                isDoubleItem = json.optBoolean("isDoubleItem", true),
            )
        }
    }
}

/** All Preferences keys, static and dynamic, live here so no call site builds one by hand. */
private object Keys {
    val HAS_ONBOARDED = booleanPreferencesKey("hasOnboarded")
    val FAVORITE_IDS = stringSetPreferencesKey("favoriteIDs")
    val CUSTOM_MEMORAMAS = stringPreferencesKey("customMemoramas")
    val THEME_PREFERENCE = stringPreferencesKey("themePreference")
    val HAPTICS_ENABLED = booleanPreferencesKey("hapticsEnabled")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notificationsEnabled")
    val NOTIFICATION_PRIMER_SHOWN = booleanPreferencesKey("notificationPrimerShown")
    val WHATS_NEW_LAST_SEEN_VERSION = stringPreferencesKey("whatsNewLastSeenVersion")
    val ONBOARDING_INTRO_SHOWN = booleanPreferencesKey("onboardingIntroShown")
    val LEVELS_INTRO_SHOWN = booleanPreferencesKey("levelsIntroShown")
    val SEASON_CATALOG = stringPreferencesKey("seasonCatalog")

    val PROFILE_TOTAL_PLAYED = intPreferencesKey("profile.totalPlayed")
    val PROFILE_TOTAL_WON = intPreferencesKey("profile.totalWon")
    val PROFILE_PERFECT_GAMES = intPreferencesKey("profile.perfectGames")
    val PROFILE_MULTIPLAYER_WINS = intPreferencesKey("profile.multiplayerWins")

    val LEVELS_HIGHEST_UNLOCKED = intPreferencesKey("levels.highestUnlocked")
    val LEVELS_LIFETIME_STARS = intPreferencesKey("levels.lifetimeStars")
    val LEVELS_WALLET_BALANCE = intPreferencesKey("levels.wallet.balance")
    val LEVELS_LIVES_REMAINING = intPreferencesKey("levels.lives.remaining")
    val LEVELS_LIVES_LAST_RESET_DAY = stringPreferencesKey("levels.lives.lastResetDay")

    val DAILY_STREAK_CURRENT = intPreferencesKey("dailyStreakCurrent")
    val DAILY_STREAK_LONGEST = intPreferencesKey("dailyStreakLongest")
    val DAILY_LAST_COMPLETED_DAY = stringPreferencesKey("dailyLastCompletedDay")
    val DAILY_LAST_ATTEMPT_DAY = stringPreferencesKey("dailyLastAttemptDay")

    val PURCHASES_HAS_REMOVED_ADS = booleanPreferencesKey("purchases.has_removed_ads")
    val PURCHASES_REWARDED_REMOVE_ADS_EXPIRATION = longPreferencesKey("purchases.rewarded_remove_ads_expiration_date")

    val ADS_INTERSTITIAL_COMPLETION_COUNT = intPreferencesKey("ads.game_finished_interstitial_completion_count")
}

private fun statsPlayedKey(boardId: String): Preferences.Key<Int> = intPreferencesKey("stats.$boardId.played")

private fun statsWonKey(boardId: String): Preferences.Key<Int> = intPreferencesKey("stats.$boardId.won")

private fun bestRemainingKey(difficulty: Difficulty): Preferences.Key<Int> =
    intPreferencesKey("profile.bestRemaining.${difficulty.key}")

private fun levelStarsKey(level: Int): Preferences.Key<Int> = intPreferencesKey("levels.stars.$level")

private fun seasonHighestUnlockedKey(seasonId: String): Preferences.Key<Int> =
    intPreferencesKey("season.$seasonId.highestUnlocked")

private fun seasonStarsKey(seasonId: String, level: Int): Preferences.Key<Int> =
    intPreferencesKey("season.$seasonId.stars.$level")
