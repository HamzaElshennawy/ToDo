package com.hamza.todo.core.recurrence

import com.hamza.todo.core.model.Frequency
import com.hamza.todo.core.model.Recurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object RecurrenceRules {

    /** The next due date strictly after [from]. */
    fun next(rule: Recurrence, from: LocalDate): LocalDate {
        val n = rule.interval.coerceAtLeast(1).toLong()
        return when (rule.frequency) {
            Frequency.DAILY -> from.plusDays(n)
            Frequency.WEEKLY -> nextWeekly(rule, from, n)
            Frequency.MONTHLY -> nextMonthly(rule, from, n)
            Frequency.YEARLY -> from.plusYears(n)
        }
    }

    /** The first occurrence on or after [from], used when a repeating task is created without a date. */
    fun firstOnOrAfter(rule: Recurrence, from: LocalDate): LocalDate = when {
        rule.frequency == Frequency.WEEKLY && rule.weekdays.isNotEmpty() ->
            if (from.dayOfWeek.value in rule.weekdays) from else nextWeekly(rule.copy(interval = 1), from, 1)
        rule.frequency == Frequency.MONTHLY && rule.monthWeekOrdinal != null && rule.monthWeekday != null -> {
            val inThisMonth = nthWeekday(from, rule.monthWeekOrdinal, rule.monthWeekday)
            if (!inThisMonth.isBefore(from)) inThisMonth
            else nthWeekday(from.plusMonths(1), rule.monthWeekOrdinal, rule.monthWeekday)
        }
        else -> from
    }

    private fun nextWeekly(rule: Recurrence, from: LocalDate, n: Long): LocalDate {
        if (rule.weekdays.isEmpty()) return from.plusWeeks(n)
        val days = rule.weekdays.sorted()
        // Later this week?
        days.firstOrNull { it > from.dayOfWeek.value }?.let { day ->
            return from.plusDays((day - from.dayOfWeek.value).toLong())
        }
        // Otherwise the first chosen day of the week [n] weeks on.
        val weekStart = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(n)
        return weekStart.plusDays((days.first() - 1).toLong())
    }

    private fun nextMonthly(rule: Recurrence, from: LocalDate, n: Long): LocalDate {
        val ordinal = rule.monthWeekOrdinal
        val weekday = rule.monthWeekday
        if (ordinal != null && weekday != null) {
            return nthWeekday(from.plusMonths(n), ordinal, weekday)
        }
        // plusMonths clamps the 31st to the end of shorter months.
        return from.plusMonths(n)
    }

    /** The [ordinal]th [weekday] of the month containing [inMonth]; ordinal -1 = last. */
    fun nthWeekday(inMonth: LocalDate, ordinal: Int, weekday: Int): LocalDate {
        val dow = DayOfWeek.of(weekday)
        return if (ordinal < 0) {
            inMonth.with(TemporalAdjusters.lastInMonth(dow))
        } else {
            inMonth.with(TemporalAdjusters.dayOfWeekInMonth(ordinal.coerceIn(1, 5), dow)).let {
                // A 5th weekday that does not exist spills into next month; fall back to the last one.
                if (it.month != inMonth.month) inMonth.with(TemporalAdjusters.lastInMonth(dow)) else it
            }
        }
    }

    fun describe(rule: Recurrence?): String {
        if (rule == null) return "Doesn’t repeat"
        val n = rule.interval.coerceAtLeast(1)
        return when (rule.frequency) {
            Frequency.DAILY -> if (n == 1) "Every day" else "Every $n days"
            Frequency.WEEKLY -> {
                val days = rule.weekdays.sorted()
                when {
                    days == listOf(1, 2, 3, 4, 5) && n == 1 -> "Every weekday"
                    days.isEmpty() -> if (n == 1) "Every week" else "Every $n weeks"
                    else -> {
                        val names = days.joinToString(", ") { shortDay(it) }
                        if (n == 1) "Every $names" else "Every $n weeks on $names"
                    }
                }
            }
            Frequency.MONTHLY -> {
                val base = if (n == 1) "Every month" else "Every $n months"
                val ordinal = rule.monthWeekOrdinal
                val weekday = rule.monthWeekday
                if (ordinal != null && weekday != null) "$base on the ${ordinalName(ordinal)} ${longDay(weekday)}" else base
            }
            Frequency.YEARLY -> if (n == 1) "Every year" else "Every $n years"
        }
    }

    private fun shortDay(iso: Int) = longDay(iso).take(3)

    private fun longDay(iso: Int) = DayOfWeek.of(iso).name.lowercase().replaceFirstChar { it.uppercase() }

    private fun ordinalName(n: Int) = when (n) {
        -1 -> "last"
        1 -> "1st"
        2 -> "2nd"
        3 -> "3rd"
        else -> "${n}th"
    }
}
