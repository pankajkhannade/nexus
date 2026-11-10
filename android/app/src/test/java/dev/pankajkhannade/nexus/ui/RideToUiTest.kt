package dev.pankajkhannade.nexus.ui

import android.content.ClipboardManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.pankajkhannade.nexus.CabApp
import dev.pankajkhannade.nexus.DestinationScreen
import dev.pankajkhannade.nexus.Need
import dev.pankajkhannade.nexus.NexusViewModel
import dev.pankajkhannade.nexus.Place
import dev.pankajkhannade.nexus.PlaceFinder
import dev.pankajkhannade.nexus.RideScreen
import dev.pankajkhannade.nexus.Screen
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Voice and typed rides to a destination, with a fake Geocoder. */
@RunWith(RobolectricTestRunner::class)
class RideToUiTest {
    @get:Rule val compose = createComposeRule()

    private val asked = mutableListOf<Pair<String, Place>>()
    private val finder = PlaceFinder { query, near, _ ->
        asked += query to near
        when {
            query.contains("दीनानाथ") || query.contains("Dinanath", true) -> listOf(
                Place("Deenanath Mangeshkar Hospital, Erandwane", 18.5089, 73.8323),
                Place("Deenanath Mangeshkar Hospital OPD, Karve Road", 18.5080, 73.8310),
            )
            query.contains("Swargate", true) || query.contains("स्वारगेट") -> listOf(Place("Swargate Bus Stand, Swargate", 18.5018, 73.8636))
            else -> emptyList()
        }
    }

    private fun model(): NexusViewModel = newModel(people = 1, home = "Baner, Pune").also {
        it.placeFinder = finder
        it.io = Dispatchers.Unconfined
    }

    @Test fun spokenHospitalShowsChoicesNearHomeThenUberGetsTheDropOff() {
        fakeInstall(CabApp.UBER.pkg)
        val model = model()
        model.understood("मला दीनानाथ हॉस्पिटलला जायचं", typed = true)
        assertTrue(model.screen == Screen.Destination)
        assertEquals("दीनानाथ हॉस्पिटल", asked.single().first)
        assertEquals("Baner, Pune", asked.single().second.address)

        compose.setParent { DestinationScreen(model) }
        compose.onNodeWithText("Deenanath Mangeshkar Hospital, Erandwane").assertIsDisplayed()
        compose.onNodeWithText("Deenanath Mangeshkar Hospital OPD, Karve Road").assertIsDisplayed()
        compose.onAllGoHere().first().performScrollTo().performClick()

        assertTrue(model.screen == Screen.Ride)
        assertEquals(18.5089, model.rideTo?.lat!!, 0.0001)
    }

    @Test fun theRideScreenOpensUberWithCoordinatesAndPickupWhereYouAre() {
        fakeInstall(CabApp.UBER.pkg)
        val model = model()
        model.chooseDestination(Place("Swargate Bus Stand, Swargate", 18.5018, 73.8636))
        compose.setParent { RideScreen(model) }
        compose.onNodeWithText("🚕  Go to Swargate Bus Stand").assertIsDisplayed()
        compose.onNodeWithText("Pickup: where you are now.").assertIsDisplayed()
        compose.onNodeWithText("Open Uber").performScrollTo().performClick()

        val uri = nextStartedIntent()!!.dataString!!
        assertTrue(uri, uri.contains("pickup=my_location"))
        assertTrue(uri, uri.contains("dropoff[latitude]=18.501800"))
        assertTrue(uri, uri.contains("dropoff[nickname]=Swargate%20Bus%20Stand"))
        assertTrue(model.screen == Screen.Home)
        assertEquals(null, model.rideTo)
    }

    @Test fun olaGetsTheDestinationInTheLinkAndCopiedAsABackup() {
        fakeInstall(CabApp.OLA.pkg)
        val model = model()
        model.chooseDestination(Place("Swargate Bus Stand, Swargate", 18.5018, 73.8636))
        compose.setParent { RideScreen(model) }
        compose.onNodeWithText("The place is filled in for you.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Open Ola").performScrollTo().performClick()
        val intent = nextStartedIntent()!!
        assertTrue(intent.dataString!!, intent.dataString!!.contains("drop_lat=18.501800"))
        assertEquals(CabApp.OLA.pkg, intent.`package`)
        // Coach line said at the handoff, in the parent's language (Marathi by default in tests).
        assertEquals("Ola उघडतोय, Swargate Bus Stand भरलेलं आहे. भाडं बघा, मग बुक करा.", model.spoken.last())
        val clip = app.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertEquals("Swargate Bus Stand, Swargate", clip)
    }

    @Test fun nothingOnTheMapStillPassesTheWordsToTheCabApp() {
        fakeInstall(CabApp.OLA.pkg)
        val model = model()
        model.understood("Phoenix mall jaana hai rickshaw", typed = true)
        compose.setParent { DestinationScreen(model) }
        compose.onNodeWithText("I couldn’t find “Phoenix mall” on the map. The cab app can search for it.").assertIsDisplayed()
        compose.onNodeWithText("Search “Phoenix mall” in the cab app").performScrollTo().performClick()
        assertTrue(model.screen == Screen.Ride)
        assertEquals("Phoenix mall", model.rideTo?.address)
    }

    @Test fun noCabAppOffersMapsDirections() {
        val model = model()
        model.chooseDestination(Place("Swargate Bus Stand, Swargate", 18.5018, 73.8636))
        compose.setParent { RideScreen(model) }
        compose.onNodeWithText("🗺  Directions in Google Maps").performScrollTo().performClick()
        val uri = nextStartedIntent()!!.dataString!!
        assertTrue(uri, uri.startsWith("https://www.google.com/maps/dir/?api=1&destination=18.501800,73.863600"))
    }

    @Test fun typedSomewhereElseFromTheRideHomeScreen() {
        fakeInstall(CabApp.UBER.pkg)
        val model = model()
        model.openNeed(Need.RIDE)
        compose.setParent { RideScreen(model) }
        compose.onNodeWithText("📍 Somewhere else").performScrollTo().performClick()
        compose.onNodeWithText("Place name or address").performTextInput("Swargate")
        compose.onNodeWithText("Find").performScrollTo().performClick()
        assertTrue(model.screen == Screen.Destination)
        assertEquals("Swargate Bus Stand, Swargate", model.destHits?.single()?.address)
    }

    @Test fun theRideTileWorksEvenWithoutAHomeAddress() {
        val model = newModel(people = 1).also { it.placeFinder = finder; it.io = Dispatchers.Unconfined }
        model.openNeed(Need.RIDE)
        assertTrue(model.screen == Screen.Ride)
        compose.setParent { RideScreen(model) }
        compose.onNodeWithText("Place name or address").assertIsDisplayed()
    }

    @Test fun callMessageAndNearHomePhrasesNeverOpenARide() {
        val model = model()
        model.understood("Pankaj la phone lav", typed = true)
        assertTrue(model.screen is Screen.Calling)
        model.understood("Pankaj la sang ki mala station la jaycha ahe", typed = true)
        assertTrue(model.screen == Screen.Message)
        model.cancelDraft()
        model.understood("रिक्षा पाहिजे", typed = true)
        assertTrue(model.screen == Screen.Ride)
        assertEquals(null, model.rideTo)
        assertTrue(asked.isEmpty())
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllGoHere() =
        onAllNodes(androidx.compose.ui.test.hasText("Go here")).fetchSemanticsNodes().indices.map { onAllNodes(androidx.compose.ui.test.hasText("Go here"))[it] }
}
