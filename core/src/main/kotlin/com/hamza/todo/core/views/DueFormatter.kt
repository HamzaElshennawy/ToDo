package com.hamza.todo.core.views

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object DueFormatter {
    private val sameYear = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val otherYear = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

    fun date(date: LocalDate, today: LocalDate): String =
        when (ChronoUnit.DAYS.between(today, date)) {
            0L -> "Today"
            1L -> "Tomorrow"
            -1L -> "Yesterday"
            else -> date.format(if (date.year == today.year) sameYear else otherYear)
        }

    fun time(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    fun label(date: LocalDate, minutes: Int?, today: LocalDate): String =
        if (minutes == null) date(date, today) else "${date(date, today)}, ${time(minutes)}"

    fun reminder(offsetMinutes: Int?): String = when (offsetMinutes) {
        null -> "None"
        0 -> "At due time"
        in 1..59 -> "$offsetMinutes min before"
        60 -> "1 hour before"
        in 61..1439 -> if (offsetMinutes % 60 == 0) "${offsetMinutes / 60} hours before" else "$offsetMinutes min before"
        1440 -> "1 day before"
        else -> if (offsetMinutes % 1440 == 0) "${offsetMinutes / 1440} days before" else "$offsetMinutes min before"
    }
}
