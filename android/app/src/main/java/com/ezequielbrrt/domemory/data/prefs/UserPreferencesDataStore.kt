package com.ezequielbrrt.domemory.data.prefs

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * The production [DataStore] backing [UserPreferences] — everything in spec 13.2 except
 * the not-yet-built widget file (see the class doc on [UserPreferences]).
 *
 * Kept as a `Context` extension rather than inside [UserPreferences] itself so the class
 * stays constructible from a plain `DataStore<Preferences>` in tests, with no Android
 * `Context` and no Robolectric involved.
 */
private val Context.userPreferencesDataStore by preferencesDataStore(name = "domemory_prefs")

fun createUserPreferences(context: Context): UserPreferences =
    UserPreferences(context.applicationContext.userPreferencesDataStore)
