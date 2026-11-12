package dev.pankajkhannade.nexus

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The language and region registries (docs/i18n-architecture.md) agree with the app's resources, and moving
 * India's numbers and Nexus's languages into them changed no behaviour.
 */
class LanguagePackTest {
    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    @Test fun everyRegisteredLanguageHasStringsAndIsInTheLocaleConfig() {
        val config = File(res, "xml/locales_config.xml").readText()
        PARENT_LANGUAGES.forEach { lang ->
            assertTrue("${lang.code} needs android:name in locales_config.xml", config.contains("android:name=\"${lang.code}\""))
            if (lang.code != "en") assertTrue("${lang.code} needs values-${lang.code}/strings.xml", File(res, "values-${lang.code}/strings.xml").isFile)
        }
    }

    @Test fun everyTranslatedStringFolderIsARegisteredLanguage() {
        val folders = res.listFiles().orEmpty().map { it.name }.filter { Regex("""values-[a-z]{2,3}""").matches(it) }
        folders.forEach { f -> assertTrue("$f has strings but no PARENT_LANGUAGES entry", PARENT_LANGUAGES.any { "values-${it.code}" == f }) }
    }

    @Test fun everyTranslationHasEveryEnglishString() {
        val names = Regex("""<string name="([^"]+)"(?![^>]*translatable="false")""")
        val english = names.findAll(File(res, "values/strings.xml").readText()).map { it.groupValues[1] }.toSet()
        PARENT_LANGUAGES.filter { it.code != "en" }.forEach { lang ->
            val local = names.findAll(File(res, "values-${lang.code}/strings.xml").readText()).map { it.groupValues[1] }.toSet()
            assertEquals("missing in values-${lang.code}", emptySet<String>(), english - local)
        }
    }

    @Test fun speechTagsAreUnchanged() {
        assertEquals("mr-IN", speechTag("mr"))
        assertEquals("hi-IN", speechTag("hi"))
        assertEquals("en-IN", speechTag("en"))
        assertEquals("en-IN", speechTag(""))
        assertEquals("en-IN", speechTag("xx"))
        assertEquals(listOf("mr", "hi", "en"), LANGUAGES.map { it.first })
        assertEquals(DEFAULT_LANGUAGE, PARENT_LANGUAGES.first().code)
    }

    @Test fun localNamesFollowTheLanguagesScript() {
        val p = Person(id = "1", name = "Pankaj", phone = "+919876543210", localName = "पंकज")
        assertEquals("पंकज", p.shownIn("mr"))
        assertEquals("पंकज", p.shownIn("hi"))
        assertEquals("Pankaj", p.shownIn("en"))
        assertEquals("Pankaj", p.copy(localName = "Panky").shownIn("mr"))
        assertTrue(Script.DEVANAGARI.isIn("आई"))
        assertFalse(Script.LATIN.isIn("Aai"))
    }

    @Test fun indiaPackKeepsTodaysNumbersAndSearch() {
        assertEquals("108", emergencyNumber(""))
        assertEquals("108", emergencyNumber("IN"))
        assertEquals("112", emergencyNumber("DE"))
        assertEquals("1930", CYBER_HELPLINE)
        assertEquals("https://cybercrime.gov.in", CYBER_PORTAL)
        assertTrue(suggestUrl("Shivaji Park").contains("countryCode=IND"))
        assertEquals("411045", pincodeIn("Baner, Pune 411045"))
        assertNull(pincodeIn("Baner, Pune 011045"))
        assertTrue(INDIA.bounds.contains(18.52, 73.85))
        assertFalse(INDIA.bounds.contains(40.5, -78.4))
        assertEquals(INDIA, regionFor("DE"))
        assertEquals(CabApp.entries.toList(), regionFor("IN").cabApps)
    }
}
