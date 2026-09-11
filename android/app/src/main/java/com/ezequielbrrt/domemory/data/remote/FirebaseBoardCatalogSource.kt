package com.ezequielbrrt.domemory.data.remote

import android.util.Log
import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.data.repository.BoardCatalogSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Reads `/data` — the board catalog (spec 13.1) — with a single snapshot after
 * authenticating anonymously.
 *
 * `/data` is world readable, so the catalog itself needs no auth. Anonymous auth is
 * established here anyway because everything else the app touches (multiplayer rooms)
 * requires it, and doing it at catalog load keeps the session warm. An auth failure
 * therefore must **not** stop the catalog read — it is logged and the read proceeds.
 *
 * Every remaining failure path degrades to an empty list. The app must stay usable with
 * no catalog at all: Levels, Daily and custom boards do not depend on it.
 */
class FirebaseBoardCatalogSource(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
) : BoardCatalogSource {

    override suspend fun load(): List<Board> {
        ensureAnonymousSession()
        return runCatching {
            val snapshot = database.reference.child(DATA_NODE).get().await()
            BoardDecoder.decodeCatalog(snapshot.value)
        }.getOrElse { error ->
            Log.w(TAG, "Board catalog unavailable; continuing without it", error)
            emptyList()
        }
    }

    private suspend fun ensureAnonymousSession() {
        if (auth.currentUser != null) return
        runCatching { auth.signInAnonymously().await() }
            .onFailure { Log.w(TAG, "Anonymous sign-in failed", it) }
    }

    private companion object {
        const val DATA_NODE = "data"
        const val TAG = "BoardCatalog"
    }
}
