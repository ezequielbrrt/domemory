package com.ezequielbrrt.domemory.core.time

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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

    /** The reverse of [of]: a zero-padded `YYYYMMDD` key back to a [LocalDate], or null if malformed. */
    fun toLocalDate(dayKey: String): LocalDate? = runCatching { LocalDate.parse(dayKey, KEY_FORMAT) }.getOrNull()

    /** True when [current] is exactly one calendar day after [previous] — the Daily Challenge streak rule. */
    fun isConsecutiveDay(previous: String, current: String): Boolean {
        val previousDate = toLocalDate(previous) ?: return false
        val currentDate = toLocalDate(current) ?: return false
        return ChronoUnit.DAYS.between(previousDate, currentDate) == 1L
    }

    private val KEY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
}

/** Injectable clock so day-boundary behaviour is testable without waiting for midnight. */
fun interface DayProvider {
    fun today(): LocalDate
}

val SystemDayProvider = DayProvider { LocalDate.now() }
