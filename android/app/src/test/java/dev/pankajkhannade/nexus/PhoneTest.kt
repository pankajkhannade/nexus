package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneTest {
    private val IN = "+91"

    @Test fun plainTenDigitIndianMobile() = assertEquals("+919876543210", normalizePhone("9876543210", IN))

    @Test fun tenDigitNumberStartingWith91KeepsCountryCode() {
        // The old international() returned "+9123456789" here, dropping the country code.
        assertEquals("+919123456789", normalizePhone("9123456789", IN))
        assertEquals("+919198765432", normalizePhone("91987 65432", IN))
    }

    @Test fun leadingTrunkZero() = assertEquals("+919876543210", normalizePhone("09876543210", IN))

    @Test fun spacesDashesAndBrackets() {
        assertEquals("+919876543210", normalizePhone("98765 43210", IN))
        assertEquals("+919876543210", normalizePhone("98765-43210", IN))
        assertEquals("+919876543210", normalizePhone("(98765) 432.10", IN))
        assertEquals("+919876543210", normalizePhone(" 98765\u00A043210 ", IN))
    }

    @Test fun countryCodeForms() {
        assertEquals("+919876543210", normalizePhone("+91 98765 43210", IN))
        assertEquals("+919876543210", normalizePhone("+91-98765-43210", IN))
        assertEquals("+919876543210", normalizePhone("919876543210", IN))
        assertEquals("+919876543210", normalizePhone("0919876543210", IN))
        assertEquals("+919876543210", normalizePhone("00919876543210", IN))
    }

    @Test fun foreignNumbersWithPlusAreKeptForIndianUser() {
        assertEquals("+14155552671", normalizePhone("+1 (415) 555-2671", IN))
        assertEquals("+447911123456", normalizePhone("0044 7911 123456", IN))
    }

    @Test fun rejectsBadIndianNumbers() {
        assertNull(normalizePhone("5876543210", IN)) // must start 6-9
        assertNull(normalizePhone("987654321", IN)) // 9 digits
        assertNull(normalizePhone("98765432101", IN)) // 11 digits, no leading 0
        assertNull(normalizePhone("+91 5876543210", IN))
        assertNull(normalizePhone("98765abc10", IN))
        assertNull(normalizePhone("98+76543210", IN))
        assertNull(normalizePhone("", IN))
    }

    @Test fun otherCountries() {
        assertEquals("+447911123456", normalizePhone("07911 123456", "+44"))
        assertEquals("+14155552671", normalizePhone("415-555-2671", "+1"))
        assertEquals("+14155552671", normalizePhone("1 415 555 2671", "+1"))
        assertEquals("+6591234567", normalizePhone("9123 4567", "+65"))
        assertNull(normalizePhone("12345", "+44"))
    }

    @Test fun problems() {
        assertEquals(PhoneProblem.DIGITS_ONLY, phoneProblem("98765abc", IN, strict = false))
        assertNull(phoneProblem("98765", IN, strict = false)) // still typing
        assertEquals(PhoneProblem.INDIA_LENGTH, phoneProblem("98765", IN, strict = true))
        assertEquals(PhoneProblem.INDIA_START, phoneProblem("5876543210", IN, strict = false))
        assertEquals(PhoneProblem.INDIA_LENGTH, phoneProblem("987654321012", IN, strict = true))
        assertNull(phoneProblem("+91 98765 43210", IN, strict = true))
        assertNull(phoneProblem("9123456789", IN, strict = true))
        assertEquals(PhoneProblem.LENGTH, phoneProblem("12345", "+44", strict = true))
        assertNull(phoneProblem("", IN, strict = true))
    }

    @Test fun repairsNumbersSavedByOlderBuilds() {
        // The 91-prefix bug stored "+9123456789"; the edit screen stored raw local numbers.
        assertEquals("+919123456789", repairStoredPhone("+9123456789", IN))
        assertEquals("+919876543210", repairStoredPhone("98765 43210", IN))
        assertEquals("+919876543210", repairStoredPhone("+919876543210", IN))
        assertEquals("not a number", repairStoredPhone("not a number", IN))
    }

    @Test fun displayAndDigits() {
        assertEquals("+91 98765 43210", displayPhone("+919876543210"))
        assertEquals("+14155552671", displayPhone("+14155552671"))
        assertEquals("919876543210", phoneDigits("+919876543210"))
    }

    @Test fun whatsAppLinkUsesFullInternationalDigits() {
        assertEquals("https://wa.me/919123456789", whatsAppLink("+919123456789", ""))
        assertEquals("https://wa.me/919876543210?text=Is%20this%20real%3F", whatsAppLink("+919876543210", "Is this real?"))
    }
}
