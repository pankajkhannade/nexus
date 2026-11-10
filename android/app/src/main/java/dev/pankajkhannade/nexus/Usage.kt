package dev.pankajkhannade.nexus

import java.time.LocalDate

/** Things Nexus counts, on the phone only. Nothing is sent anywhere unless the family taps Export. */
enum class Event(val key: String) {
    OPEN("opens"),
    CALL("calls"),
    WHATSAPP("whatsapp"),
    VOICE("voice_commands"),
    VOICE_FAIL("voice_not_understood"),
    SAFETY_CHECK("safety_checks"),
    ASK_FAMILY("ask_family"),
    RIDE("rides"),
    /** A voice-dictated message handed to WhatsApp or SMS (the parent still taps Send there). */
    MESSAGE("messages"),
    /** S15 Near home tile opens, one count per need (no app names, no places). */
    NEAR_GROCERY("near_grocery"),
    NEAR_MEDICINE("near_medicine"),
    NEAR_DOCTOR("near_doctor"),
    NEAR_RIDE("near_ride"),
    NEAR_HOME_SERVICE("near_home_service"),
    NEAR_EMERGENCY("near_emergency");

    companion object {
        fun from(key: String): Event? = entries.firstOrNull { it.key == key }
    }
}

/** Daily counters: epoch day -> event -> count. Serialised as lines "day key count". */
data class Usage(val days: Map<Long, Map<Event, Int>> = emptyMap()) {
    fun record(event: Event, day: Long): Usage {
        val today = days[day].orEmpty()
        return Usage(days + (day to today + (event to (today[event] ?: 0) + 1)))
    }

    fun count(event: Event, day: Long): Int = days[day]?.get(event) ?: 0

    /** Count of [event] over all recorded days. */
    fun total(event: Event): Int = days.values.sumOf { it[event] ?: 0 }

    /** Days with at least one open, call, voice command or safety check. */
    fun activeDays(): Int = days.count { (_, counts) -> counts.values.sum() > 0 }

    fun serialize(): String = days.toSortedMap().flatMap { (day, counts) ->
        counts.entries.sortedBy { it.key.ordinal }.map { (event, n) -> "$day ${event.key} $n" }
    }.joinToString("\n")

    companion object {
        fun parse(text: String): Usage {
            val days = mutableMapOf<Long, MutableMap<Event, Int>>()
            text.lineSequence().forEach { line ->
                val parts = line.trim().split(" ")
                if (parts.size != 3) return@forEach
                val day = parts[0].toLongOrNull() ?: return@forEach
                val event = Event.from(parts[1]) ?: return@forEach
                val n = parts[2].toIntOrNull() ?: return@forEach
                days.getOrPut(day) { mutableMapOf() }[event] = n
            }
            return Usage(days)
        }
    }
}

/** Answer to "Did Nexus help this week?". */
enum class Helped(val key: String) { YES("yes"), LITTLE("a_little"), NO("no") }

data class FeedbackAnswer(val helped: Helped? = null, val easier: String = "")

/**
 * The day-3 and day-7 feedback rounds. Q1 ("Did Nexus help?") then optional Q2 ("What would make it easier?").
 * A round closes when Q2 is saved or skipped ([finish]), or after two skips of Q1. Answering Q1 alone keeps
 * the round open so Q2 shows next.
 */
data class Feedback(
    val answers: Map<Int, FeedbackAnswer> = emptyMap(),
    val skips: Map<Int, Int> = emptyMap(),
    val closed: Set<Int> = emptySet(),
) {
    fun isClosed(round: Int): Boolean = round in closed || (skips[round] ?: 0) >= 2

    fun answer(round: Int, helped: Helped): Feedback =
        copy(answers = answers + (round to (answers[round] ?: FeedbackAnswer()).copy(helped = helped)))

    /** Saves the Q2 text and closes the round. */
    fun easier(round: Int, text: String): Feedback =
        copy(
            answers = answers + (round to (answers[round] ?: FeedbackAnswer()).copy(easier = text.trim().replace(Regex("""\s*\n\s*"""), " "))),
            closed = closed + round,
        )

    /** Q2 skipped: the round is done. */
    fun finish(round: Int): Feedback = copy(closed = closed + round)

    fun skip(round: Int): Feedback = copy(skips = skips + (round to (skips[round] ?: 0) + 1))

    /**
     * Which round to show today: 7 from day 7 on, otherwise 3 from day 3 on; null if none is due.
     * Day 0 is the setup day. A missed day-3 round is not shown once day 7 arrives.
     */
    fun due(installDay: Long, today: Long): Int? {
        if (installDay <= 0) return null
        val age = today - installDay
        return when {
            age >= 7 -> 7.takeUnless { isClosed(7) }
            age >= 3 -> 3.takeUnless { isClosed(3) }
            else -> null
        }
    }

    fun serialize(): String = buildList {
        answers.toSortedMap().forEach { (round, a) ->
            a.helped?.let { add("$round helped ${it.key}") }
            if (a.easier.isNotBlank()) add("$round easier ${a.easier.replace("\n", " ")}")
        }
        skips.toSortedMap().forEach { (round, n) -> add("$round skips $n") }
        closed.sorted().forEach { add("$it closed yes") }
    }.joinToString("\n")

    companion object {
        fun parse(text: String): Feedback {
            var f = Feedback()
            text.lineSequence().forEach { line ->
                val parts = line.trim().split(" ", limit = 3)
                if (parts.size != 3) return@forEach
                val round = parts[0].toIntOrNull() ?: return@forEach
                f = when (parts[1]) {
                    "helped" -> Helped.entries.firstOrNull { it.key == parts[2] }?.let { f.answer(round, it) } ?: f
                    "easier" -> f.copy(answers = f.answers + (round to (f.answers[round] ?: FeedbackAnswer()).copy(easier = parts[2])))
                    "skips" -> f.copy(skips = f.skips + (round to (parts[2].toIntOrNull() ?: 0)))
                    "closed" -> f.finish(round)
                    else -> f
                }
            }
            return f
        }
    }
}

/**
 * Plain-text usage summary the family can read before sharing. Counts only: no names, numbers or messages.
 */
fun exportText(usage: Usage, feedback: Feedback, installDay: Long, today: Long, language: String): String = buildString {
    appendLine("Nexus usage (stays on the phone unless you share it)")
    appendLine("Language: ${language.ifBlank { "en" }}")
    if (installDay > 0) appendLine("Set up: ${LocalDate.ofEpochDay(installDay)} (day ${today - installDay})")
    appendLine("Active days: ${usage.activeDays()}")
    appendLine()
    usage.days.toSortedMap().forEach { (day, counts) ->
        val parts = Event.entries.mapNotNull { e -> counts[e]?.takeIf { it > 0 }?.let { "${e.key}=$it" } }
        if (parts.isNotEmpty()) appendLine("${LocalDate.ofEpochDay(day)}: ${parts.joinToString(", ")}")
    }
    listOf(3, 7).forEach { round ->
        val a = feedback.answers[round]
        val skips = feedback.skips[round] ?: 0
        if (a != null || skips > 0) {
            appendLine()
            append("Day $round feedback: helped=${a?.helped?.key ?: "-"}")
            if (!a?.easier.isNullOrBlank()) append("; easier: \"${a!!.easier}\"")
            if (skips > 0) append("; skipped $skips")
            appendLine()
        }
    }
}.trimEnd()
