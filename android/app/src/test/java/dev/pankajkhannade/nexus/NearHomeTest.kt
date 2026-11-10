package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S15 Near home: catalog, PIN code, spoken needs and maps links (pure JVM, no Robolectric). */
class NearHomeTest {
    private val pkgPattern = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")

    @Test fun everyPickableNeedHasAtLeastTwoAppsWithUniquePackages() {
        for (need in Need.PICKABLE) {
            val apps = appsFor(need)
            assertTrue("$need has ${apps.size} apps", apps.size >= 2)
            assertEquals("$need has a duplicate package", apps.size, apps.map { it.pkg }.toSet().size)
            assertEquals("$need has a duplicate label", apps.size, apps.map { it.label }.toSet().size)
            apps.forEach { assertTrue("bad package ${it.pkg}", pkgPattern.matches(it.pkg)) }
        }
        assertTrue(appsFor(Need.EMERGENCY).isEmpty())
    }

    @Test fun catalogKeepsTheVerifiedPackagesInPopularityOrder() {
        assertEquals("com.grofers.customerapp", appsFor(Need.GROCERY).first().pkg)
        assertEquals("com.aranoah.healthkart.plus", appsFor(Need.MEDICINE).first().pkg)
        assertEquals("com.practo.fabric", appsFor(Need.DOCTOR).first().pkg)
        assertEquals("com.urbanclap.urbanclap", appsFor(Need.HOME_SERVICE).first().pkg)
        assertEquals(CabApp.entries.map { it.pkg }, appsFor(Need.RIDE).map { it.pkg })
        // Live Play package for Ola; the old "com.olacabs" is 404 on Play.
        assertTrue(appsFor(Need.RIDE).any { it.pkg == "com.olacabs.customer" })
        // Housejoy left Play in 2022.
        assertFalse(SERVICE_CATALOG.values.flatten().any { it.pkg.contains("housejoy") })
    }

    @Test fun defaultPickIsTheFirstInstalledAppOrNone() {
        val installed = setOf("com.zeptoconsumerapp", "com.bigbasket.mobileapp", "com.phonegap.rxpal")
        val picks = defaultPicks { it in installed }
        assertEquals("com.zeptoconsumerapp", picks["grocery"])
        assertEquals("com.phonegap.rxpal", picks["medicine"])
        assertEquals("", picks["doctor"])
        assertEquals("", picks["home_service"])
    }

    @Test fun pincodeIsReadFromTheAddress() {
        assertEquals("411045", pincodeIn("Flat 4, Baner Road, Pune, Maharashtra 411045"))
        assertEquals("400076", pincodeIn("Powai, Mumbai 400 076, India"))
        assertEquals("411045", pincodeIn("12 MG Road, Pune 411045"))
        assertNull(pincodeIn("Shivaji Nagar, Pune"))
        assertNull(pincodeIn("Call +91 98765 43210"))
        assertNull(pincodeIn("012345 Road"))
    }

    @Test fun addressPinBeatsTheGeocoderAndBadPinsAreRejected() {
        assertEquals("411045", homePincode("Baner, Pune 411045", "411001"))
        assertEquals("411007", homePincode("Aundh, Pune", "411007"))
        assertEquals("", homePincode("Aundh, Pune", null))
        assertEquals("", homePincode("Aundh, Pune", "SW1A"))
        assertEquals("411045", cleanPincode("411 045"))
        assertNull(cleanPincode("41104"))
        assertNull(cleanPincode("011045"))
    }

    @Test fun spokenNeedsInMarathiHindiEnglishAndHinglish() {
        val cases = mapOf(
            "औषध पाहिजे" to Need.MEDICINE,
            "dawai chahiye" to Need.MEDICINE,
            "medicine" to Need.MEDICINE,
            "मला औषधं हवी आहेत" to Need.MEDICINE,
            "दवाइयाँ मँगाओ" to Need.MEDICINE,
            "मेडिकल दुकान कुठे आहे" to Need.MEDICINE,
            "दूध मागव" to Need.GROCERY,
            "doodh mangao" to Need.GROCERY,
            "groceries" to Need.GROCERY,
            "भाजी आणि किराणा मागव" to Need.GROCERY,
            "Blinkit kholo" to Need.GROCERY,
            "doctor" to Need.DOCTOR,
            "डॉक्टर" to Need.DOCTOR,
            "पास में डॉक्टर कहाँ है" to Need.DOCTOR,
            "दवाखाना कुठे आहे" to Need.DOCTOR,
            "plumber" to Need.HOME_SERVICE,
            "electrician chahiye" to Need.HOME_SERVICE,
            "बाथरूममध्ये पाणी गळतंय प्लंबर हवा" to Need.HOME_SERVICE,
            "रिक्षा पाहिजे" to Need.RIDE,
            "auto bulao" to Need.RIDE,
            "ambulance bulao" to Need.EMERGENCY,
            "108 ला फोन लाव" to Need.EMERGENCY,
            "medical emergency" to Need.EMERGENCY,
        )
        cases.forEach { (said, need) -> assertEquals(said, need, readNeed(said)) }
    }

    @Test fun callsMessagesAndChatAreNotNeeds() {
        for (said in listOf(
            "Pankaj la phone lav", "call Pankaj", "Aai ko call karo", "पंकजला फोन लाव",
            "Pankaj la sang doodh pahije", "पंकजला सांग की येताना दूध घेऊन ये", "tell Pankaj to bring medicine",
            "whatsapp Pankaj dawai", "ghar jana hai", "hello", "",
        )) {
            assertNull(said, readNeed(said))
        }
    }

    @Test fun mapsSearchUsesHomeCoordinatesThenPinThenAddress() {
        val home = Place("Baner, Pune", 18.559, 73.7868)
        assertEquals("geo:18.559000,73.786800?q=medical%20store", nearbyGeoUri("medical store", home, "411045"))
        assertEquals("geo:0,0?q=doctor%20clinic%20near%20411045", nearbyGeoUri("doctor clinic", Place("Baner, Pune"), "411045"))
        assertEquals("geo:0,0?q=hospital%20near%20Baner%2C%20Pune", nearbyGeoUri("hospital", Place("Baner, Pune"), ""))
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=medical%20store&center=18.559000,73.786800",
            nearbyWebUri("medical store", home, ""),
        )
    }

    @Test fun tilesShowOnlyWhatWorks() {
        val bare = Saved(setupDone = true)
        assertEquals(listOf(Need.MEDICINE, Need.DOCTOR, Need.EMERGENCY), visibleNeeds(bare))
        val full = bare.copy(home = Place("Baner"), services = mapOf("grocery" to "com.grofers.customerapp", "home_service" to "com.urbanclap.urbanclap"))
        assertEquals(Need.entries.toList(), visibleNeeds(full))
        assertEquals(listOf(Need.MEDICINE, Need.DOCTOR, Need.EMERGENCY), visibleNeeds(bare.copy(services = mapOf("grocery" to "", "home_service" to "com.unknown"))))
    }

    @Test fun emergencyNumberIs108InIndia() {
        assertEquals("108", emergencyNumber("IN"))
        assertEquals("108", emergencyNumber(""))
        assertEquals("112", emergencyNumber("GB"))
    }

    @Test fun nearHomeCountsAreExportedAsCountsOnly() {
        val usage = Usage().record(Event.NEAR_MEDICINE, 20402).record(Event.NEAR_MEDICINE, 20402).record(Event.NEAR_EMERGENCY, 20402)
        val text = exportText(usage, Feedback(), 20400, 20402, "mr")
        assertTrue(text, text.contains("near_medicine=2"))
        assertTrue(text, text.contains("near_emergency=1"))
        assertEquals(usage, Usage.parse(usage.serialize()))
    }
}
