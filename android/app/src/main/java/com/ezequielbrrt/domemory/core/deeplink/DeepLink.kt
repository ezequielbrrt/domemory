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

    companion object {
        private const val CODE_LENGTH = 6
        private const val CUSTOM_SCHEME = "domemory"

        /** Null for anything unparseable or not one of the accepted forms. */
        fun parse(raw: String?): DeepLink? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null

            // "Gather the host (custom scheme only) plus path components": for
            // domemory://daily the host *is* "daily" (there's no "//" authority concept a
            // custom scheme needs), but https://domemory.app/daily's host is the real
            // domain and must not be treated as a path segment.
            val segments = buildList {
                if (uri.scheme.equals(CUSTOM_SCHEME, ignoreCase = true)) {
                    uri.host?.takeIf(String::isNotBlank)?.let(::add)
                }
                uri.path?.split('/')?.filter(String::isNotBlank)?.let(::addAll)
            }

            if (segments.any { it.equals("daily", ignoreCase = true) }) return Daily

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
