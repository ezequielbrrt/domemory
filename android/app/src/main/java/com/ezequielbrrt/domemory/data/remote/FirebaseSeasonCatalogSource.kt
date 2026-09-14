package com.ezequielbrrt.domemory.data.remote

import android.util.Log
import com.ezequielbrrt.domemory.services.seasons.SeasonCatalogSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Reads `/seasons` — the season catalog (spec 9.2) — with a single snapshot after
 * authenticating anonymously, mirroring [FirebaseBoardCatalogSource] exactly (same
 * anonymous-session warmup, same silent-non-fatal-failure shape, spec 13.1).
 *
 * `/seasons` is a *dictionary* keyed by season id, unlike `/data`'s array, so the raw
 * snapshot value is a `Map<String, Any?>` rather than a `List`; [SeasonPayloadJson]
 * converts it to the JSON text `SeasonDecoder.decode` expects. Every failure path —
 * auth failure, network failure, an absent or unexpected node, a value that will not
 * serialize — resolves to `null`, which [com.ezequielbrrt.domemory.services.seasons.SeasonCatalogService]
 * treats as "no correction available" and leaves the cached catalog exactly as it was
 * (spec 9.7: "An absent or unexpected `/seasons` node leaves the cached catalog in place
 * rather than wiping it").
 */
class FirebaseSeasonCatalogSource(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
) : SeasonCatalogSource {

    override suspend fun load(): String? {
        ensureAnonymousSession()
        return runCatching {
            val snapshot = database.reference.child(SEASONS_NODE).get().await()
            SeasonPayloadJson.encode(snapshot.value)
        }.getOrElse { error ->
            Log.w(TAG, "Season catalog unavailable; keeping the cached catalog", error)
            null
        }
    }

    private suspend fun ensureAnonymousSession() {
        if (auth.currentUser != null) return
        runCatching { auth.signInAnonymously().await() }
            .onFailure { Log.w(TAG, "Anonymous sign-in failed", it) }
    }

    private companion object {
        const val SEASONS_NODE = "seasons"
        const val TAG = "SeasonCatalog"
    }
}
