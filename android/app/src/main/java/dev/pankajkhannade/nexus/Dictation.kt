package dev.pankajkhannade.nexus

/**
 * Voice-dictated messages: "पंकचला वाटसब कर आणि सांग की तू कधी येणार आहेस घरी" ->
 * to Pankaj, on WhatsApp, "तू घरी कधी येणार आहेस?".
 *
 * Everything here is rule-based and runs on the phone. Nothing is sent: the confirm screen opens
 * WhatsApp or the SMS app with the text filled in and the parent taps Send there.
 */
enum class Channel { WHATSAPP, SMS, ANY }

data class Dictation(
    /** The saved person it is for, or null when nobody matched (the screen asks "who?"). */
    val person: Person?,
    /** The spoken name when it did not match anyone ("Suresh"), for the "who?" line. */
    val askedName: String?,
    val channel: Channel,
    /** The message after [polishMessage]. Empty when only "WhatsApp Pankaj" was said (asks "what should I say?"). */
    val text: String,
    /** The words as heard, before the rewrite. */
    val rawBody: String,
)

// ---- Tokens --------------------------------------------------------------------------------

private class Tok(val text: String, val start: Int, val end: Int) {
    val low: String = text.lowercase()
}

private val WORD = Regex("""[\p{L}\p{M}\p{N}'’]+""")

private fun tokens(text: String): List<Tok> = WORD.findAll(text).map { Tok(it.value, it.range.first, it.range.last + 1) }.toList()

// ---- Devanagari to a rough Latin form, only for matching names and channel words ----------

private val CONSONANTS = mapOf(
    'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n", 'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh",
    'ञ' to "n", 'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d",
    'ध' to "dh", 'न' to "n", 'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r",
    'ल' to "l", 'ळ' to "l", 'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
)
private val VOWELS = mapOf(
    'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai",
    'ओ' to "o", 'औ' to "au", 'ऍ' to "e", 'ॲ' to "a", 'ऑ' to "o",
)
private val MATRAS = mapOf(
    'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o",
    'ौ' to "au", 'ॅ' to "e", 'ॉ' to "o",
)

/** "पंकज" -> "pankaj", "वाटसब" -> "vaatasab". Word-final inherent a is dropped. Latin passes through lowercased. */
internal fun romanize(word: String): String {
    val out = StringBuilder()
    var pendingA = false
    for (c in word.lowercase()) {
        when {
            c in CONSONANTS -> {
                if (pendingA) out.append('a')
                out.append(CONSONANTS.getValue(c)); pendingA = true
            }
            c in MATRAS -> { out.append(MATRAS.getValue(c)); pendingA = false }
            c == '्' -> pendingA = false
            c == '़' -> Unit
            c == 'ं' || c == 'ँ' -> { if (pendingA) out.append('a'); out.append('n'); pendingA = false }
            c == 'ः' -> { if (pendingA) out.append('a'); out.append('h'); pendingA = false }
            c in VOWELS -> { if (pendingA) out.append('a'); out.append(VOWELS.getValue(c)); pendingA = false }
            else -> { if (pendingA) out.append('a'); out.append(c); pendingA = false }
        }
    }
    return out.toString()
}

/** Sound key so "Pankaj", "पंकज" and a misheard "पंकच" land close together. */
internal fun soundKey(word: String): String {
    var s = romanize(word).filter { it.isLetter() }
    s = s.replace("aa", "a").replace("ee", "i").replace("oo", "u").replace("w", "v").replace("ph", "f")
        .replace("z", "j").replace("q", "k").replace("ck", "k").replace("x", "ks")
    s = s.replace(Regex("""([bcdfgjklmnprstv])h"""), "$1")
    s = s.replace(Regex("""(.)\1+"""), "$1")
    if (s.length > 3 && s.endsWith("y")) s = s.dropLast(1) + "i"
    if (s.length > 3 && s.endsWith("a")) s = s.dropLast(1)
    return s
}

/** Consonants only, b=p, h dropped: "whatsapp", "वाटसब", "व्हॉट्सॲप" all become "vtsp". */
private fun skeleton(word: String): String =
    romanize(word).filter { it.isLetter() }.replace("w", "v").replace('b', 'p').replace('g', 'j').replace('z', 'j')
        .filter { it !in "aeiouyh" }.replace(Regex("""(.)\1+"""), "$1")

// ---- Channel and trigger words --------------------------------------------------------------

private val SMS_WORDS = setOf(
    "sms", "text", "txt", "message", "messages", "msg", "massage", "sandesh",
    "एसएमएस", "ऐसएमएस", "मेसेज", "मैसेज", "मॅसेज", "मेसेज़", "मैसेज़", "मसेज", "संदेश",
)

private fun channelOf(word: String): Channel? {
    val low = word.lowercase()
    if (low in SMS_WORDS) return Channel.SMS
    val sk = skeleton(low)
    if (sk.length in 4..7 && sk.startsWith("vtsp")) return Channel.WHATSAPP
    if (sk in setOf("sms", "msj", "msjs") && low.length <= 9) return Channel.SMS
    return null
}

/** "Tell" words that make it a message even without "WhatsApp": "पंकजला सांग की ...", "tell Pankaj that ...". */
private val TELL_WORDS = setOf(
    "tell", "सांग", "सांगा", "sang", "saang", "sanga", "saanga", "बोल", "बोलो", "बोला", "bol", "bolo", "bola",
    "कहो", "कह", "kaho", "keh", "kah", "kehna", "bata", "batao", "बता", "बताओ", "बताना",
)

private val CALL_WORDS = setOf("call", "phone", "fone", "fon", "ring", "dial", "कॉल", "फ़ोन", "फोन", "phon")

/** Separate case words and fillers between the name and the verb: "Pankaj ko", "Aai la", "मेरे बेटे को". */
private val CASE_WORDS = setOf(
    "ko", "la", "na", "ne", "को", "ला", "ना", "ने", "to", "please", "plz", "pls", "zara", "jara", "जरा",
    "mere", "meri", "mera", "maza", "majha", "mazya", "majhya", "maajhya", "mazi", "majhi", "मेरे", "मेरी", "माझ्या", "माझी",
)

private val CASE_SUFFIXES = listOf("ला", "ना", "ले", "को", "ने", "शी", "la", "ko", "na", "ne", "le")

private val NOT_A_NAME = setOf(
    "me", "him", "her", "them", "us", "you", "it", "i", "that", "and", "saying", "someone", "everyone", "nexus",
    "ok", "okay", "hey", "please", "ki", "की", "कि", "आणि", "and", "aur", "और", "tu", "तू", "tum", "तुम",
) + CASE_WORDS

/** Strips verbs, "and", "tell (him) that" and the like from the start of the body. Applied until nothing changes. */
private val LEAD = Regex(
    "(?iu)^(?:" + listOf(
        """kar\s+(?:do|de|dena)""", """कर\s+(?:दो|दे|देना)""", """kar(?:o|a|na|ke|un|do|de)?""", """कर(?:ा|ो|ून|के|ना|दो|दे)?""",
        """पाठव(?:ा|ून|शील)?""", """pa+thav(?:a|un)?""", """bhej\s+(?:do|de|dena)""", """भेज\s+(?:दो|दे|देना)""", """bhej(?:o|na)?""", """भेज(?:ो|ना)?""",
        """send(?:\s+(?:a|this))?(?:\s+message)?""", """var""", """वर""", """pe""", """par""", """पे""", """पर""",
        """a+ni""", """आणि""", """आणी""", """अणि""", """aur""", """और""", """and""",
        """sa+ng(?:a|un)?""", """सांग(?:ा|ून)?""", """bol(?:o|a|un)?""", """बोल(?:ो|ा|ून)?""", """(?:kah|keh)\s+(?:do|de|dena)""", """कह\s+(?:दो|दे|देना)""", """kaho""", """कहो""",
        """bata\s+(?:do|de|dena)""", """बता\s+(?:दो|दे|देना)""", """batao""", """बताओ""", """bata""", """बता""",
        """tell(?:\s+(?:him|her|them))?""", """say""", """saying""", """that""", """ki""", """kee""", """की""", """कि""", """के""",
        """please""", """plz""", """जरा""", """zara""",
    ).joinToString("|") + """)(?![\p{L}\p{M}\p{N}])[\s,:;.\-–]*""",
)

private fun stripLead(body: String): String {
    var s = body.trim().trimStart(',', ':', '-', '–', '.', ' ')
    while (true) {
        val next = LEAD.find(s)?.let { s.substring(it.range.last + 1) } ?: return s
        if (next == s) return s
        s = next
    }
}

// ---- Recipient --------------------------------------------------------------------------------

/** Spellings to try for one spoken word: as heard, and without a case ending ("पंकचला" -> "पंकच"). */
private fun nameForms(word: String): List<String> {
    val forms = mutableListOf(word)
    for (suffix in CASE_SUFFIXES) {
        if (word.length > suffix.length + 1 && word.endsWith(suffix, ignoreCase = true)) forms += word.dropLast(suffix.length)
    }
    return forms
}

/** 3 = name or alias as saved, 2 = same sound or family word, 1 = one slip of the recogniser. 0 = no. */
private fun matchScore(person: Person, spoken: String): Int {
    val wanted = spoken.trim().lowercase()
    val names = listOf(person.name, person.alias).filter { it.isNotBlank() }
    if (names.any { it.trim().lowercase() == wanted || it.trim().lowercase().substringBefore(' ') == wanted }) return 3
    if (names.any { nameMatches(it, spoken) }) return 2
    val key = soundKey(spoken)
    if (key.length < 3) return 0
    var best = 0
    for (n in names) {
        val parts = n.trim().split(Regex("""\s+"""))
        val keys = listOf(soundKey(parts.joinToString("")), soundKey(parts.first()))
        if (keys.any { it == key }) best = maxOf(best, 2)
        else if (key.length >= 4 && keys.any { editDistance(it, key) <= (if (key.length >= 7) 2 else 1) }) best = maxOf(best, 1)
    }
    return best
}

private fun bestPerson(spoken: String, people: List<Person>): Pair<Person, Int>? {
    var best: Pair<Person, Int>? = null
    for (form in nameForms(spoken)) {
        for (p in people) {
            val s = matchScore(p, form)
            if (s > 0 && (best == null || s > best.second)) best = p to s
        }
        val relation = RELATION_WORDS[form.lowercase()]
        val only = relation?.let { r -> people.filter { it.relation == r }.singleOrNull() }
        if (only != null && (best == null || best.second < 2)) best = only to 2
    }
    return best
}

private class Recipient(val person: Person?, val asked: String?, val firstTok: Int, val lastTok: Int)

private fun isVerbOrFiller(word: String): Boolean = LEAD.matchEntire(word + " ") != null

/**
 * Looks for the name just before [at] (Hindi/Marathi order), skipping "ko", "la", "मेरे" and fillers.
 * An unsaved name counts only with a case word or ending ("Suresh la", "सुरेशला"), so "Send WhatsApp" is not a name.
 */
private fun recipientBefore(toks: List<Tok>, at: Int, people: List<Person>): Recipient? {
    var i = at - 1
    var marked = false
    while (i >= 0 && toks[i].low in CASE_WORDS) { marked = marked || toks[i].low in setOf("ko", "la", "na", "को", "ला", "ना"); i-- }
    if (i < 0) return null
    val one = toks[i]
    if (one.low !in NOT_A_NAME && channelOf(one.text) == null && one.low !in CALL_WORDS) {
        val single = bestPerson(one.text, people)
        if (i >= 1 && toks[i - 1].low !in NOT_A_NAME && channelOf(toks[i - 1].text) == null) {
            val pair = bestPerson(toks[i - 1].text + " " + one.text, people)
            if (pair != null && (single == null || pair.second > single.second)) return Recipient(pair.first, null, i - 1, i)
        }
        if (single != null) return Recipient(single.first, null, i, i)
        val forms = nameForms(one.text)
        val asked = forms.last()
        if ((marked || forms.size > 1) && asked.length >= 2 && asked.none(Char::isDigit) && !isVerbOrFiller(asked)) {
            return Recipient(null, asked, i, i)
        }
    }
    // "Send Pankaj a WhatsApp": a saved name a little further back.
    for (j in (i - 1 downTo maxOf(0, i - 2))) {
        val hit = bestPerson(toks[j].text, people)?.takeIf { it.second >= 2 } ?: continue
        return Recipient(hit.first, null, j, j)
    }
    return null
}

/** Looks for the name just after [at] (English order: "text Pankaj that ..."). */
private fun recipientAfter(toks: List<Tok>, at: Int, people: List<Person>): Recipient? {
    var i = at + 1
    while (i < toks.size && toks[i].low in setOf("to", "my", "ko", "la")) i++
    if (i >= toks.size) return null
    val one = toks[i]
    if (one.low in NOT_A_NAME || isVerbOrFiller(one.text)) return null
    if (i + 1 < toks.size) {
        val pair = bestPerson(one.text + " " + toks[i + 1].text, people)
        val single = bestPerson(one.text, people)
        if (pair != null && (single == null || pair.second > single.second)) return Recipient(pair.first, null, i, i + 1)
    }
    bestPerson(one.text, people)?.let { return Recipient(it.first, null, i, i) }
    // An unknown word after "text/whatsapp" is taken as a name only when it looks like one (capitalised Latin or Devanagari).
    val looksLikeName = one.text.first().isUpperCase() || one.text.first() in '\u0900'..'\u097F'
    return if (looksLikeName) Recipient(null, one.text, i, i) else null
}

// ---- Reading the command ----------------------------------------------------------------------

/**
 * Reads a dictated-message command, or returns null when [said] is not one (for example a call:
 * "Pankaj ko phone lagao"). Hindi, Marathi, English and Hinglish, Devanagari or Latin.
 */
fun readDictation(said: String, saved: Saved): Dictation? {
    val text = said.trim()
    if (text.isEmpty()) return null
    val toks = tokens(text)
    if (toks.isEmpty()) return null

    var channel: Channel? = null
    var trigger = -1
    var triggerEnd = -1
    for ((i, t) in toks.withIndex()) {
        val single = channelOf(t.text)
        // "whats app", "व्हॉट्स ॲप": the recogniser sometimes splits the word.
        val joined = if (single == null && i + 1 < toks.size) channelOf(t.text + toks[i + 1].text).takeIf { it == Channel.WHATSAPP } else null
        if (single != null || joined != null) {
            channel = single ?: joined
            trigger = i
            triggerEnd = if (single != null) i else i + 1
            break
        }
        if (t.low in TELL_WORDS) { trigger = i; triggerEnd = i; break }
    }
    if (trigger < 0) return null
    val weak = channel == null
    // "Pankaj ko phone karke bolo ki ..." is still a call: a call word before a plain "tell" wins.
    if (weak && toks.take(trigger).any { it.low in CALL_WORDS }) return null

    val people = saved.people
    val before = recipientBefore(toks, trigger, people)
    val recipient = before ?: recipientAfter(toks, triggerEnd, people)
    if (weak && recipient?.person == null) return null
    if (weak && before == null && toks[trigger].low != "tell") return null

    val bodyFrom = when {
        recipient != null && before == null -> toks[recipient.lastTok].end
        else -> toks[triggerEnd].end
    }
    val raw = stripLead(text.substring(bodyFrom)).trim()
    return Dictation(
        person = recipient?.person,
        askedName = recipient?.asked,
        channel = channel ?: Channel.ANY,
        text = polishMessage(raw),
        rawBody = raw,
    )
}

// ---- Rewriting the body -----------------------------------------------------------------------

private val QUESTION_WORDS = setOf(
    // Marathi
    "कधी", "केव्हा", "काय", "कुठे", "कुठं", "कसा", "कशी", "कसं", "कसे", "किती", "कोण", "कोणी", "कोणाला", "कुणी",
    "kadhi", "kevha", "kay", "kaay", "kuthe", "kasa", "kashi", "kasa", "kiti", "kon", "koni",
    // Hindi
    "कब", "क्या", "कहाँ", "कहां", "कैसे", "कैसा", "कैसी", "कितना", "कितनी", "कितने", "कौन", "क्यों", "क्यूँ",
    "kab", "kya", "kahan", "kaha", "kaise", "kaisa", "kaisi", "kitna", "kitni", "kitne", "kaun", "kyun", "kyon",
    // English
    "when", "what", "where", "why", "how", "who", "which",
)
private val EN_AUX_START = setOf("are", "is", "will", "can", "could", "would", "do", "did", "have", "has", "should", "shall", "were", "was")
/** Marathi "का" asks a yes/no question only at the very end ("जेवलास का"). */
private val FINAL_QUESTION = setOf("का", "ka")

/** Short place and time words a speaker often adds at the end: "तू कधी येणार आहेस घरी". */
private val TRAILING_PLACE = setOf(
    "घरी", "घर", "ghari", "ghar", "इकडे", "तिकडे", "इथे", "तिथे", "आज", "उद्या", "परत", "लवकर", "ऑफिसला",
    "यहाँ", "यहां", "वहाँ", "वहां", "कल", "वापस", "जल्दी", "aaj", "kal", "udya", "lavkar", "jaldi", "wapas", "vapas",
)
private val JOINERS = setOf("आणि", "आणी", "अणि", "आनी", "ani", "aani", "और", "aur", "and")
/** Only unambiguous misspellings of small joining words. Content words are never changed. */
private val SPELLING = mapOf("आणी" to "आणि", "अणि" to "आणि", "आनी" to "आणि")

private fun isDevanagari(s: String) = s.any { it in '\u0900'..'\u097F' }

private fun isQuestion(words: List<String>): Boolean {
    val low = words.map { it.lowercase() }
    if (low.any { it in QUESTION_WORDS }) return true
    if (low.lastOrNull() in FINAL_QUESTION) return true
    return !isDevanagari(words.joinToString("")) && low.firstOrNull() in EN_AUX_START && low.size >= 3
}

/**
 * Turns the dictated words into a message, keeping the parent's language and person (तू / तुम / you):
 * drops a leading "की / कि / that", splits a question from what follows at "आणि / और / and",
 * moves a trailing place or time word in front of the question word, ends questions with "?",
 * other sentences with ".", and capitalises English. When a rule does not clearly apply the words stay as said.
 */
fun polishMessage(raw: String): String {
    var s = stripLead(raw.trim().trim('"', '“', '”', '\'')).replace(Regex("""\s+"""), " ").trim()
    if (s.isEmpty()) return ""
    val pieces = Regex("""[^.!?।]+[.!?।]*""").findAll(s).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()
    val sentences = mutableListOf<Pair<List<String>, String>>()
    for (piece in pieces) {
        val end = piece.takeLastWhile { it in ".!?।" }
        var words = piece.dropLast(end.length).split(" ").filter { it.isNotBlank() }.map { SPELLING[it] ?: it }
        // "तू कधी येणार आहेस घरी आणि येताना साबण घेऊन ये": the question ends at "आणि".
        while (true) {
            val j = words.indices.firstOrNull { it > 1 && words[it].lowercase() in JOINERS && isQuestion(words.subList(0, it)) } ?: break
            sentences += words.subList(0, j) to ""
            words = words.drop(j)
        }
        if (words.isNotEmpty()) sentences += words to end
    }
    return sentences.joinToString(" ") { (words, end) -> finishSentence(words, end) }.trim()
}

private fun finishSentence(input: List<String>, end: String): String {
    var words = input
    val question = isQuestion(words) || end.contains('?')
    if (question && words.size >= 4) {
        val q = words.indexOfFirst { it.lowercase() in QUESTION_WORDS }
        val last = words.last()
        if (q in 0 until words.size - 2 && last.lowercase() in TRAILING_PLACE) {
            words = words.subList(0, q) + last + words.subList(q, words.size - 1)
        }
    }
    var sentence = words.joinToString(" ")
    if (!isDevanagari(sentence)) {
        sentence = sentence.split(" ").joinToString(" ") { w ->
            when (w) { "i" -> "I"; "i'm" -> "I'm"; "i'll" -> "I'll"; "i've" -> "I've"; "i'd" -> "I'd"; else -> w }
        }
        sentence = sentence.replaceFirstChar { it.uppercase() }
    }
    val mark = when {
        question -> "?"
        end.contains('!') -> "!"
        end.contains('।') -> "।"
        else -> "."
    }
    return sentence + mark
}
