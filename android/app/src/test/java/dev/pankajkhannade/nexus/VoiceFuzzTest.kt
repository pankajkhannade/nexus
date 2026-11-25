package dev.pankajkhannade.nexus

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Odd phrases a parent might say (QA 2026-11-25). Shared by the pure and the ViewModel fuzz tests. */
object FuzzPhrases {
    val words = listOf(
        "Pankaj", "pankaj", "पंकज", "पंकजला", "Pankaj la", "Priya", "Aai", "आई", "Raju", "Sunil", "सुनीलला", "Rahul", "राहुल को",
        "la", "ला", "ko", "को", "ne", "ची", "चा", "कडे", "ani", "आणि", "आणी", "aur", "और", "and", "ki", "की", "kee", "mhanje", "म्हणजे",
        "phone", "फोन", "lav", "लाव", "kar", "कर", "करो", "लगाओ", "call", "whatsapp", "व्हॉट्सॲप", "वाटसब", "sms", "message", "मेसेज",
        "sang", "सांग", "सांगा", "bol", "बोल", "tell", "him", "that", "mi", "मी", "निघाले", "yete", "येते", "late", "उशीर",
        "rickshaw", "रिक्षा", "auto", "ola", "uber", "rapido", "cab", "book", "बुक", "ghari", "घरी", "home", "office",
        "swargate", "स्वारगेट", "kothrud", "दीनानाथला", "hospital", "हॉस्पिटल", "medical", "औषध", "goli", "गोळी", "ghyaychi", "घेतली", "dawai", "le", "li",
        "safe", "खरं", "खरा", "आहे", "का", "scam", "fraud", "OTP", "1234", "pin", "PIN", "card", "4111 1111 1111 1111", "108", "112", "1930",
        "jar", "जर", "nahi", "नाही", "uchalla", "उचलला", "tar", "तर", "pahije", "पाहिजे", "chahiye", "near", "javal", "जवळ", "doctor",
        ",", ".", "?", "।", "!", "-", "…", "  ", "\n", "😀", "🙏", "a", "क", "्", "ा", "ं", "\u200d", "'", "\"", "%", "%s", "$", "\\",
    )

    fun phrase(rnd: Random): String = buildString {
        repeat(rnd.nextInt(0, 14)) { if (isNotEmpty() && rnd.nextInt(4) > 0) append(' '); append(words[rnd.nextInt(words.size)]) }
    }

    val fixed = listOf(
        "", " ", ".", "?", "।", "la", "ला", "ani", "आणि", "आणि आणि आणि", "Pankaj", "Pankaj la", "Pankaj la whatsapp kar",
        "Pankaj la whatsapp kar ani", "Pankaj la sang ki", "ola book kar", "rickshaw", "ola book kar swargate", "रिक्षा पाहिजे",
        "दीनानाथला रिक्षा बुक कर आणि Pankaj ला सांग मी निघाले", "Pankaj la phone lav ani jar nahi uchalla tar Priya la phone lav",
        "Pankaj la phone lav ani Priya la phone lav ani Aai la phone lav ani Raju la phone lav ani Sunil la phone lav",
        "हा मेसेज खरा आहे का", "goli ghyaychi", "dawai le li", "औषध पाहिजे", "OTP 482913 Pankaj la sang", "Pankaj la sang माझा PIN 4321 आहे",
        "x".repeat(5000), "पंकजला सांग ".repeat(300), "🙏🙏🙏", "\u200d\u200d", "%1\$s", "1234567890",
    )
}

/**
 * QA 2026-11-25: nothing a parent can say may crash the parser. 4,000 odd phrases (empty, punctuation,
 * half commands, mixed scripts, very long). Fixed seed, so a failure is reproducible; the phrase is printed.
 */
class VoiceFuzzTest {
    @Test fun parserNeverThrows() {
        val saved = Saved(
            setupDone = true, language = "mr", dial = "+91", home = Place("Kothrud, Pune", 18.5, 73.8),
            people = listOf(
                Person("1", "Pankaj", "+919876543210", Relation.SON, askFamily = true, localName = "पंकज"),
                Person("2", "Priya", "+919123456789", Relation.DAUGHTER),
                Person("3", "Aai", "+919988776655", Relation.SPOUSE),
                Person("4", "Raju", "+917000011122", Relation.OTHER, alias = "Bablu"),
            ),
        )
        val rnd = Random(20261125)
        val failures = mutableListOf<String>()
        (FuzzPhrases.fixed + List(4000) { FuzzPhrases.phrase(rnd) }).forEach { said ->
            runCatching {
                RuleIntentParser.parse(said, saved)
                splitCommand(said, saved)
                isCompleteCommand(RuleIntentParser, said, saved)
                readDictation(said, saved)
                readDestination(said)
                readNeed(said)
                isSafetyAsk(said)
            }.onFailure { failures += "${it::class.simpleName}: ${it.message} <- \"${said.take(120)}\"" }
        }
        assertTrue(failures.distinct().take(15).joinToString("\n"), failures.isEmpty())
    }

}
