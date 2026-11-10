package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rides to anywhere (Pankaj: "book a ride to somewhere outside my home via voice"). */
class DestinationTest {
    private val pankaj = Person("1", "Pankaj", "+919876543210", Relation.SON, askFamily = true)
    private val saved = Saved(setupDone = true, dial = "+91", people = listOf(pankaj), home = Place("Baner, Pune", 18.559, 73.7868))

    @Test fun placePhrasesInMarathiHindiAndEnglish() {
        val cases = mapOf(
            "स्वारगेटला जायचं आहे" to "स्वारगेट",
            "मला दीनानाथ हॉस्पिटलला जायचं" to "दीनानाथ हॉस्पिटल",
            "mujhe station jana hai" to "station",
            "Phoenix mall jaana hai rickshaw" to "Phoenix mall",
            "take me to Pune airport" to "Pune airport",
            "cab book kar Kothrud la" to "Kothrud",
            "Kothrud la rickshaw book kar" to "Kothrud",
            "I want to go to Deccan Gymkhana" to "Deccan Gymkhana",
            "drop me at FC Road" to "FC Road",
            "book a cab to Hinjewadi Phase 1" to "Hinjewadi Phase 1",
            "Shivaji nagar le chalo" to "Shivaji nagar",
            "airport tak chalo" to "airport",
            "डेक्कनला सोडा" to "डेक्कन",
            "मला उद्या स्टेशनला जायचं आहे" to "स्टेशन",
            "मुझे रेलवे स्टेशन जाना है" to "रेलवे स्टेशन",
            "rickshaw ne Swargate la jaycha ahe" to "Swargate",
            "mala Sarasbaug la jaycha" to "Sarasbaug",
            "ride to Magarpatta City" to "Magarpatta City",
            "मंडईपर्यंत रिक्षा" to "मंडई",
            "Ruby Hall jana hai jaldi" to "Ruby Hall",
        )
        for ((said, place) in cases) assertEquals(said, place, readDestination(said))
    }

    @Test fun theParserTurnsThemIntoRides() {
        assertEquals(Command.RideTo("स्वारगेट"), RuleIntentParser.parse("स्वारगेटला जायचं आहे", saved))
        assertEquals(Command.RideTo("Pune airport"), RuleIntentParser.parse("take me to Pune airport", saved))
    }

    @Test fun homeStaysRideHome() {
        for (said in listOf("ghar jana hai", "घरी जायचं आहे", "take me home", "mujhe ghar jana hai")) {
            assertEquals(said, Command.RideHome, RuleIntentParser.parse(said, saved))
        }
    }

    @Test fun officeUsesTheSavedOffice() {
        val office = Place("Hinjewadi Phase 2", 18.59, 73.70)
        val withOffice = saved.copy(office = office)
        assertEquals(Command.RideTo(office.address, office), RuleIntentParser.parse("office la jaycha", withOffice))
        assertEquals(Command.RideTo(office.address, office), RuleIntentParser.parse("take me to office", withOffice))
    }

    @Test fun callsMessagesAndNeedsAreNeverRides() {
        val notRides = listOf(
            "Pankaj la phone lav", "call Pankaj", "पंकजला फोन लाव", "Sunil ko phone lagao",
            "Pankaj la sang ki mala station la jaycha ahe", "पंकजला व्हॉट्सॲप कर आणि सांग की मी स्वारगेटला जात आहे",
            "whatsapp Pankaj I am going to the market", "tell Pankaj to come to the station",
            "दूध मागव", "औषध पाहिजे", "रिक्षा पाहिजे", "doctor", "ambulance bulao", "plumber chahiye",
        )
        for (said in notRides) {
            assertNull(said, readDestination(said))
            assertTrue(said, RuleIntentParser.parse(said, saved) !is Command.RideTo)
        }
    }

    @Test fun aLoneNeedWordGoesToItsTileNotTheMap() {
        assertEquals(Command.OpenNeed(Need.DOCTOR), RuleIntentParser.parse("hospital la jaycha", saved))
        assertEquals(Command.RideTo("Dinanath hospital"), RuleIntentParser.parse("Dinanath hospital la jaycha", saved))
    }

    @Test fun uberGetsTheDropOffCoordinatesAndNickname() {
        val place = Place("Swargate Bus Stand, Swargate", 18.5018, 73.8636)
        val h = cabHandoff(CabApp.UBER, Trip(dropoff = Stop(PlaceKind.ADDRESS, place, label = "Swargate Bus Stand")))
        val uri = h.uri!!
        assertTrue(uri, uri.contains("pickup=my_location"))
        assertTrue(uri, uri.contains("dropoff[latitude]=18.501800"))
        assertTrue(uri, uri.contains("dropoff[nickname]=Swargate%20Bus%20Stand"))
        assertNull(h.copyText)
        assertEquals("Swargate Bus Stand, Swargate", cabHandoff(CabApp.OLA, Trip(dropoff = Stop(PlaceKind.ADDRESS, place))).copyText)
    }

    @Test fun distanceIsSane() {
        assertEquals(120.0, distanceKm(18.52, 73.86, 19.07, 72.88), 15.0) // Pune to Mumbai, roughly
    }
}
