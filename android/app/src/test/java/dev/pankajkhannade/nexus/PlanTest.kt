package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanTest {
    private val aai = Person("1", "Aai", "+919876543210")
    private val pankaj = Person("2", "Pankaj", "+919123456789")
    private val sheela = Person("3", "Sheela", "+919000000001")
    private val ravi = Person("4", "Ravi Kumar", "+919000000002")
    private val home = Place("Flat 4, 21 MG Road, Pune", 18.52, 73.85)
    private val office = Place("Hinjewadi Phase 1, Pune", 18.59, 73.74)
    private val saved = Saved(
        setupDone = true, dial = "+91", home = home, office = office,
        people = listOf(aai, pankaj, sheela, ravi),
    )

    private fun read(text: String, s: Saved = saved) = readMessage(text, s)

    // ---- calls ----

    @Test fun englishCall() {
        val r = read("Call Aai")
        assertEquals(Ask.CALL, r.ask)
        assertEquals(aai, r.person)
    }

    @Test fun englishCallWithTrailingWords() {
        assertEquals(pankaj, read("please call pankaj now").person)
        assertEquals(aai, read("phone Aai tomorrow at 5").person)
        assertEquals(ravi, read("call Ravi Kumar").person)
        assertEquals(ravi, read("call Ravi").person)
    }

    @Test fun callMeAtNumberIsNotAPersonNamedMe() {
        val r = read("Meet me at 21 MG Road. Call me at 98765 43210 if you get lost.", saved.copy(people = emptyList()))
        assertEquals(Ask.CALL, r.ask)
        assertNull(r.person)
        assertNull(r.askedName)
        assertEquals("+919876543210", r.number)
    }

    @Test fun numberInTextMatchesSavedPerson() {
        val r = read("Call me at 98765 43210")
        assertEquals(aai, r.person)
        assertNull(r.number)
    }

    @Test fun hinglishCall() {
        assertEquals(aai, read("Aai ko call karo").person)
        assertEquals(pankaj, read("Pankaj ko phone lagao").person)
        assertEquals(pankaj, read("zara Pankaj ko phone laga do").person)
        assertEquals(Ask.CALL, read("Aai ko call karo").ask)
    }

    @Test fun hinglishCallDoesNotTakeVerbAsName() {
        // The old parser read "karo" as the name.
        val r = read("Ravi Sharma ko call karo", saved.copy(people = listOf(aai)))
        assertNull(r.person)
        assertEquals("Ravi Sharma", r.askedName)
    }

    @Test fun marathiLatinCall() {
        assertEquals(pankaj, read("Pankaj la phone lav").person)
        assertEquals(pankaj, read("Pankajla phone lav").person)
        assertEquals(aai, read("Aai la phone kar").person)
        assertEquals(sheela, read("Sheela la phone kar").person)
        // A name that itself ends in "la" still matches when "la" is attached: "Sheelala".
        assertEquals(sheela, read("Sheelala phone kar").person)
        assertEquals(sheela, read("Sheela phone kar").person)
    }

    @Test fun devanagariCall() {
        val withDev = saved.copy(people = listOf(Person("5", "पंकज", "+919123456789"), aai))
        assertEquals("पंकज", read("पंकज को फ़ोन लगाओ", withDev).person?.name)
        assertEquals("पंकज", read("पंकजला फोन लाव", withDev).person?.name)
        // Relation words match across scripts: saved "Aai", spoken "आई".
        assertEquals(aai, read("आईला फोन लाव", withDev).person)
        assertEquals(aai, read("आई को फोन करो", withDev).person)
    }

    @Test fun relationSpellings() {
        val mummy = Person("6", "Mummy", "+919000000003")
        val s = saved.copy(people = listOf(mummy))
        assertEquals(mummy, read("Mumma ko call karo", s).person)
        assertEquals(mummy, read("मम्मी को फोन करो", s).person)
    }

    @Test fun messageAsks() {
        val r = read("WhatsApp Pankaj")
        assertEquals(Ask.MESSAGE, r.ask)
        assertEquals(pankaj, r.person)
        assertEquals(Ask.MESSAGE, read("Aai ko message karo").ask)
        assertEquals(aai, read("Aai la whatsapp kar").person)
    }

    @Test fun unknownNameIsOfferedForContactsSearch() {
        val r = read("call Suresh")
        assertNull(r.person)
        assertEquals("Suresh", r.askedName)
    }

    @Test fun emptyText() {
        val r = read("   ")
        assertNull(r.ask)
        assertNull(r.trip)
    }

    // ---- rides ----

    @Test fun pickMeUpAtHomeIsPickupNotDropoff() {
        // The old parser made home the drop-off.
        val trip = read("pick me up at home").trip!!
        assertEquals(PlaceKind.HOME, trip.pickup?.kind)
        assertEquals(home, trip.pickup?.place)
        assertNull(trip.dropoff)
    }

    @Test fun pickupVariants() {
        assertEquals(PlaceKind.HOME, read("Pick up from home").trip?.pickup?.kind)
        assertEquals(PlaceKind.OFFICE, read("pick me from office").trip?.pickup?.kind)
        assertEquals(PlaceKind.HOME, read("ghar se pick karo").trip?.pickup?.kind)
        assertEquals(PlaceKind.HOME, read("gharun pick kar").trip?.pickup?.kind)
    }

    @Test fun dropoffVariants() {
        assertEquals(PlaceKind.HOME, read("take me home").trip?.dropoff?.kind)
        assertEquals(PlaceKind.HOME, read("go home").trip?.dropoff?.kind)
        assertEquals(PlaceKind.OFFICE, read("ride to the office").trip?.dropoff?.kind)
        assertEquals(PlaceKind.HOME, read("ghar jana hai").trip?.dropoff?.kind)
        assertEquals(PlaceKind.HOME, read("घरी जायचं आहे").trip?.dropoff?.kind)
        assertNull(read("take me home").trip?.pickup)
    }

    @Test fun pickupAndDropoffTogether() {
        val t1 = read("pick me up from office and take me home").trip!!
        assertEquals(PlaceKind.OFFICE, t1.pickup?.kind)
        assertEquals(PlaceKind.HOME, t1.dropoff?.kind)
        val t2 = read("ghar se office").trip!!
        assertEquals(PlaceKind.HOME, t2.pickup?.kind)
        assertEquals(PlaceKind.OFFICE, t2.dropoff?.kind)
        val t3 = read("pick me up at home and drop me at office").trip!!
        assertEquals(PlaceKind.HOME, t3.pickup?.kind)
        assertEquals(PlaceKind.OFFICE, t3.dropoff?.kind)
    }

    @Test fun bareMentionIsDropoff() {
        assertEquals(PlaceKind.OFFICE, read("office").trip?.dropoff?.kind)
    }

    @Test fun streetAddress() {
        val t = read("Meet me at 21 MG Road, Flat 4, Bengaluru.").trip!!
        assertEquals("21 MG Road, Flat 4", t.dropoff?.place?.address)
        val p = read("pick me up from 12 FC Road").trip!!
        assertEquals("12 FC Road", p.pickup?.place?.address)
    }

    @Test fun unsavedPlaceIsStillRecognised() {
        val t = read("take me home", saved.copy(home = Place())).trip!!
        assertEquals(PlaceKind.HOME, t.dropoff?.kind)
        assertEquals(false, t.dropoff?.place?.isSet)
    }

    @Test fun callPlusPlace() {
        val r = read("call Aai, I'm going home")
        assertEquals(aai, r.person)
        assertEquals(PlaceKind.HOME, r.trip?.dropoff?.kind)
    }

    @Test fun nothingRecognised() {
        val r = read("hello there")
        assertNull(r.ask)
        assertNull(r.person)
        assertNull(r.trip)
    }
}
