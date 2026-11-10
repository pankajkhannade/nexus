package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class CabsTest {
    private val home = Place("Flat 4, 21 MG Road, Pune", 18.5204303, 73.8567437)
    private val office = Place("Hinjewadi Phase 1", 18.5912, 73.7389)
    private val noCoords = Place("Near Shivaji Park, Dadar")

    @Test fun uberDropoffUsesDocumentedCoordinateParams() {
        val h = cabHandoff(CabApp.UBER, Trip(dropoff = Stop(PlaceKind.HOME, home)))
        assertEquals(
            "https://m.uber.com/ul/?action=setPickup&pickup=my_location" +
                "&dropoff[latitude]=18.520430&dropoff[longitude]=73.856744" +
                "&dropoff[formatted_address]=Flat%204%2C%2021%20MG%20Road%2C%20Pune",
            h.uri,
        )
        assertEquals("com.ubercab", h.pkg)
        assertNull(h.copyText)
    }

    @Test fun uberPickupAtHome() {
        val h = cabHandoff(CabApp.UBER, Trip(pickup = Stop(PlaceKind.HOME, home)))
        val uri = h.uri!!
        assertTrue(uri.contains("pickup[latitude]=18.520430&pickup[longitude]=73.856744"))
        assertTrue(uri.contains("pickup[formatted_address]=Flat%204"))
        assertFalse(uri.contains("dropoff"))
        assertFalse(uri.contains("my_location"))
        assertNull(h.copyText)
    }

    @Test fun uberPickupAndDropoff() {
        val uri = cabHandoff(CabApp.UBER, Trip(Stop(PlaceKind.OFFICE, office), Stop(PlaceKind.HOME, home))).uri!!
        assertTrue(uri.contains("pickup[latitude]=18.591200"))
        assertTrue(uri.contains("dropoff[latitude]=18.520430"))
    }

    @Test fun uberWithoutCoordinatesCopiesAddressInstead() {
        val h = cabHandoff(CabApp.UBER, Trip(dropoff = Stop(PlaceKind.HOME, noCoords)))
        assertEquals("https://m.uber.com/ul/?action=setPickup&pickup=my_location", h.uri)
        assertEquals("Near Shivaji Park, Dadar", h.copyText)
    }

    @Test fun coordinatesUseDotDecimalsRegardlessOfLocale() {
        val before = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertTrue(cabHandoff(CabApp.UBER, Trip(dropoff = Stop(PlaceKind.HOME, home))).uri!!.contains("18.520430"))
        } finally {
            java.util.Locale.setDefault(before)
        }
    }

    @Test fun olaGetsADropOffDeepLinkAndRapidoCopiesTheAddress() {
        val trip = Trip(dropoff = Stop(PlaceKind.HOME, home))
        val ola = cabHandoff(CabApp.OLA, trip)
        // developers.olacabs.com/docs/deep-linking: olacabs://app/launch with drop_lat / drop_lng / drop_address.
        val uri = ola.uri!!
        assertTrue(uri, uri.startsWith("olacabs://app/launch?landing_page=bk"))
        assertTrue(uri, uri.contains("drop_lat=18.520430") && uri.contains("drop_lng=73.856744"))
        assertTrue(uri, uri.contains("drop_address=Flat%204%2C%2021%20MG%20Road%2C%20Pune"))
        assertFalse("no pickup: Ola uses where the parent is", uri.contains("&lat="))
        assertEquals("com.olacabs.customer", ola.pkg)
        assertNull("nothing to paste", ola.copyText)
        assertTrue(ola.carriesDropoff)
        assertEquals("copied quietly in case Ola ignores the link", home.address, ola.backupCopy)
        val rapido = cabHandoff(CabApp.RAPIDO, trip)
        assertNull(rapido.uri)
        assertEquals("com.rapido.passenger", rapido.pkg)
        assertEquals(home.address, rapido.copyText)
    }

    @Test fun olaPickupOnlyCopiesPickup() {
        assertEquals(home.address, cabHandoff(CabApp.OLA, Trip(pickup = Stop(PlaceKind.HOME, home))).copyText)
    }

    @Test fun mapsLinks() {
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=18.520430,73.856744",
            mapsLink(Trip(dropoff = Stop(PlaceKind.HOME, home))),
        )
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=Near%20Shivaji%20Park%2C%20Dadar&origin=18.591200,73.738900",
            mapsLink(Trip(Stop(PlaceKind.OFFICE, office), Stop(PlaceKind.HOME, noCoords))),
        )
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=18.520430,73.856744",
            mapsLink(Trip(pickup = Stop(PlaceKind.HOME, home))),
        )
        assertNull(mapsLink(Trip()))
    }

    @Test fun unsetPlacesAreIgnored() {
        val h = cabHandoff(CabApp.UBER, Trip(dropoff = Stop(PlaceKind.HOME, Place())))
        assertEquals("https://m.uber.com/ul/?action=setPickup&pickup=my_location", h.uri)
        assertNull(h.copyText)
        assertNull(mapsLink(Trip(dropoff = Stop(PlaceKind.HOME, Place()))))
    }

    @Test fun spokenPickupBecomesUberPickupEndToEnd() {
        val saved = Saved(setupDone = true, dial = "+91", home = home)
        val trip = readMessage("pick me up at home", saved).trip!!
        val uri = cabHandoff(CabApp.UBER, trip).uri!!
        assertTrue(uri.contains("pickup[latitude]=18.520430"))
        assertFalse(uri.contains("dropoff"))
    }

    @Test fun cabAppIds() {
        assertEquals(CabApp.OLA, CabApp.from("ola"))
        assertNull(CabApp.from("bogus"))
    }
}
