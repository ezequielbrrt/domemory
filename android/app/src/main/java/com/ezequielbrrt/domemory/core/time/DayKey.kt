package com.ezequielbrrt.domemory.core.time

import java.time.LocalDate

/**
 * The one place the app decides what day it is (spec 8).
 *
 * Everything that needs a day boundary — the Daily Challenge, daily lives, season
 * activation windows and the widget — goes through this, so nothing can disagree
 * about midnight. Evaluated in the device's own calendar.
 *
 * The key is zero-padded `YYYYMMDD` deliberately: string ordering on that format is
 * chronological, so season windows can be compared as strings and no timezone offset
 * can shift a boundary day by one.
 */
object DayKey {
    fun of(date: LocalDate): String =
        "%04d%02d%02d".format(date.year, date.monthValue, date.dayOfMonth)

    fun parse(isoDate: String): String? = runCatching {
        val parsed = LocalDate.parse(isoDate.trim())
        of(parsed)
    }.getOrNull()
}

/** Injectable clock so day-boundary behaviour is testable without waiting for midnight. */
fun interface DayProvider {
    fun today(): LocalDate
}

val SystemDayProvider = DayProvider { LocalDate.now() }
