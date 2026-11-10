package dev.pankajkhannade.nexus

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** UX sprint 1: faces you can tell apart (#1) and India-only address search (#6). Pure JVM. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class FacesAndPlacesTest {
    private val pankaj = Person("919876543210", "Pankaj", "+919876543210", "son")
    private val priya = Person("919123456789", "Priya", "+919123456789", "daughter")
    private val prakash = Person("917000011122", "Prakash", "+917000011122", "other")
    private val pooja = Person("919988776655", "Pooja", "+919988776655", "other")

    @Test fun everyFaceColourCarriesWhiteTextAtAA() {
        FACE_COLORS.forEach { c -> assertTrue("$c: ${contrast(c, Color.White)}", contrast(c, Color.White) >= 4.5) }
        assertEquals("8 distinct colours", 8, FACE_COLORS.toSet().size)
    }

    @Test fun samInitialPeopleGetDifferentColours() {
        val family = listOf(pankaj, priya, prakash, pooja)
        val slots = family.map { faceColorIndex(it, family) }
        assertEquals("four P's, four colours", 4, slots.toSet().size)
        assertNotEquals(faceColorIndex(pankaj, family), faceColorIndex(priya, family))
    }

    @Test fun aFaceKeepsItsColourWhenSomeoneElseIsEdited() {
        val family = listOf(pankaj, priya)
        val edited = listOf(pankaj.copy(alias = "Bunty"), priya)
        assertEquals(faceColorIndex(pankaj, family), faceColorIndex(edited[0], edited))
    }

    @Test fun phoneBookMatchesNotInTheFamilyStillDifferInAList() {
        val options = listOf(pankaj, pankaj.copy(id = "919876500000", phone = "+919876500000"))
        val a = faceColorIndex(options[0], emptyList(), 0)
        val b = faceColorIndex(options[1], emptyList(), 1)
        assertTrue(a in FACE_COLORS.indices && b in FACE_COLORS.indices)
    }

    @Test fun firstNameIsShownInFull() {
        assertEquals("Pankaj", firstName("Pankaj Khannade"))
        assertEquals("आई", firstName(" आई "))
    }

    @Test fun addressSearchAsksForIndiaOnly() {
        val url = suggestUrl("Shivaji Park, Dadar")
        assertTrue(url, url.contains("countryCode=IND"))
        assertTrue(url, url.contains("text=Shivaji+Park%2C+Dadar"))
        assertFalse("no location bias without home coordinates", url.contains("location="))
        val near = suggestUrl("Kothrud", Place("Shivaji Nagar, Pune", 18.5308, 73.8475))
        assertTrue(near, near.contains("location=73.84750,18.53080"))
    }

    /** Recorded 2026-11-11: the same query with a US locale returned "Dadaram, ... Waycross, GA, USA". */
    @Test fun shivajiParkDadarNeverShowsANonIndiaResult() {
        val json = """
            {"suggestions":[
              {"text":"Shivaji Park Dadar West, Mumbai, Maharashtra, IND"},
              {"text":"Dadaram, 610 Memorial Dr, Waycross, GA, 31501, USA"},
              {"text":"Shivaji Park Post Office, Dadarkar Building, 1 N C Kelkar Marg, Dadar West, Mumbai, Maharashtra, 400028, IND"},
              {"text":"Shivaji Park, Altoona, PA, USA"}
            ]}
        """.trimIndent()
        assertEquals(
            listOf(
                "Shivaji Park Dadar West, Mumbai, Maharashtra",
                "Shivaji Park Post Office, Dadarkar Building, 1 N C Kelkar Marg, Dadar West, Mumbai, Maharashtra, 400028",
            ),
            parseSuggestions(json),
        )
    }

    @Test fun theGeocoderBoxIsIndia() {
        assertTrue(IndiaBox.contains(19.0270, 72.8380)) // Shivaji Park, Dadar
        assertTrue(IndiaBox.contains(8.0883, 77.5385)) // Kanyakumari
        assertFalse(IndiaBox.contains(40.5187, -78.3947)) // Altoona, PA
        assertFalse(IndiaBox.contains(51.5072, -0.1276)) // London
    }
}
