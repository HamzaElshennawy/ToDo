package com.hamza.todo.core.parse

import com.hamza.todo.core.model.Frequency
import com.hamza.todo.core.model.Priority
import com.hamza.todo.core.model.Recurrence
import com.hamza.todo.core.recurrence.RecurrenceRules
import com.hamza.todo.core.views.DueFormatter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.temporal.TemporalAdjusters

enum class DetectedKind { DATE, PRIORITY, LIST, TAG, REPEAT }

/** A piece of the typed text that was understood, shown as a removable chip. */
data class Detected(val kind: DetectedKind, val label: String, val ranges: List<IntRange>)

data class QuickAddResult(
    val title: String,
    val dueDate: LocalDate? = null,
    val dueMinutes: Int? = null,
    val priority: Priority? = null,
    val listName: String? = null,
    val tags: List<String> = emptyList(),
    val recurrence: Recurrence? = null,
    val detected: List<Detected> = emptyList(),
)

/**
 * Reads dates, times, priority, list, tags and repeats out of a one-line task, e.g.
 * "Call mom tomorrow 5pm !high @personal #family every week".
 *
 * Kinds in `disabled` are not parsed, so their words stay in the title (used when the
 * person removes a chip).
 */
class QuickAddParser(private val listNames: List<String> = emptyList()) {

    private class Token(val text: String, val range: IntRange) {
        val word: String = text.lowercase().trimEnd(',', '.', ';')
    }

    fun parse(input: String, now: LocalDateTime, disabled: Set<DetectedKind> = emptySet()): QuickAddResult {
        val tokens = Regex("\\S+").findAll(input).map { Token(it.value, it.range) }.toList()
        val used = BooleanArray(tokens.size)
        val today = now.toLocalDate()
        val detected = mutableListOf<Detected>()

        fun mark(indices: List<Int>): List<IntRange> {
            indices.forEach { used[it] = true }
            return indices.map { tokens[it].range }
        }

        fun free(i: Int) = i in tokens.indices && !used[i]

        // Priority: !high, !h, !1, !!! … (the last one written wins)
        var priority: Priority? = null
        if (DetectedKind.PRIORITY !in disabled) {
            val hits = tokens.indices.filter { free(it) && priorityOf(tokens[it].word) != null }
            if (hits.isNotEmpty()) {
                priority = priorityOf(tokens[hits.last()].word)
                detected += Detected(DetectedKind.PRIORITY, "${priority!!.label} priority", mark(hits))
            }
        }

        // Tags: #word
        val tags = mutableListOf<String>()
        if (DetectedKind.TAG !in disabled) {
            val hits = tokens.indices.filter { free(it) && TAG.matches(tokens[it].word) }
            hits.forEach { tags += tokens[it].word.removePrefix("#") }
            if (hits.isNotEmpty()) detected += Detected(DetectedKind.TAG, tags.joinToString(" ") { "#$it" }, mark(hits))
        }

        // List: @name, matched against existing lists (spaces ignored)
        var listName: String? = null
        if (DetectedKind.LIST !in disabled) {
            for (i in tokens.indices) {
                if (!free(i) || !tokens[i].word.startsWith("@")) continue
                val wanted = normalize(tokens[i].word.removePrefix("@"))
                val match = listNames.firstOrNull { normalize(it) == wanted } ?: continue
                listName = match
                detected += Detected(DetectedKind.LIST, match, mark(listOf(i)))
                break
            }
        }

        // Repeat: every day / weekly / every 2 weeks / every monday …
        var recurrence: Recurrence? = null
        if (DetectedKind.REPEAT !in disabled) {
            for (i in tokens.indices) {
                if (!free(i)) continue
                val found = repeatAt(tokens, i, ::free) ?: continue
                recurrence = found.first
                detected += Detected(DetectedKind.REPEAT, RecurrenceRules.describe(recurrence), mark(found.second))
                break
            }
        }

        // Date and time
        var date: LocalDate? = null
        var minutes: Int? = null
        var tonight = false
        if (DetectedKind.DATE !in disabled) {
            val dateIdx = mutableListOf<Int>()
            for (i in tokens.indices) {
                if (!free(i)) continue
                val found = dateAt(tokens, i, today, ::free) ?: continue
                date = found.first
                tonight = tokens[found.second.last()].word == "tonight"
                dateIdx += found.second
                break
            }
            dateIdx.forEach { used[it] = true }
            for (i in tokens.indices) {
                if (!free(i)) continue
                val found = timeAt(tokens, i, ::free) ?: continue
                minutes = found.first
                dateIdx += found.second
                break
            }
            if (tonight && minutes == null) minutes = 20 * 60
            if (date == null && recurrence != null) {
                date = RecurrenceRules.firstOnOrAfter(recurrence, today)
            }
            if (date == null && minutes != null) {
                date = if (minutes > now.hour * 60 + now.minute) today else today.plusDays(1)
            }
            if (dateIdx.isNotEmpty() && date != null) {
                detected += Detected(DetectedKind.DATE, DueFormatter.label(date, minutes, today), mark(dateIdx))
            }
        }

        val title = tokens.indices.filter { !used[it] }.joinToString(" ") { tokens[it].text }.trim()
        return QuickAddResult(
            title = title,
            dueDate = date,
            dueMinutes = minutes,
            priority = priority,
            listName = listName,
            tags = tags.distinct(),
            recurrence = recurrence,
            detected = detected.sortedBy { it.kind.ordinal },
        )
    }

    private fun priorityOf(word: String): Priority? = when (word) {
        "!!!", "!high", "!h", "!1", "!p1" -> Priority.HIGH
        "!!", "!medium", "!med", "!m", "!2", "!p2" -> Priority.MEDIUM
        "!", "!low", "!l", "!3", "!p3" -> Priority.LOW
        else -> null
    }

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun repeatAt(t: List<Token>, i: Int, free: (Int) -> Boolean): Pair<Recurrence, List<Int>>? {
        when (t[i].word) {
            "daily" -> return Recurrence(Frequency.DAILY) to listOf(i)
            "weekly" -> return Recurrence(Frequency.WEEKLY) to listOf(i)
            "monthly" -> return Recurrence(Frequency.MONTHLY) to listOf(i)
            "yearly", "annually" -> return Recurrence(Frequency.YEARLY) to listOf(i)
            "every" -> Unit
            else -> return null
        }
        if (!free(i + 1)) return null
        val w1 = t[i + 1].word
        weekday(w1)?.let { return Recurrence(Frequency.WEEKLY, weekdays = setOf(it.value)) to listOf(i, i + 1) }
        if (w1 == "weekday") return Recurrence(Frequency.WEEKLY, weekdays = setOf(1, 2, 3, 4, 5)) to listOf(i, i + 1)
        unit(w1)?.let { return Recurrence(it) to listOf(i, i + 1) }
        val n = if (w1 == "other") 2 else number(w1)
        if (n != null && free(i + 2)) {
            unit(t[i + 2].word)?.let { return Recurrence(it, interval = n) to listOf(i, i + 1, i + 2) }
        }
        return null
    }

    private fun unit(word: String): Frequency? = when (word) {
        "day", "days" -> Frequency.DAILY
        "week", "weeks" -> Frequency.WEEKLY
        "month", "months" -> Frequency.MONTHLY
        "year", "years" -> Frequency.YEARLY
        else -> null
    }

    private fun dateAt(t: List<Token>, i: Int, today: LocalDate, free: (Int) -> Boolean): Pair<LocalDate, List<Int>>? {
        val w = t[i].word
        when (w) {
            "today", "tod", "tonight" -> return today to listOf(i)
            "tomorrow", "tmr", "tmrw" -> return today.plusDays(1) to listOf(i)
        }
        // "on" / "this" / "next" before a weekday, "next week"
        if ((w == "on" || w == "this" || w == "next") && free(i + 1)) {
            val next = t[i + 1].word
            if (w == "next" && next == "week") {
                return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) to listOf(i, i + 1)
            }
            weekday(next)?.let { return today.with(TemporalAdjusters.next(it)) to listOf(i, i + 1) }
            if (w == "on") dayMonthAt(t, i + 1, today, free)?.let { return it.first to listOf(i) + it.second }
            return null
        }
        weekday(w)?.let { return today.with(TemporalAdjusters.next(it)) to listOf(i) }
        // in 3 days / in a week
        if (w == "in" && free(i + 1) && free(i + 2)) {
            val n = number(t[i + 1].word)
            val u = unit(t[i + 2].word)
            if (n != null && u != null) {
                val d = when (u) {
                    Frequency.DAILY -> today.plusDays(n.toLong())
                    Frequency.WEEKLY -> today.plusWeeks(n.toLong())
                    Frequency.MONTHLY -> today.plusMonths(n.toLong())
                    Frequency.YEARLY -> today.plusYears(n.toLong())
                }
                return d to listOf(i, i + 1, i + 2)
            }
        }
        return dayMonthAt(t, i, today, free)
    }

    /** "12 oct", "oct 12", "12th october". Rolls to next year if the date has passed. */
    private fun dayMonthAt(t: List<Token>, i: Int, today: LocalDate, free: (Int) -> Boolean): Pair<LocalDate, List<Int>>? {
        if (!free(i) || !free(i + 1)) return null
        val a = t[i].word
        val b = t[i + 1].word
        val (day, month) = dayNumber(a)?.let { d -> month(b)?.let { d to it } }
            ?: month(a)?.let { m -> dayNumber(b)?.let { it to m } }
            ?: return null
        val thisYear = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        val date = if (thisYear.isBefore(today)) thisYear.plusYears(1) else thisYear
        return date to listOf(i, i + 1)
    }

    private fun timeAt(t: List<Token>, i: Int, free: (Int) -> Boolean): Pair<Int, List<Int>>? {
        if (t[i].word == "at" && free(i + 1)) {
            timeAt(t, i + 1, free)?.let { return it.first to listOf(i) + it.second }
            return null
        }
        val w = t[i].word
        if (w == "noon") return 12 * 60 to listOf(i)
        if (w == "midnight") return 23 * 60 + 59 to listOf(i)
        TIME_AMPM.matchEntire(w)?.let { m ->
            return to24(m.groupValues[1].toInt(), m.groupValues[2], m.groupValues[3])?.let { it to listOf(i) }
        }
        TIME_24.matchEntire(w)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h in 0..23 && min in 0..59) return h * 60 + min to listOf(i)
        }
        // "5 pm" written as two words
        if (free(i + 1)) {
            val ampm = t[i + 1].word
            HOUR_ONLY.matchEntire(w)?.let { m ->
                if (ampm == "am" || ampm == "pm") {
                    return to24(m.groupValues[1].toInt(), m.groupValues[2], ampm)?.let { it to listOf(i, i + 1) }
                }
            }
        }
        return null
    }

    private fun to24(hour: Int, min: String, ampm: String): Int? {
        if (hour !in 1..12) return null
        val m = if (min.isEmpty()) 0 else min.toInt()
        if (m !in 0..59) return null
        val h = when {
            ampm == "am" && hour == 12 -> 0
            ampm == "pm" && hour != 12 -> hour + 12
            else -> hour
        }
        return h * 60 + m
    }

    private fun number(word: String): Int? = word.toIntOrNull()?.takeIf { it in 1..999 } ?: WORD_NUMBERS[word]

    private fun dayNumber(word: String): Int? =
        Regex("^(\\d{1,2})(st|nd|rd|th)?$").matchEntire(word)?.groupValues?.get(1)?.toInt()?.takeIf { it in 1..31 }

    private fun weekday(word: String): DayOfWeek? = WEEKDAYS[word]

    private fun month(word: String): Month? = MONTHS[word]

    companion object {
        private val TAG = Regex("^#[\\p{L}\\p{N}_-]+$")
        private val TIME_AMPM = Regex("^(\\d{1,2})(?::(\\d{2}))?(am|pm)$")
        private val TIME_24 = Regex("^(\\d{1,2}):(\\d{2})$")
        private val HOUR_ONLY = Regex("^(\\d{1,2})(?::(\\d{2}))?$")

        private val WORD_NUMBERS = mapOf(
            "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        )

        private val WEEKDAYS: Map<String, DayOfWeek> = buildMap {
            DayOfWeek.entries.forEach { d ->
                val name = d.name.lowercase()
                put(name, d)
                put(name.take(3), d)
            }
            put("tues", DayOfWeek.TUESDAY)
            put("thur", DayOfWeek.THURSDAY)
            put("thurs", DayOfWeek.THURSDAY)
        }

        private val MONTHS: Map<String, Month> = buildMap {
            Month.entries.forEach { m ->
                val name = m.name.lowercase()
                put(name, m)
                put(name.take(3), m)
            }
            put("sept", Month.SEPTEMBER)
        }
    }
}
