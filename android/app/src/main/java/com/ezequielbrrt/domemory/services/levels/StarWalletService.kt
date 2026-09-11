package com.ezequielbrrt.domemory.services.levels

import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Spendable half of the Levels star economy (spec 7.3). Stars earned by clearing levels
 * are credited by [LevelProgressService.recordCompletion] straight through
 * `UserPreferences.recordLevelCompletion`; this service is where they get spent — on
 * power-ups, extra lives and level skips (spec 7.6, 7.7). Kept separate from
 * `levels.lifetimeStars` so spending never walks the player's mastery score backwards.
 *
 * iOS reads this balance synchronously off `UserDefaults`. DataStore has no synchronous
 * read, so [balance] is an in-memory cache — a `StateFlow` a caller can `collectAsState`
 * for a reactive display — kept correct by having every mutator ([credit], [spend],
 * [refresh]) update it directly inside the same suspend call that touches the store,
 * rather than by a separately-launched observer of `UserPreferences.levelsWalletBalance`.
 * That distinction matters: an earlier version derived [balance] via
 * `levelsWalletBalance.stateIn(scope, SharingStarted.Eagerly, 0)`, and while that reads
 * correctly in production, its update never reliably became visible to a test's
 * `advanceUntilIdle()` — seeing the mutation and the cache "catching up" to it are two
 * independently-scheduled coroutines connected only through DataStore's own flow
 * machinery, and a virtual-time dispatcher doesn't deterministically settle that (see
 * [LevelProgressService]'s doc for the same lesson). Updating inline removes the second
 * coroutine.
 *
 * Because [credit] can also happen from outside this class (`recordCompletion` writes
 * `LEVELS_WALLET_BALANCE` directly, without going through here), [balance] can go stale
 * after a level completion until something calls [refresh] — `LevelsViewModel.refresh()`
 * does exactly that, on the same "explicit pull after a known mutation point" convention
 * `MenuViewModel` already uses instead of a continuously-collected combine.
 */
class StarWalletService(
    private val prefs: UserPreferences,
    private val scope: CoroutineScope,
) {
    private val _balance = MutableStateFlow(0)
    val balance: StateFlow<Int> = _balance.asStateFlow()

    init {
        scope.launch { _balance.value = prefs.levelsWalletBalance.first() }
    }

    /** Best-effort synchronous check for UI gating (e.g. disabling a power-up button). */
    fun canAfford(cost: Int): Boolean = _balance.value >= cost

    /** Re-reads the persisted balance, for callers whose balance may have changed through
     * another path (a level win crediting the wallet directly). Returns the fresh value. */
    suspend fun refresh(): Int {
        val value = prefs.levelsWalletBalance.first()
        _balance.value = value
        return value
    }

    /** Adds stars to the wallet. */
    suspend fun credit(amount: Int) {
        if (amount <= 0) return
        prefs.addToWallet(amount)
        _balance.value = prefs.levelsWalletBalance.first()
    }

    /**
     * Deducts [amount] if the wallet can afford it. Returns false and leaves the balance
     * untouched when it can't, so callers can treat a `false` return as "purchase did not
     * happen" without a separate `canAfford` check racing the actual spend. The DataStore
     * transaction — not the cached [balance] — is what decides, so a spend that races a
     * stale cache can never overdraw the real balance.
     */
    suspend fun spend(amount: Int): Boolean {
        if (amount <= 0) return true
        val didSpend = prefs.trySpendFromWallet(amount)
        if (didSpend) _balance.value = prefs.levelsWalletBalance.first()
        return didSpend
    }
}
