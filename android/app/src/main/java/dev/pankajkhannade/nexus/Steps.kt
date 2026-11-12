package dev.pankajkhannade.nexus

/**
 * Multi-step voice commands (2026-11-12). One utterance such as "दीनानाथ हॉस्पिटलला जायचं आहे आणि पंकजला सांग मी
 * निघाले" becomes an ordered list of the intents Nexus already has, run one at a time, each with its own
 * confirmation. Deterministic on purpose: the LFM3 test (orchestration-lfm3-2026-11-12.md) found an on-device
 * model too slow, too often wrong and unsafe on its own, while clause splitting + the rule parser covered most
 * requests in under a millisecond.
 */

/**
 * The words that join or condition clauses, per language (docs/i18n-architecture.md: they belong to each
 * language's grammar data; Kotlin for now, `grammar.json` later). All languages are used together, because
 * parents mix them ("Rapido se Swargate jana hai aur Meena ko call karo").
 */
data class ClauseGrammar(
    val code: String,
    /** "and", "then": a new step follows. Longest first is not needed; matching is on whole words. */
    val sequence: List<String>,
    /** Regex alternatives for "if (he) doesn't pick up": the next step runs only if the call didn't work. */
    val ifNotWorked: List<String>,
    /** Pronoun -> how to put the earlier step's person in its place ("%s" is the name). */
    val pronouns: Map<String, String>,
    /** Ways to put a carried-over person in front of a clause that names no one ("सांग मी पाच वाजता येते"). */
    val carryPrefixes: List<String>,
    /** Words that ask for a location share. Nexus never shares location by itself (guard rule). */
    val locationWords: List<String>,
)

val CLAUSE_GRAMMARS: List<ClauseGrammar> = listOf(
    ClauseGrammar(
        code = "mr",
        sequence = listOf("आणि", "आणी", "ani", "aani", "नंतर", "मग", "nantar", "mag", "आणि मग", "आणि नंतर", "त्यानंतर"),
        ifNotWorked = listOf(
            """(?:(?:जर|jar)\s+)?(?:(?:तो|ती|ते|to|ti)\s+)?(?:(?:फोन|phone)\s+)?(?:नाही\s+(?:उचलला|उचलली|उचलले|लागला|लागली)|(?:उचलला|उचलली|लागला|लागली)\s+नाही|nahi\s+(?:uchalla|lagla))\s+(?:तर|tar)""",
        ),
        pronouns = mapOf("त्याला" to "%sला", "तिला" to "%sला", "त्यांना" to "%sला", "tyala" to "%sla", "tila" to "%sla"),
        carryPrefixes = listOf("%sला "),
        locationWords = listOf("लोकेशन", "लोकेशन", "ठिकाण पाठव"),
    ),
    ClauseGrammar(
        code = "hi",
        sequence = listOf("और", "फिर", "aur", "phir", "fir", "और फिर", "उसके बाद", "uske baad"),
        ifNotWorked = listOf(
            """(?:(?:अगर|agar)\s+)?(?:(?:वो|वह|wo|woh)\s+)?(?:(?:फोन|phone)\s+)?(?:न|नहीं|na|nahi|nahin)\s+(?:उठाएं|उठाए|उठाया|उठे|लगे|लगा|uthaye|uthaya|uthe|lage|laga)\s+(?:तो|to)""",
        ),
        pronouns = mapOf("उसे" to "%s को", "उसको" to "%s को", "usko" to "%s ko", "use" to "%s ko", "unko" to "%s ko", "उन्हें" to "%s को"),
        carryPrefixes = listOf("%s को "),
        locationWords = listOf("लोकेशन", "location"),
    ),
    ClauseGrammar(
        code = "en",
        sequence = listOf("and", "then", "and then", "after that", "also", "and also"),
        ifNotWorked = listOf(
            """if\s+(?:he|she|they|it|nobody|no\s+one)?\s*(?:does\s*n[o']?t|do\s*n[o']?t|did\s*n[o']?t|does\s+not|do\s+not|did\s+not)\s+(?:pick\s+up|answer)(?:\s+the\s+phone)?""",
            """if\s+(?:there\s+is\s+)?no\s+answer""",
        ),
        pronouns = mapOf("him" to "%s", "her" to "%s", "them" to "%s"),
        carryPrefixes = listOf("tell %s "),
        locationWords = listOf("location"),
    ),
)

/** One step of a plan: the clause to parse (with any carried-over name filled in) and whether it is conditional. */
data class PlannedStep(val text: String, val onlyIfNotWorked: Boolean = false)

/** The result of splitting: two or more steps make a plan; [locationDropped] = a location share was asked for. */
data class SplitResult(val steps: List<PlannedStep>, val locationDropped: Boolean = false) {
    val isPlan: Boolean get() = steps.size >= 2
}

/**
 * A plan waiting on the parent, saved with the rest of [Saved] so it survives Nexus being killed while the parent
 * is in Uber or WhatsApp. [next] is the step to offer next. Plans older than [PLAN_TTL_MS] are dropped.
 */
data class PlanQueue(val steps: List<PlannedStep>, val next: Int, val touchedAt: Long) {
    val pending: PlannedStep? get() = steps.getOrNull(next)
    fun expired(now: Long): Boolean = now - touchedAt > PLAN_TTL_MS || now < touchedAt - PLAN_TTL_MS
}

/** About half an hour: long enough for a ride or a call, short enough that tomorrow's home isn't nagging about today. */
const val PLAN_TTL_MS = 30 * 60_000L
const val MAX_PLAN_STEPS = 5

private val WORD_EDGE_L = """(?<![\p{L}\p{M}\p{N}])"""
private val WORD_EDGE_R = """(?![\p{L}\p{M}\p{N}])"""

private fun alternation(words: Collection<String>) = words.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

private val SEQUENCE = Regex(
    """(?iu)\s*(?:[,;]\s*)?$WORD_EDGE_L(?:${alternation(CLAUSE_GRAMMARS.flatMap { it.sequence })})$WORD_EDGE_R\s*(?:,\s*)?""",
)
private val IF_NOT = Regex(
    """(?iu)\s*(?:[,;]\s*)?(?:$WORD_EDGE_L(?:${alternation(CLAUSE_GRAMMARS.flatMap { it.sequence })})$WORD_EDGE_R\s*)?""" +
        """$WORD_EDGE_L(?:${CLAUSE_GRAMMARS.flatMap { it.ifNotWorked }.joinToString("|")})$WORD_EDGE_R\s*,?\s*""",
)
/** A sentence end or comma between clauses ("Ring my son. Also wake me…", "रस्ता दाखव, मग…"). */
private val PUNCT = Regex("""\s*[.!?।,;]\s+""")
private val LOCATION = Regex("""(?iu)$WORD_EDGE_L(?:${alternation(CLAUSE_GRAMMARS.flatMap { it.locationWords })})$WORD_EDGE_R""")

private class Cut(val start: Int, val end: Int, val conditional: Boolean)

/** Every place the utterance could be cut, in order, conditionals winning over a plain "and" at the same spot. */
private fun cuts(text: String): List<Cut> {
    val found = mutableListOf<Cut>()
    IF_NOT.findAll(text).forEach { found += Cut(it.range.first, it.range.last + 1, true) }
    SEQUENCE.findAll(text).forEach { m -> if (found.none { m.range.first < it.end && it.start < m.range.last + 1 }) found += Cut(m.range.first, m.range.last + 1, false) }
    PUNCT.findAll(text).forEach { m -> if (found.none { m.range.first < it.end && it.start < m.range.last + 1 }) found += Cut(m.range.first, m.range.last + 1, false) }
    return found.sortedBy { it.start }
}

private fun personOf(command: Command): Person? = when (command) {
    is Command.Call -> command.person
    is Command.WhatsApp -> command.person
    is Command.Message -> command.dictation.person
    else -> null
}

/** A clause strong enough to end a dictated message: it names who to call or message, or a ride. */
private fun strongAfterMessage(command: Command): Boolean = when (command) {
    is Command.Call, is Command.WhatsApp, Command.RideHome, is Command.RideTo -> true
    is Command.Message -> command.dictation.person != null
    is Command.FindContact -> command.ask == Ask.CALL
    else -> false
}

/** The name to put back into a clause: as the parent would say it, in the clause's script, one word if possible. */
private fun spokenName(person: Person, clause: String): String {
    val devanagari = Script.DEVANAGARI.isIn(clause)
    return when {
        devanagari && person.localName.isNotBlank() -> person.localName
        person.alias.isNotBlank() -> person.alias
        else -> person.name
    }.trim()
}

/** Puts the earlier step's person where a pronoun is ("message him" -> "message Pankaj"). */
private fun withPronouns(clause: String, person: Person?): String {
    if (person == null) return clause
    var out = clause
    for ((pronoun, template) in CLAUSE_GRAMMARS.flatMap { it.pronouns.entries }) {
        out = out.replace(Regex("""(?iu)$WORD_EDGE_L${Regex.escape(pronoun)}$WORD_EDGE_R"""), Regex.escapeReplacement(template.format(spokenName(person, clause))))
    }
    return out
}

/**
 * The clause as it should be parsed after [previous]: pronouns replaced, and a clause that names nobody
 * ("सांग मी पाच वाजता येते" after "डॉक्टरांना फोन लाव") given the earlier step's person.
 */
private fun resolveClause(clause: String, previous: Person?, carry: Boolean, parse: (String) -> Command): Pair<String, Command> {
    val replaced = withPronouns(clause, previous)
    val direct = parse(replaced)
    val needsPerson = carry && previous != null && (direct == Command.Unknown || (direct is Command.Message && direct.dictation.person == null && direct.dictation.askedName == null))
    if (!needsPerson) return replaced to direct
    for (prefix in CLAUSE_GRAMMARS.flatMap { it.carryPrefixes }) {
        val text = prefix.format(spokenName(previous!!, replaced)) + replaced
        val command = parse(text)
        if (personOf(command)?.id == previous.id) return text to command
    }
    return replaced to direct
}

/**
 * Splits [said] into steps for the existing intents. Fewer than two valid steps means "not a plan": the caller
 * handles the utterance exactly as before. A cut is kept only when the words after it are a command by
 * themselves, and after a dictated message only when they clearly start a new action (a name to call or message,
 * or a ride), so "पंकजला सांग की दूध आणि साबण आण" stays one message.
 */
fun splitCommand(said: String, saved: Saved, parser: IntentParser = RuleIntentParser): SplitResult {
    val text = said.trim()
    if (text.isEmpty()) return SplitResult(emptyList())
    val parse = { s: String -> parser.parse(s, saved) }

    // Clauses with their place in the text, so words that turn out not to be a new step can be glued back as said.
    class Piece(val start: Int, val end: Int, val conditional: Boolean) {
        val words: String get() = text.substring(start, end).trim().trim(',', '.', ';', '।').trim()
    }
    val pieces = mutableListOf<Piece>()
    var from = 0
    var conditional = false
    for (cut in cuts(text)) {
        if (cut.start < from) continue
        pieces += Piece(from, cut.start, conditional)
        conditional = cut.conditional
        from = cut.end
    }
    pieces += Piece(from, text.length, conditional)
    val clauses = pieces.filter { it.words.isNotEmpty() }
    if (clauses.size < 2) return SplitResult(emptyList())

    var locationDropped = false
    val steps = mutableListOf<Pair<PlannedStep, Command>>()
    var current: Piece? = null // the clause being grown, not yet a step

    for (piece in clauses) {
        if (LOCATION.containsMatchIn(piece.words)) {
            // Never turned into a share: Nexus has no location permission and adds no location (guard rule).
            locationDropped = true
            continue
        }
        val cur = current
        if (cur == null) { current = piece; continue }
        val previous = steps.lastOrNull()?.second?.let(::personOf)
        val (curText, curCommand) = resolveClause(cur.words, previous, cur.conditional, parse)
        val (_, candCommand) = resolveClause(piece.words, personOf(curCommand) ?: previous, piece.conditional, parse)
        val accept = candCommand != Command.Unknown && curCommand != Command.Unknown &&
            (curCommand !is Command.Message || piece.conditional || strongAfterMessage(candCommand))
        current = if (accept) {
            steps += PlannedStep(curText, cur.conditional) to curCommand
            piece
        } else {
            // Not a new step: the words belong to the clause before ("…दूध आणि साबण आण").
            Piece(cur.start, piece.end, cur.conditional)
        }
    }
    current?.let { last ->
        val previous = steps.lastOrNull()?.second?.let(::personOf)
        val (lastText, lastCommand) = resolveClause(last.words, previous, last.conditional, parse)
        if (lastCommand != Command.Unknown) steps += PlannedStep(lastText, last.conditional) to lastCommand
    }
    // A conditional can't start a plan.
    val planned = steps.map { it.first }.mapIndexed { i, s -> if (i == 0) s.copy(onlyIfNotWorked = false) else s }
    return SplitResult(planned.take(MAX_PLAN_STEPS), locationDropped)
}
