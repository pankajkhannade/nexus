package dev.pankajkhannade.nexus

/**
 * "Is this safe?" rule checks. Runs entirely on the phone. Nexus never says a message is safe:
 * the mildest verdict is NONE ("No warning signs found").
 */
enum class Level { DANGER, CAREFUL, NONE }

/** Why a message looks risky. [weight]: 3 = strong (alone means Danger), 2 = medium, 1 = weak (only counts with others). */
enum class Reason(val weight: Int) {
    ASKS_OTP_PIN(3),
    UPI_COLLECT(3),
    REMOTE_APP(3),
    APP_FILE(3),
    LOOKALIKE_LINK(3),
    POLICE_THREAT(3),
    KYC(2),
    ACCOUNT_BLOCKED(2),
    SHORT_LINK(2),
    UNKNOWN_LINK(2),
    PRIZE(2),
    JOB_TASK(2),
    REFUND(1),
    NEW_NUMBER_MONEY(2),
    URGENCY(1),
}

data class Verdict(val level: Level, val reasons: List<Reason>) {
    /** Up to 3 reasons, strongest first, for the screen. */
    val shown: List<Reason> get() = reasons.take(3)

    /** Show "Never share OTP or PIN" when the message is about codes, PINs or payments. */
    val showNeverShare: Boolean get() = reasons.any { it == Reason.ASKS_OTP_PIN || it == Reason.UPI_COLLECT || it == Reason.KYC }
}

private const val I = "(?iu)"

private fun rx(vararg alternatives: String) = Regex(I + "(?:" + alternatives.joinToString("|") + ")")

private val CODE = """(?<![\p{L}\p{M}])(?:otp|o\.t\.p|one[\s-]?time[\s-]?password|pin|m-?pin|upi\s*pin|atm\s*pin|cvv|password|ओटीपी|पिन|कोड|code)(?![\p{L}\p{M}])"""

/** Asking the reader to give out a code: "share the OTP", "OTP batao", "OTP सांगा", "send me the code you received". */
private val ASKS_CODE = rx(
    """(?:share|send|tell|give|forward|provide|enter|type|read\s*out|confirm)\s+(?:me\s+|us\s+|the\s+|your\s+|that\s+|this\s+|received\s+)*$CODE""",
    """$CODE\s*(?:jo\s+aaya|received|you\s+got|aaya\s+hai)?[\s\S]{0,25}?(?:batao|bata\s*do|bataiye|bhejo|bhej\s*do|bhejiye|share\s*karo|share\s*kar|sanga|sang|पाठवा|पाठव|सांगा|सांग|बताओ|बताइए|बता\s*दो|भेजो|भेज\s*दो|भेजिए|शेयर\s*करें|शेयर\s*करो)""",
    """$CODE[\s\S]{0,20}?(?:to\s+(?:verify|confirm|cancel|activate|unblock|complete))""",
)

/** "Do not share your OTP" style warnings from real banks are not requests. */
private val NEGATED_CODE = rx(
    """(?:do\s*not|don'?t|never|not\s+to)\s+(?:share|disclose|tell|give)""",
    """किसी\s+(?:के\s+)?(?:को|साथ)\s+(?:भी\s+)?(?:न|ना|नहीं)""",
    """कोणालाही\s+(?:सांगू|देऊ)\s+नका""",
    """kisi\s+ko\s+(?:bhi\s+)?(?:na|mat|nahi)""",
    """(?:sharing|share)\s+(?:of\s+)?(?:otp|pin)\s+is\s+(?:not|never)""",
)

private val UPI_COLLECT = rx(
    """(?:enter|put|type)\s+(?:your\s+)?(?:upi\s+)?(?:m-?)?pin\s+(?:to|for)\s+(?:receive|get|accept|claim)""",
    """(?:receive|get|claim|accept)\s+(?:the\s+)?(?:money|payment|amount|cashback|refund|rs\.?|₹|inr)[\s\S]{0,40}?(?:pin|scan|qr|approve|collect)""",
    """(?:scan|स्कैन)[\s\S]{0,20}?(?:qr|क्यूआर)[\s\S]{0,30}?(?:receive|get|pane|पाने|मिळ|प्राप्त)""",
    """collect\s+request""",
    """(?:paise|paisa|money|payment|amount)\s+(?:receive|pane|lene|milne)\s+(?:karne\s+)?ke\s+liye[\s\S]{0,25}?pin""",
    """पैसे\s+(?:पाने|लेने|मिलने)\s+के\s+लिए[\s\S]{0,20}?पिन""",
    """पैसे\s+मिळवण्यासाठी[\s\S]{0,20}?पिन""",
    """(?:approve|accept)\s+(?:the\s+)?(?:request|payment)\s+(?:to|for)\s+(?:receive|get)""",
)

private val REMOTE_APP = rx("""any\s*desk""", """team\s*viewer""", """quick\s*support""", """rust\s*desk""", """airdroid""", """screen\s*share\s+app""", """एनीडेस्क""")

private val POLICE = rx(
    """digital\s+arrest""", """arrest\s+warrant""", """money\s+laundering""", """\b(?:cbi|ncb|ed|customs|cyber\s*crime|police|trai)\s+(?:officer|department|case|notice|se|से)""",
    """parcel[\s\S]{0,40}?(?:drugs|illegal|seized|customs)""", """(?:fedex|dhl|courier)[\s\S]{0,40}?(?:seized|drugs|illegal|customs)""",
    """डिजिटल\s+अरेस्ट""", """गिरफ्तार""", """अटक\s+(?:वॉरंट|होईल)""", """मनी\s+लॉन्ड्रिंग""",
)

private val KYC = rx(
    """\bkyc\b""", """e-?kyc""", """केवाईसी""", """(?:pan|aadhaa?r|आधार|पैन)\s*(?:card\s*)?(?:update|link|verify|अपडेट|लिंक)""",
    """(?:update|link|verify)\s+(?:your\s+)?(?:pan|aadhaa?r)""",
)

private val BLOCKED = rx(
    """\b(?:account|a/c|acct|card|sim|number|wallet|connection|kyc)\b[\s\S]{0,30}?(?:will\s+be\s+|has\s+been\s+|is\s+|get\s+)?(?:blocked|suspended|closed|deactivated|frozen|disconnected|terminated|band|bandh)(?![a-z])""",
    """(?:electricity|power|light|bijli|bijlee)[\s\S]{0,30}?(?:disconnect|cut|kat|kaat)""",
    """(?:block|band|bandh)\s+(?:ho\s+)?(?:jayega|jaega|jaayega|jayegi|ho\s+jaayega)""",
    """(?:बंद|ब्लॉक)\s+(?:हो\s+)?(?:जाएगा|जायेगा|जाएगी|होगा|होईल|होणार|केले\s+जाईल|करण्यात\s+येईल)""",
    """(?:बिजली|बिज़ली|वीज)[\s\S]{0,30}?(?:कट|कटेगी|खंडित|बंद)""",
)

private val PRIZE = rx(
    """lottery""", """lucky\s+draw""", """you\s+(?:have\s+)?won""", """\bwinner\b""", """\bprize\b""", """\bkbc\b""", """jackpot""",
    """claim\s+(?:your\s+)?(?:reward|prize|gift|cashback)""", """free\s+(?:gift|iphone|recharge)""",
    """लॉटरी""", """इनाम""", """बक्षीस""", """जीत(?:ा|े|ी)\s+(?:है|हैं|हो)""", """जिंकल""", """jeet[ae]?\s+(?:hai|ho)""", """inaam""",
)

private val JOB = rx(
    """work\s+from\s+home""", """part[\s-]?time\s+(?:job|work)""", """earn\s+(?:rs\.?|₹|inr)?\s*\d""", """daily\s+(?:income|earning|payout)""",
    """(?:like|rate|review)\s+(?:youtube\s+)?(?:videos|hotels|products)""", """prepaid\s+task""", """telegram\s+(?:group|task)""",
    """ghar\s+baithe\s+(?:kamaye|kamao|kamai)""", """घर\s+बैठे""", """घरबसल्या""", """रोज\s+(?:₹|रु)""",
)

private val REFUND = rx(
    """refund""", """tax\s+return\s+(?:approved|pending)""", """excess\s+(?:amount|payment)\s+(?:credited|paid)""", """cashback\s+of""",
    """रिफंड""", """परतावा""", """galti\s+se\s+(?:paise|payment|bhej)""", """गलती\s+से""", """chukun""", """चुकून""",
)

private val NEW_NUMBER = rx("""(?:this\s+is\s+)?my\s+new\s+number""", """naya\s+number""", """mera\s+number\s+badal""", """नया\s+नंबर""", """नवीन\s+नंबर""", """navin\s+number""")
private val MONEY_ASK = rx(
    """(?:send|transfer|pay|need)\s+(?:me\s+)?(?:some\s+)?(?:money|rs\.?|₹|\d{3,})""", """paise\s+(?:bhejo|bhej\s*do|chahiye|transfer)""",
    """पैसे\s+(?:भेजो|भेज\s*दो|चाहिए|पाठव|पाठवा|हवे\s+आहेत|लागतील)""", """paise\s+pathav""", """gpay\s+kar""", """phonepe\s+kar""",
)

private val URGENCY = rx(
    """\burgent(?:ly)?\b""", """immediately""", """\bat\s+once\b""", """within\s+\d+\s*(?:hours|hrs|minutes|mins)""", """today\s+only""", """last\s+(?:chance|date|warning)""",
    """\basap\b""", """\btonight\b""", """turant""", """तुरंत""", """फौरन""", """ताबडतोब""", """लगेच""", """आज\s+रात""", """jaldi\s+karo""",
)

private val APK = Regex("""(?i)\.apk\b|\binstall\s+(?:this\s+)?(?:app|apk)\s+from\s+(?:this\s+)?link""")

private val URL = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"']+|\b(?:[a-z0-9-]+\.)+(?:com|in|net|org|co|xyz|top|click|live|icu|buzz|online|site|info|shop|cc|tk|ml|ga|cf|gq|link|me|ly|gl|gd|at|io|app|vip|bid|win|loan|work|sbi)(?:/[^\s<>"']*)?""")

private val SHORTENERS = setOf(
    "bit.ly", "tinyurl.com", "goo.gl", "t.ly", "cutt.ly", "is.gd", "rb.gy", "shorturl.at", "tiny.cc", "ow.ly",
    "s.id", "rebrand.ly", "t.co", "bitly.com", "short.gy", "v.gd", "shrtco.de", "u.to", "clck.ru",
)

/** Brands scammers imitate, and the domains they really use. */
private val BRANDS = mapOf(
    "sbi" to setOf("sbi.co.in", "onlinesbi.sbi", "sbi.bank.in", "onlinesbi.com", "sbicard.com"),
    "hdfc" to setOf("hdfcbank.com", "hdfcbank.bank.in", "hdfc.com"),
    "icici" to setOf("icicibank.com", "icici.bank.in"),
    "axis" to setOf("axisbank.com", "axis.bank.in"),
    "kotak" to setOf("kotak.com", "kotak.bank.in"),
    "paytm" to setOf("paytm.com", "paytm.me"),
    "phonepe" to setOf("phonepe.com"),
    "gpay" to setOf("pay.google.com", "google.com"),
    "amazon" to setOf("amazon.in", "amazon.com", "amzn.to", "amzn.in"),
    "flipkart" to setOf("flipkart.com", "fkrt.it"),
    "airtel" to setOf("airtel.in", "airtel.com"),
    "jio" to setOf("jio.com"),
    "indiapost" to setOf("indiapost.gov.in"),
    "incometax" to setOf("incometax.gov.in"),
    "uidai" to setOf("uidai.gov.in", "myaadhaar.uidai.gov.in"),
    "aadhaar" to setOf("uidai.gov.in"),
    "npci" to setOf("npci.org.in"),
    "epfo" to setOf("epfindia.gov.in"),
    "mahadiscom" to setOf("mahadiscom.in"),
    "msedcl" to setOf("mahadiscom.in"),
    "bescom" to setOf("bescom.co.in"),
    "irctc" to setOf("irctc.co.in"),
    "whatsapp" to setOf("whatsapp.com", "wa.me"),
)

/** Domains that are fine to link to (government and big services). */
private val KNOWN_GOOD_SUFFIXES = setOf(".gov.in", ".nic.in", ".bank.in") + BRANDS.values.flatten().toSet() +
    setOf(
        "google.com", "youtube.com", "youtu.be", "wikipedia.org", "whatsapp.com", "wa.me", "maps.app.goo.gl",
        "jiosaavn.com", "jiocinema.com", "jiomart.com", "jiohotstar.com", "paytmbank.com", "amazonaws.com",
    )


/** Lower-case host of a URL-ish string, without "www.". */
fun hostOf(url: String): String? {
    val noScheme = url.replace(Regex("(?i)^https?://"), "")
    val host = noScheme.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':').lowercase().trimEnd('.')
    if (host.isEmpty() || !host.contains('.')) return null
    return host.removePrefix("www.")
}

private fun matchesDomain(host: String, domain: String): Boolean =
    if (domain.startsWith(".")) host.endsWith(domain) else host == domain || host.endsWith(".$domain")

private fun linkReasons(text: String): Set<Reason> {
    val reasons = mutableSetOf<Reason>()
    URL.findAll(text).forEach { match ->
        val url = match.value.trimEnd('.', ',', ')', '!', '?')
        if (url.contains(Regex("(?i)\\.apk(?:$|[?#/])"))) reasons += Reason.APP_FILE
        val host = hostOf(url) ?: return@forEach
        if (host.matches(Regex("""\d{1,3}(?:\.\d{1,3}){3}"""))) {
            reasons += Reason.LOOKALIKE_LINK
            return@forEach
        }
        if (SHORTENERS.any { matchesDomain(host, it) }) {
            reasons += Reason.SHORT_LINK
            return@forEach
        }
        if (KNOWN_GOOD_SUFFIXES.any { matchesDomain(host, it) }) return@forEach
        val squashed = host.replace("-", "").replace(".", "")
        val imitated = BRANDS.keys.firstOrNull { squashed.contains(it) }
        if (imitated != null) {
            reasons += Reason.LOOKALIKE_LINK
            return@forEach
        }
        reasons += Reason.UNKNOWN_LINK
    }
    return reasons
}

fun checkMessage(raw: String): Verdict {
    val text = raw.replace('\u00A0', ' ').trim()
    if (text.isEmpty()) return Verdict(Level.NONE, emptyList())
    val reasons = mutableSetOf<Reason>()

    if (ASKS_CODE.containsMatchIn(text) && !NEGATED_CODE.containsMatchIn(text)) reasons += Reason.ASKS_OTP_PIN
    if (UPI_COLLECT.containsMatchIn(text)) reasons += Reason.UPI_COLLECT
    if (REMOTE_APP.containsMatchIn(text)) reasons += Reason.REMOTE_APP
    if (APK.containsMatchIn(text)) reasons += Reason.APP_FILE
    if (POLICE.containsMatchIn(text)) reasons += Reason.POLICE_THREAT
    if (KYC.containsMatchIn(text)) reasons += Reason.KYC
    if (BLOCKED.containsMatchIn(text)) reasons += Reason.ACCOUNT_BLOCKED
    if (PRIZE.containsMatchIn(text)) reasons += Reason.PRIZE
    if (JOB.containsMatchIn(text)) reasons += Reason.JOB_TASK
    if (REFUND.containsMatchIn(text)) reasons += Reason.REFUND
    if (NEW_NUMBER.containsMatchIn(text) && MONEY_ASK.containsMatchIn(text)) reasons += Reason.NEW_NUMBER_MONEY
    if (URGENCY.containsMatchIn(text)) reasons += Reason.URGENCY
    reasons += linkReasons(text)

    val sorted = reasons.sortedWith(compareByDescending<Reason> { it.weight }.thenBy { it.ordinal })
    val strong = sorted.any { it.weight >= 3 }
    val mediumScore = sorted.filter { it.weight == 2 }.size
    val weak = sorted.any { it.weight == 1 }
    val level = when {
        strong -> Level.DANGER
        mediumScore >= 2 -> Level.DANGER
        mediumScore == 1 && weak -> Level.DANGER
        mediumScore == 1 -> Level.CAREFUL
        else -> Level.NONE
    }
    // Weak signals (urgency) alone are normal in family chats ("come home immediately"), so don't list them alone.
    val shown = if (level == Level.NONE) emptyList() else sorted
    return Verdict(level, shown)
}
