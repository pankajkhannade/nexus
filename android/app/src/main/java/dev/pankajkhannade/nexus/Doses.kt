package dev.pankajkhannade.nexus

import java.time.LocalDate
import java.time.ZonedDateTime

/** How many medicine times the child can set. More than this does not fit a parent's day. */
const val MAX_DOSES = 4

/**
 * One medicine the child set. [name] is what the parent says ("BP ची गोळी"). It stays on the phone
 * and is never written into the usage log. It leaves the phone only if someone taps Send to the child.
 */
data class Dose(val id: String, val name: String, val hour: Int, val minute: Int)

/** The parent's answer for one medicine on one day. [day] is yyyy-MM-dd. */
data class DoseMark(val doseId: String, val day: String, val taken: Boolean)

/** 2 to 40 characters, one line. Null when it should not be saved. */
fun cleanDoseName(raw: String): String? {
    val name = raw.trim().replace(Regex("""\s+"""), " ").replace("\n", "")
    return name.takeIf { it.length in 2..40 }
}

fun cleanDoseTime(hour: Int, minute: Int): Pair<Int, Int>? =
    if (hour in 0..23 && minute in 0..59) hour to minute else null

/** The next time this dose should speak. If that clock time has passed, it is tomorrow. */
fun nextDoseAt(hour: Int, minute: Int, now: ZonedDateTime): ZonedDateTime {
    val at = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    return if (at.isAfter(now)) at else at.plusDays(1)
}

fun doseClock(dose: Dose): String = "%02d:%02d".format(dose.hour, dose.minute)

/**
 * Medicines whose time has already come today and that have no answer yet.
 * A dose more than 12 hours ago is left off the home card so yesterday's pill does not sit there all day.
 */
fun dueDoses(doses: List<Dose>, marks: List<DoseMark>, now: ZonedDateTime): List<Dose> {
    val day = now.toLocalDate().toString()
    val minutesNow = now.hour * 60 + now.minute
    return doses.filter { dose ->
        val late = minutesNow - (dose.hour * 60 + dose.minute)
        val answered = marks.any { it.doseId == dose.id && it.day == day }
        late in 0..(12 * 60) && !answered
    }
}

/** One answer per medicine per day. Keeps 14 days, and drops medicines that were removed. */
fun recordMark(marks: List<DoseMark>, mark: DoseMark, doses: List<Dose>, today: LocalDate): List<DoseMark> {
    val rest = marks.filterNot { it.doseId == mark.doseId && it.day == mark.day }
    return pruneMarks(rest + mark, doses, today)
}

fun pruneMarks(marks: List<DoseMark>, doses: List<Dose>, today: LocalDate): List<DoseMark> {
    val ids = doses.map { it.id }.toSet()
    val cutoff = today.minusDays(13).toString()
    return marks.filter { it.doseId in ids && it.day >= cutoff }
}

/**
 * The line sent to the child. [state] supplies the already-translated word for each mark
 * (taken / not yet / no answer). Only [day] is included.
 */
fun doseShareLines(doses: List<Dose>, marks: List<DoseMark>, day: LocalDate, state: (DoseMark?) -> String): List<String> {
    val key = day.toString()
    return doses.map { dose ->
        val mark = marks.firstOrNull { it.doseId == dose.id && it.day == key }
        "${dose.name} ${doseClock(dose)}: ${state(mark)}"
    }
}

private val TAKE_WORDS = setOf(
    "ghya", "ghe", "ghyaychi", "ghyayche", "ghetli", "ghetla", "lena", "leni", "liya", "lo", "le", "li",
    "taken", "take", "took", "khaya", "khayi",
    "घ्या", "घेतली", "घेतला", "घ्यायची", "घ्यायचे", "ले", "ली", "लो", "लेनी", "लेना", "खा",
)

/** "goli ghyaychi" / "dawai le li": they mean today's dose, not the pharmacy. "औषध पाहिजे" does not. */
fun asksAboutTaking(said: String): Boolean {
    val words = said.lowercase().split(Regex("""[^\p{L}\p{M}]+""")).filter { it.isNotBlank() }
    return words.any { it in TAKE_WORDS }
}
