package com.ezequielbrrt.domemory.core.deeplink

import java.net.URI

/**
 * The link forms spec 11.1 accepts, parsed from the raw URI string.
 *
 * Deliberately parsed with [java.net.URI] rather than `android.net.Uri`: the Android SDK
 * stub jar plain JUnit tests run against throws on most `android.net.Uri` method bodies
 * (no Robolectric here — see the root `CLAUDE.md` test policy), and `java.net.URI` is both
 * sufficient for this shape and already the pattern `Season`'s artwork-URL validation uses
 * for the same reason.
 */
sealed interface DeepLink {
    /** `domemory://daily` or `https://domemory.app/daily` — open today's Daily Challenge. */
    data object Daily : DeepLink

    /** `domemory://join/CODE`, `https://domemory.app/join/CODE`, or `?code=CODE` — a 6-char room code. */
    data class Join(val code: String) : DeepLink

    /**
     * `domemory://season/<id>` or `https://domemory.app/season/<id>` — open a season's level
     * map. The App Store in-app event for a season opens the app with it (iOS's
     * `SeasonDeepLink`). [seasonId] is null for `domemory://season`, which asks for whichever
     * season is active, and an id is only ever matched against the *active* season, so a
     * link for a season that has ended leaves the player on the menu.
     */
    data class OpenSeason(val seasonId: String?, val receivedAtMillis: Long) : DeepLink {
        enum class Resolution { OPEN, WAIT, DROP }

        /**
         * [Resolution.WAIT] while no season is known yet — on a first launch the catalog
         * arrives from the network a moment after the link — but only for [MAX_WAIT_MILLIS],
         * so a season becoming active much later (say, at midnight) never pulls the player
         * into it unasked.
         */
        fun resolve(activeSeasonId: String?, nowMillis: Long): Resolution = when {
            nowMillis - receivedAtMillis > MAX_WAIT_MILLIS -> Resolution.DROP
            activeSeasonId == null -> Resolution.WAIT
            seasonId == null || seasonId == activeSeasonId -> Resolution.OPEN
            else -> Resolution.DROP
        }

        companion object {
            const val MAX_WAIT_MILLIS = 30_000L
        }
    }

    companion object {
        private const val CODE_LENGTH = 6
        private const val CUSTOM_SCHEME = "domemory"
        private const val APP_LINK_SCHEME = "https"
        private const val APP_LINK_HOST = "domemory.app"

        /** Null for anything unparseable or not one of the accepted forms. */
        fun parse(raw: String?, receivedAtMillis: Long = System.currentTimeMillis()): DeepLink? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            val isCustomScheme = uri.scheme.equals(CUSTOM_SCHEME, ignoreCase = true)
            val isAppLink = uri.scheme.equals(APP_LINK_SCHEME, ignoreCase = true) &&
                uri.host.equals(APP_LINK_HOST, ignoreCase = true)
            if (!isCustomScheme && !isAppLink) return null

            // "Gather the host (custom scheme only) plus path components": for
            // domemory://daily the host *is* "daily" (there's no "//" authority concept a
            // custom scheme needs), but https://domemory.app/daily's host is the real
            // domain and must not be treated as a path segment.
            val segments = buildList {
                if (isCustomScheme) {
                    uri.host?.takeIf(String::isNotBlank)?.let(::add)
                }
                uri.path?.split('/')?.filter(String::isNotBlank)?.let(::addAll)
            }

            if (segments.any { it.equals("daily", ignoreCase = true) }) return Daily

            // "season" must lead: domemory://join/season is an invite to room SEASON.
            if (segments.firstOrNull().equals("season", ignoreCase = true)) {
                return OpenSeason(segments.getOrNull(1), receivedAtMillis)
            }

            val afterJoin = segments.indexOfFirst { it.equals("join", ignoreCase = true) }
                .takeIf { it >= 0 }
                ?.let { segments.getOrNull(it + 1) }
            val queryCode = uri.query
                ?.split('&')
                ?.map { it.split('=', limit = 2) }
                ?.firstOrNull { it.firstOrNull().equals("code", ignoreCase = true) }
                ?.getOrNull(1)

            val code = (afterJoin ?: queryCode)?.filter(Char::isLetterOrDigit)?.uppercase()
            return code?.takeIf { it.length == CODE_LENGTH }?.let(::Join)
        }
    }
}
