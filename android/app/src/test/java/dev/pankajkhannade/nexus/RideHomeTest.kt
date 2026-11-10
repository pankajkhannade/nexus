package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S10 Ride home: which app comes first, and which spoken phrases open it. */
class RideHomeTest {
    private val saved = Saved(setupDone = true, home = Place("12 MG Road, Pune"), people = listOf(Person("1", "Rahul", "+919876543210", Relation.SON)))

    @Test fun lastUsedAppComesFirst() {
        assertEquals(listOf(CabApp.OLA, CabApp.UBER, CabApp.RAPIDO), rideOrder(listOf(CabApp.UBER, CabApp.OLA, CabApp.RAPIDO), "ola"))
    }

    @Test fun firstInstalledWhenLastUsedIsMissing() {
        assertEquals(listOf(CabApp.UBER, CabApp.RAPIDO), rideOrder(listOf(CabApp.UBER, CabApp.RAPIDO), "ola"))
        assertEquals(listOf(CabApp.RAPIDO), rideOrder(listOf(CabApp.RAPIDO), ""))
    }

    @Test fun noCabAppMeansEmpty() {
        assertTrue(rideOrder(emptyList(), "uber").isEmpty())
    }

    @Test fun spokenGoHomePhrasesOpenRideHome() {
        for (said in listOf("ghar jana hai", "घरी जायचं आहे", "take me home")) {
            assertTrue(said, wantsRideHome(readMessage(said, saved)))
        }
    }

    @Test fun callingSomeoneIsNotARide() {
        assertFalse(wantsRideHome(readMessage("Rahul ko phone lagao", saved)))
        assertFalse(wantsRideHome(readMessage("pick me up at home", saved)))
    }

    @Test fun olaAndRapidoCopyTheHomeAddress() {
        val trip = Trip(dropoff = Stop(PlaceKind.HOME, saved.home))
        assertEquals("12 MG Road, Pune", cabHandoff(CabApp.OLA, trip).copyText)
        assertEquals("12 MG Road, Pune", cabHandoff(CabApp.RAPIDO, trip).copyText)
        val withCoords = Trip(dropoff = Stop(PlaceKind.HOME, Place("12 MG Road, Pune", 18.52, 73.85)))
        assertEquals(null, cabHandoff(CabApp.UBER, withCoords).copyText)
    }
}
