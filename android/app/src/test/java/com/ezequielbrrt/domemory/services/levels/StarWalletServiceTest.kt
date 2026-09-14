package com.ezequielbrrt.domemory.services.levels

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the spendable half of the star economy (spec 7.3, 7.6, 7.7): [StarWalletService.credit],
 * [StarWalletService.spend] and [StarWalletService.refresh] all go straight through
 * `UserPreferences`' atomic `dataStore.edit` transactions and update the cached
 * [StarWalletService.balance] inline, in the same suspend call — not via a separate
 * observer of `UserPreferences.levelsWalletBalance`, which does not settle reliably
 * under `runTest` (see [LevelProgressService]'s doc for the same lesson, learned there
 * first). One consequence worth pinning: because [StarWalletService.credit] can also
 * happen from *outside* this class (`LevelProgressService.recordCompletion` credits the
 * wallet directly), [StarWalletService.balance] can go stale until something calls
 * [StarWalletService.refresh] — it is not a live view of the store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StarWalletServiceTest {
    private fun TestScope.wallet(): StarWalletService {
        val file = File.createTempFile("star_wallet", ".preferences_pb").also { it.deleteOnExit() }
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(scope = this, produceFile = { file }))
        return StarWalletService(prefs, this)
    }

    @Test fun `a fresh wallet starts empty`() = runTest {
        val wallet = wallet(); advanceUntilIdle()
        assertEquals(0, wallet.balance.value)
        assertFalse(wallet.canAfford(1))
    }

    @Test fun `credit raises the balance and canAfford reflects it`() = runTest {
        val wallet = wallet(); advanceUntilIdle()
        wallet.credit(5); advanceUntilIdle()
        assertEquals(5, wallet.balance.value)
        assertTrue(wallet.canAfford(5))
        assertFalse(wallet.canAfford(6))
    }

    @Test fun `spend succeeds and debits exactly the amount when affordable`() = runTest {
        val wallet = wallet(); advanceUntilIdle()
        wallet.credit(10); advanceUntilIdle()
        assertTrue(wallet.spend(4)); advanceUntilIdle()
        assertEquals(6, wallet.balance.value)
    }

    @Test fun `spend fails and leaves the balance untouched when short`() = runTest {
        val wallet = wallet(); advanceUntilIdle()
        wallet.credit(3); advanceUntilIdle()
        assertFalse(wallet.spend(4)); advanceUntilIdle()
        assertEquals(3, wallet.balance.value)
    }

    @Test fun `spend is authoritative against the real balance, not a stale cache`() = runTest {
        // Two spends racing the same cached balance: the DataStore transaction — not
        // StarWalletService's own view of it — is what decides, so the second spend must
        // see the first one's debit rather than both reading the same starting number.
        val wallet = wallet(); advanceUntilIdle()
        wallet.credit(LevelPowerUp.LIFE_COST); advanceUntilIdle()

        assertTrue(wallet.spend(LevelPowerUp.LIFE_COST))
        assertFalse(wallet.spend(LevelPowerUp.LIFE_COST))
        advanceUntilIdle()
        assertEquals(0, wallet.balance.value)
    }

    @Test fun `refresh picks up a level-completion credit made through a different path`() = runTest {
        // recordCompletion credits the wallet straight through UserPreferences, not
        // through this StarWalletService instance, so its cached balance does not learn
        // about the credit on its own — refresh() is the explicit pull that catches it
        // up (the same "pull after a known mutation point" convention MenuViewModel uses
        // instead of a continuously-collected combine).
        val file = File.createTempFile("star_wallet_shared", ".preferences_pb").also { it.deleteOnExit() }
        val prefs = UserPreferences(PreferenceDataStoreFactory.create(scope = this, produceFile = { file }))
        val wallet = StarWalletService(prefs, this)
        val progress = LevelProgressService(prefs, this)
        advanceUntilIdle()

        progress.recordCompletion(1, true, 80.0, 90.0, 0)
        advanceUntilIdle()

        assertEquals(0, wallet.balance.value) // stale until refreshed
        assertEquals(3, wallet.refresh())
        assertEquals(3, wallet.balance.value)
        assertTrue(wallet.spend(3))
        assertEquals(0, wallet.balance.value)
    }
}
