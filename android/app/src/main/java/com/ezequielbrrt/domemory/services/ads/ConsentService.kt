package com.ezequielbrrt.domemory.services.ads

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ezequielbrrt.domemory.BuildConfig
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Google's consent message for regulated markets (spec 11.4), the Android twin of iOS's
 * `ConsentService`. Only the "European regulations" message is published in AdMob, so the
 * SDK shows a form only in the EEA, the UK and Switzerland; everywhere else [gatherConsent]
 * returns with nothing on screen and [canRequestAds] becomes true. The region decision is
 * the SDK's — the app never checks the player's country itself.
 *
 * No ad may be requested while [canRequestAds] is false, which is the case on a first launch
 * until the update returns. The three flags are Compose snapshot state rather than
 * `StateFlow`s — the counterpart of iOS's `@Observable` — so the `AdsService.is…Configured`
 * checks that composables call during composition recompose them when consent arrives, and
 * the menu's banner and the rewarded buttons appear without any call site collecting a flow.
 *
 * An object, like [AdsService] and [AdFreeGate], because the banner and native composables
 * and every rewarded call site need it and none of them has the app container. [install] is
 * called once from `DoMemoryApplication.onCreate`.
 */
object ConsentService {
    /** The stored status until [gatherConsent] refreshes it. */
    var canRequestAds by mutableStateOf(false)
        private set

    /** True only where regulation lets the player change their choice later — it gates the
     * Settings "Privacy options" row. */
    var isPrivacyOptionsRequired by mutableStateOf(false)
        private set

    /** Up while a consent or privacy options form may be on screen. An app-open ad must not
     * present while it is true (no app-open placement is wired on Android yet). */
    var isPresentingForm by mutableStateOf(false)
        private set

    /** Reads the status stored by a previous launch, so a returning player's banner can load
     * before this launch's update returns. */
    fun install(context: Context) {
        refresh(UserMessagingPlatform.getConsentInformation(context))
    }

    /**
     * Refreshes the consent status and shows the form when the player's region requires one
     * and no valid choice is stored. A network failure keeps the last stored status, so a
     * returning player who already chose still gets ads.
     */
    suspend fun gatherConsent(activity: Activity) {
        val information = UserMessagingPlatform.getConsentInformation(activity)
        val debugSettings = debugSettings(activity)
        if (debugSettings != null && activity.intent?.getBooleanExtra(EXTRA_RESET, false) == true) {
            log("Resetting the stored consent choice.")
            information.reset()
        }
        val parameters = ConsentRequestParameters.Builder()
            .apply { debugSettings?.let(::setConsentDebugSettings) }
            .build()
        val updated = suspendCancellableCoroutine { continuation ->
            information.requestConsentInfoUpdate(
                activity,
                parameters,
                { if (continuation.isActive) continuation.resume(true) },
                { error ->
                    log("Consent info update failed: ${error.message}")
                    if (continuation.isActive) continuation.resume(false)
                },
            )
        }
        if (updated) {
            isPresentingForm = true
            try {
                suspendCancellableCoroutine { continuation ->
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                        error?.let { log("Consent form failed: ${it.message}") }
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            } finally {
                isPresentingForm = false
            }
        }
        refresh(information)
        log("Consent gathered. canRequestAds=$canRequestAds, privacyOptionsRequired=$isPrivacyOptionsRequired")
    }

    /** Reopens the form from the Settings "Privacy options" row. */
    suspend fun showPrivacyOptions(activity: Activity) {
        isPresentingForm = true
        try {
            suspendCancellableCoroutine { continuation ->
                UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                    error?.let { log("Privacy options failed: ${it.message}") }
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        } finally {
            isPresentingForm = false
        }
        refresh(UserMessagingPlatform.getConsentInformation(activity))
    }

    private fun refresh(information: ConsentInformation) {
        update(
            canRequestAds = information.canRequestAds(),
            privacyOptionsRequired = information.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
        )
    }

    /** The single write path for the observable flags; unit tests drive the ad gates with it. */
    internal fun update(canRequestAds: Boolean, privacyOptionsRequired: Boolean) {
        this.canRequestAds = canRequestAds
        this.isPrivacyOptionsRequired = privacyOptionsRequired
    }

    /**
     * Debug builds can pretend to be in a region by launching with intent extras:
     *
     * ```
     * adb shell am start -n com.ezequielbrrt.domemory/.MainActivity \
     *     --es ump_debug_geography EEA --ez ump_reset true
     * ```
     *
     * `ump_debug_geography` is `EEA` or `OTHER` (case-insensitive). On a physical device add
     * `--es ump_test_device_id <hashed id>` — the SDK logs the id to logcat on its first
     * request; emulators are test devices already. A stored choice outlives a change of
     * region, so `ump_reset` clears it first. Release builds never set debug settings.
     */
    private fun debugSettings(activity: Activity): ConsentDebugSettings? {
        if (!BuildConfig.DEBUG) return null
        val intent: Intent = activity.intent ?: return null
        val geography = debugGeography(intent.getStringExtra(EXTRA_DEBUG_GEOGRAPHY)) ?: return null
        log("Forcing debug geography ${intent.getStringExtra(EXTRA_DEBUG_GEOGRAPHY)}.")
        return ConsentDebugSettings.Builder(activity)
            .setDebugGeography(geography)
            .apply { intent.getStringExtra(EXTRA_TEST_DEVICE_ID)?.let(::addTestDeviceHashedId) }
            .build()
    }

    /** Maps the `ump_debug_geography` extra to the SDK's constant; null leaves geography alone. */
    internal fun debugGeography(value: String?): Int? = when (value?.uppercase()) {
        "EEA" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
        "OTHER" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER
        else -> null
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private const val TAG = "ConsentService"
    private const val EXTRA_DEBUG_GEOGRAPHY = "ump_debug_geography"
    private const val EXTRA_TEST_DEVICE_ID = "ump_test_device_id"
    private const val EXTRA_RESET = "ump_reset"
}
