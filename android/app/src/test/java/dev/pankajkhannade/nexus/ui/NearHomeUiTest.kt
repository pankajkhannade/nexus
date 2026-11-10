package dev.pankajkhannade.nexus.ui

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.pankajkhannade.nexus.COUNTRIES
import dev.pankajkhannade.nexus.EmergencyScreen
import dev.pankajkhannade.nexus.Event
import dev.pankajkhannade.nexus.InstallScreen
import dev.pankajkhannade.nexus.NearHomeScreen
import dev.pankajkhannade.nexus.NearHomeStep
import dev.pankajkhannade.nexus.Need
import dev.pankajkhannade.nexus.NeedHelpScreen
import dev.pankajkhannade.nexus.NexusTheme
import dev.pankajkhannade.nexus.NexusViewModel
import dev.pankajkhannade.nexus.Place
import dev.pankajkhannade.nexus.Screen
import dev.pankajkhannade.nexus.ServicePicker
import dev.pankajkhannade.nexus.UiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

const val BLINKIT = "com.grofers.customerapp"
const val ZEPTO = "com.zeptoconsumerapp"
const val TATA_1MG = "com.aranoah.healthkart.plus"
const val URBAN_COMPANY = "com.urbanclap.urbanclap"

fun waitFor(what: String, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 5_000
    while (!condition()) {
        check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
        idleMain()
        Thread.sleep(2)
    }
}

/** Home in Baner with coordinates and PIN from a fake Geocoder (Robolectric's real one returns nothing). */
fun NexusViewModel.homeWithCoordinates(): NexusViewModel {
    lookupHome = { Place(it, 18.559, 73.7868) to "411045" }
    saveHome("Baner, Pune")
    waitFor("geocoded home") { saved.home.hasCoordinates && saved.pincode == "411045" }
    return this
}

/** S15 Near home: child picker, parent tiles, hand-offs, voice. */
@RunWith(RobolectricTestRunner::class)
class NearHomeUiTest {
    @get:Rule val compose = createComposeRule()

    /** The Near home screens the way NexusApp routes them. */
    @Composable
    private fun Routed(model: NexusViewModel) {
        when (val s = model.screen) {
            is Screen.NeedHelp -> NeedHelpScreen(model, s.need)
            is Screen.Install -> InstallScreen(model, s.need, s.app)
            is Screen.Emergency -> EmergencyScreen(model)
            else -> NearHomeScreen(model)
        }
    }

    private fun setChild(content: @Composable () -> Unit) = compose.setContent { NexusTheme(mode = UiMode.Child) { Column { content() } } }

    // ---- Child: dropdown ----

    @Test fun dropdownTagsInstalledAppsAndOffersInstallForTheRest() {
        fakeInstall(ZEPTO)
        setChild { ServicePicker(Need.GROCERY, ZEPTO) {} }
        compose.onNodeWithText("Zepto · Installed").assertIsDisplayed()

        compose.onNodeWithTag("picker_grocery").performClick()

        compose.onNodeWithTag("installed_$ZEPTO", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("install_$BLINKIT", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag("installed_$BLINKIT", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag("install_$ZEPTO", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("None").assertExists()
    }

    @Test fun pickingAnAppInTheDropdownSavesIt() {
        var picked = ""
        setChild { ServicePicker(Need.MEDICINE, "") { picked = it } }
        compose.onNodeWithTag("picker_medicine").performClick()
        compose.onNodeWithText("PharmEasy").performClick()
        assertEquals("com.phonegap.rxpal", picked)
    }

    @Test fun installOpensThePlayStoreApp() {
        setChild { ServicePicker(Need.GROCERY, "") {} }
        compose.onNodeWithTag("picker_grocery").performClick()
        compose.onNodeWithTag("install_$BLINKIT", useUnmergedTree = true).performClick()

        val intent = nextStartedIntent()!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("market://details?id=$BLINKIT", intent.dataString)
    }

    @Test fun installFallsBackToTheWebPageWithoutAPlayStoreApp() {
        val browser = ComponentName("com.android.browser", "com.android.browser.Main")
        shadowOf(app.packageManager).addActivityIfNotPresent(browser)
        shadowOf(app.packageManager).addIntentFilterForActivity(
            browser,
            IntentFilter(Intent.ACTION_VIEW).apply { addCategory(Intent.CATEGORY_DEFAULT); addCategory(Intent.CATEGORY_BROWSABLE); addDataScheme("https") },
        )
        shadowOf(app).checkActivities(true)
        setChild { ServicePicker(Need.GROCERY, BLINKIT) {} }

        compose.onNodeWithText("Install Blinkit").performClick()

        val intent = nextStartedIntent()!!
        assertEquals("https://play.google.com/store/apps/details?id=$BLINKIT", intent.dataString)
    }

    @Test fun setupStepPreselectsTheFirstInstalledAppAndReadsThePin() {
        fakeInstall(ZEPTO)
        fakeInstall("com.phonegap.rxpal")
        val model = newModel(people = 1)
        model.setupHome = "Baner Road, Pune 411045"
        model.setupServices = emptyMap()
        model.setupPincode = ""
        setChild { NearHomeStep(model) }
        idleMain()

        assertEquals(ZEPTO, model.setupServices["grocery"])
        assertEquals("com.phonegap.rxpal", model.setupServices["medicine"])
        assertEquals("", model.setupServices["home_service"])
        assertEquals("411045", model.setupPincode)
        compose.onNodeWithText("Zepto · Installed").assertExists()

        model.setupPeople = model.saved.people
        model.finishSetup(COUNTRIES.first { it.code == "IN" })
        assertEquals(ZEPTO, model.saved.services["grocery"])
        assertEquals("411045", model.saved.pincode)
    }

    @Test fun pincodeComesFromTheGeocoderWhenTheAddressHasNone() {
        val model = newModel(people = 1)
        model.lookupHome = { Place(it, 18.56, 73.81) to "411007" }
        model.saveHome("Aundh, Pune")
        waitFor("pincode") { model.saved.pincode == "411007" }
        model.saveHome("Baner Road, Pune 411045")
        assertEquals("411045", model.saved.pincode)
    }

    // ---- Parent: tiles ----

    @Test fun groceryTileOpensThePickedApp() {
        fakeInstall(BLINKIT)
        val model = newModel(people = 1, home = "Baner, Pune")
        model.pickService(Need.GROCERY, BLINKIT)
        model.screen = Screen.NearHome
        val before = model.usage.total(Event.NEAR_GROCERY)
        compose.setParent { Routed(model) }

        compose.onNodeWithContentDescription("Milk & groceries").performScrollTo().performClick()

        val intent = nextStartedIntent()!!
        assertEquals(BLINKIT, intent.component?.packageName ?: intent.`package`)
        assertEquals(before + 1, model.usage.total(Event.NEAR_GROCERY))
        assertTrue(model.screen == Screen.Home)
    }

    @Test fun aRemovedAppShowsTheBigInstallScreen() {
        val model = newModel(people = 1, home = "Baner, Pune")
        model.pickService(Need.HOME_SERVICE, URBAN_COMPANY)
        model.screen = Screen.NearHome
        compose.setParent { Routed(model) }

        compose.onNodeWithContentDescription("Home help & repairs").performScrollTo().performClick()

        compose.onNodeWithText("Urban Company is not on this phone").assertIsDisplayed()
        compose.onNodeWithText("Install Urban Company").performScrollTo().performClick()
        assertEquals("market://details?id=$URBAN_COMPANY", nextStartedIntent()!!.dataString)
    }

    @Test fun tilesWithoutAPickAreHiddenButDoctorMedicinesAndEmergencyStay() {
        val model = newModel(people = 1)
        compose.setParent { NearHomeScreen(model) }
        compose.onNodeWithContentDescription("Medicines").assertExists()
        compose.onNodeWithContentDescription("Doctor").assertExists()
        compose.onNodeWithContentDescription("Emergency").assertExists()
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Milk & groceries")).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Rickshaw / cab")).fetchSemanticsNodes().size)
    }

    @Test fun medicinesWithoutAnAppSearchesMedicalStoresAroundHome() {
        val model = newModel(people = 1).homeWithCoordinates()
        compose.setParent { Routed(model) }
        compose.onNodeWithText("Services for 411045").assertIsDisplayed()

        compose.onNodeWithContentDescription("Medicines").performScrollTo().performClick()

        val intent = nextStartedIntent()!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("geo:18.559000,73.786800?q=medical%20store", intent.dataString)
        assertEquals(1, model.usage.total(Event.NEAR_MEDICINE))
    }

    @Test fun medicinesWithAnAppOffersTheAppAndNearbyStores() {
        fakeInstall(TATA_1MG)
        val model = newModel(people = 1).homeWithCoordinates()
        model.pickService(Need.MEDICINE, TATA_1MG)
        compose.setParent { Routed(model) }

        compose.onNodeWithContentDescription("Medicines").performScrollTo().performClick()
        compose.onNodeWithText("Order on Tata 1mg").assertIsDisplayed()
        compose.onNodeWithText("Nearby medical stores", substring = true).performScrollTo().performClick()
        assertEquals("geo:18.559000,73.786800?q=medical%20store", nextStartedIntent()!!.dataString)
    }

    @Test fun doctorSearchesClinicsAroundHomeCoordinates() {
        val model = newModel(people = 1).homeWithCoordinates()
        compose.setParent { Routed(model) }

        compose.onNodeWithContentDescription("Doctor").performScrollTo().performClick()

        assertEquals("geo:18.559000,73.786800?q=doctor%20clinic", nextStartedIntent()!!.dataString)
        assertEquals(1, model.usage.total(Event.NEAR_DOCTOR))
    }

    @Test fun emergencyDials108AndFindsTheNearestHospital() {
        val model = newModel(people = 2).homeWithCoordinates()
        compose.setParent { Routed(model) }

        compose.onNodeWithContentDescription("Emergency").performScrollTo().performClick()
        compose.onNodeWithText("Call 108 (ambulance)", substring = true).performClick()
        val dial = nextStartedIntent()!!
        assertEquals(Intent.ACTION_DIAL, dial.action)
        assertEquals("tel:108", dial.dataString)

        compose.onNodeWithText("Call Pankaj", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nearest hospital", substring = true).performScrollTo().performClick()
        assertEquals("geo:18.559000,73.786800?q=hospital", nextStartedIntent()!!.dataString)
        assertEquals(1, model.usage.total(Event.NEAR_EMERGENCY))
    }

    // ---- Voice ----

    @Test fun spokenNeedsRouteStraightToTheirTile() {
        fakeInstall(BLINKIT)
        fakeInstall(URBAN_COMPANY)
        val model = newModel(people = 1).homeWithCoordinates()
        model.pickService(Need.GROCERY, BLINKIT)
        model.pickService(Need.HOME_SERVICE, URBAN_COMPANY)

        for (said in listOf("दूध मागव", "doodh mangao", "groceries")) {
            model.understood(said, typed = true)
            assertEquals(said, BLINKIT, nextStartedIntent()!!.component?.packageName)
        }
        for (said in listOf("plumber", "electrician chahiye", "प्लंबर पाहिजे")) {
            model.understood(said, typed = true)
            assertEquals(said, URBAN_COMPANY, nextStartedIntent()!!.component?.packageName)
        }
        for (said in listOf("औषध पाहिजे", "dawai chahiye", "medicine")) {
            model.understood(said, typed = true)
            assertEquals(said, "geo:18.559000,73.786800?q=medical%20store", nextStartedIntent()!!.dataString)
        }
        for (said in listOf("doctor", "डॉक्टर", "doctor la phone lav")) {
            model.understood(said, typed = true)
            assertEquals(said, "geo:18.559000,73.786800?q=doctor%20clinic", nextStartedIntent()!!.dataString)
        }
        model.understood("ambulance bulao", typed = true)
        assertTrue(model.screen == Screen.Emergency)
        model.understood("रिक्षा पाहिजे", typed = true)
        assertTrue(model.screen == Screen.Ride)
    }

    @Test fun callsMessagesAndRideHomeStillWin() {
        val model = newModel(people = 1, home = "Baner, Pune")
        model.understood("Pankaj la phone lav", typed = true)
        assertTrue(model.screen.toString(), model.screen is Screen.Calling)
        model.understood("call Pankaj", typed = true)
        assertTrue(model.screen is Screen.Calling)
        model.understood("Pankaj la sang doodh pahije", typed = true)
        assertTrue(model.screen.toString(), model.screen == Screen.Message)
        model.cancelDraft()
        model.understood("पंकजला वाटसब कर आणि सांग की येताना औषध घेऊन ये", typed = true)
        assertTrue(model.screen.toString(), model.screen == Screen.Message)
        model.cancelDraft()
        model.understood("ghar jana hai", typed = true)
        assertTrue(model.screen == Screen.Ride)
        model.understood("hello there", typed = true)
        assertTrue(model.screen == Screen.NotUnderstood)
    }
}
