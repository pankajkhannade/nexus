package dev.pankajkhannade.nexus.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.pankajkhannade.nexus.PersonDraft
import dev.pankajkhannade.nexus.PersonSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** S2 person sheet: the validation a child sees while setting up the parent's phone. */
@RunWith(RobolectricTestRunner::class)
class PersonSheetUiTest {
    @get:Rule val compose = createComposeRule()

    private var saved: PersonDraft? = null

    private fun show(duplicate: String? = null) = compose.setParent {
        PersonSheet(
            draft = PersonDraft(null, "", "", "", askFamily = true, alias = "", typed = true),
            dial = "+91",
            onDismiss = {},
            onRemove = null,
            onSave = { saved = it; true },
            duplicateOf = { duplicate },
        )
    }

    private fun save() = compose.onNodeWithText("Save").performScrollTo().performClick()

    @Test fun blankNameIsRejected() {
        show()
        compose.onNodeWithText("Phone number").performTextInput("9876543210")
        save()
        compose.onNodeWithText("Add a name for this number.").assertExists()
        assertNull(saved)
    }

    @Test fun shortIndianNumberShowsTheTenDigitRule() {
        show()
        compose.onNodeWithText("Name, as your parent says it").performTextInput("Aai")
        compose.onNodeWithText("Phone number").performTextInput("98765")
        save()
        compose.onAllNodesWithText("Indian mobile numbers have 10 digits.").fetchSemanticsNodes().let { assert(it.isNotEmpty()) }
        assertNull(saved)
    }

    @Test fun numberStartingWithFiveIsRejected() {
        show()
        compose.onNodeWithText("Name, as your parent says it").performTextInput("Aai")
        compose.onNodeWithText("Phone number").performTextInput("5123456789")
        save()
        compose.onAllNodesWithText("Indian mobile numbers start with 6, 7, 8 or 9.").fetchSemanticsNodes().let { assert(it.isNotEmpty()) }
        assertNull(saved)
    }

    @Test fun duplicateNumberNamesWhoAlreadyHasIt() {
        show(duplicate = "Pankaj")
        compose.onNodeWithText("Name, as your parent says it").performTextInput("Aai")
        compose.onNodeWithText("Phone number").performTextInput("9876543210")
        save()
        compose.onNodeWithText("Pankaj already has this number.").assertExists()
        assertNull(saved)
    }

    @Test fun validPersonIsSaved() {
        show()
        compose.onNodeWithText("Name, as your parent says it").performTextInput("Aai")
        compose.onNodeWithText("Phone number").performTextInput("98765 43210")
        save()
        assertEquals("Aai", saved?.name)
    }

    // UX sprint 2: the optional Devanagari name.
    @Test fun thePersonSheetHasTheDevanagariNameBox() {
        var savedDraft: dev.pankajkhannade.nexus.PersonDraft? = null
        compose.setParent {
            dev.pankajkhannade.nexus.PersonSheet(
                draft = dev.pankajkhannade.nexus.PersonDraft(null, "Pankaj", "98765 43210", "son", askFamily = true, alias = "", typed = true),
                dial = "+91", onDismiss = {}, onRemove = null, onSave = { savedDraft = it; true }, duplicateOf = { null },
            )
        }
        compose.onNodeWithText("Name in Marathi / Hindi letters (optional)").performTextInput("पंकज")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        assertEquals("पंकज", savedDraft?.localName)
    }
}
