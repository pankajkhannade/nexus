package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * More real-world Indian scam texts (Hinglish, Hindi, Marathi, English) plus the "never say safe" rule.
 * Numbers and links are made up.
 */
class SafetyIndiaTest {
    private fun level(text: String) = checkMessage(text).level
    private fun reasons(text: String) = checkMessage(text).reasons

    @Test fun hinglishOtpBataoIsDanger() {
        val text = "Beta main bank se bol raha hu, aapke phone pe jo OTP aaya hai wo jaldi batao warna account band ho jayega"
        assertEquals(Level.DANGER, level(text))
        assertTrue(Reason.ASKS_OTP_PIN in reasons(text))
    }

    @Test fun hindiOtpBhejoIsDanger() {
        assertEquals(Level.DANGER, level("आपके मोबाइल पर आया OTP भेजो, आपका इनाम का पैसा तुरंत खाते में आएगा"))
    }

    @Test fun hinglishGharBaitheTaskIsFlagged() {
        val text = "Ghar baithe kamaye Rs 5000 roz. Sirf YouTube videos like karo. Telegram group join karo abhi"
        assertNotEquals(Level.NONE, level(text))
        assertTrue(Reason.JOB_TASK in reasons(text))
    }

    @Test fun hinglishBijliKatIsFlagged() {
        val text = "Aapka bijli connection aaj raat 9 baje kat jayega. Turant is number par call kare 9123456780"
        assertEquals(Level.DANGER, level(text))
    }

    @Test fun hindiLotteryWithLinkIsDanger() {
        val text = "बधाई हो! आपने KBC लॉटरी में 25 लाख जीते हैं। इनाम पाने के लिए यहाँ क्लिक करें http://kbc-lottery-winner.top"
        assertEquals(Level.DANGER, level(text))
        assertTrue(Reason.PRIZE in reasons(text))
    }

    @Test fun marathiKycShortLinkIsDanger() {
        val text = "प्रिय ग्राहक, तुमचे KYC अपडेट नसल्याने खाते बंद होईल. लगेच अपडेट करा: bit.ly/kyc-mh"
        assertEquals(Level.DANGER, level(text))
        assertTrue(Reason.KYC in reasons(text))
    }

    @Test fun marathiLotteryIsFlagged() {
        assertNotEquals(Level.NONE, level("अभिनंदन! तुम्ही 10 लाखांचे बक्षीस जिंकलात. आजच संपर्क करा"))
    }

    @Test fun marathiNewNumberMoneyIsFlagged() {
        val text = "आई हा माझा नवीन नंबर आहे. लगेच 10000 पैसे पाठवा, नंतर सांगतो"
        assertNotEquals(Level.NONE, level(text))
        assertTrue(Reason.NEW_NUMBER_MONEY in reasons(text))
    }

    @Test fun courierCustomsDigitalArrestIsDanger() {
        val text = "Your FedEx parcel to Taiwan has been seized by customs with illegal drugs. Call CBI officer now or face arrest warrant"
        assertEquals(Level.DANGER, level(text))
        assertTrue(Reason.POLICE_THREAT in reasons(text))
    }

    @Test fun teamViewerSupportIsDanger() {
        assertEquals(Level.DANGER, level("Sir aapka refund pending hai. TeamViewer QuickSupport download karke code bataiye"))
    }

    @Test fun phonePeQrToReceiveIsDanger() {
        assertEquals(Level.DANGER, level("Main aapka sofa khareed raha hu OLX pe. Scan this QR code to receive Rs 12000 advance"))
    }

    @Test fun lookalikeBankDomainIsDanger() {
        val v = checkMessage("Your HDFC NetBanking will expire. Login now https://hdfc-netbanking-verify.in/secure")
        assertEquals(Level.DANGER, v.level)
        assertTrue(Reason.LOOKALIKE_LINK in v.reasons)
    }

    @Test fun realMessagesStayQuiet() {
        assertEquals(Level.NONE, level("Rs.500.00 debited from A/c XX1234 on 10-11-26 to VPA kirana@okaxis. Not you? Call 18001234 - SBI"))
        assertEquals(Level.NONE, level("Papa kal 10 baje doctor appointment hai, main lene aaunga"))
        assertEquals(Level.NONE, level("आई, मी 8 वाजता घरी पोहोचतो. जेवण तयार ठेव."))
        assertEquals(Level.NONE, level("Your Ola ride OTP is 4821. Share it with your driver to start the trip."))
        assertEquals(Level.NONE, level("बेटा, दवाई ले आना, शाम को पूजा है"))
        assertEquals(Level.NONE, level("Watch https://www.youtube.com/watch?v=abc123 for the bhajan"))
    }

    @Test fun emptyTextHasNoVerdictReasons() {
        val v = checkMessage("   ")
        assertEquals(Level.NONE, v.level)
        assertTrue(v.reasons.isEmpty())
    }

    @Test fun noVerdictEverSaysSafe() {
        // Nexus never tells a parent a message is safe. The mildest verdict title must not claim safety in any language.
        // (The body line "This does not mean it is safe" is a deliberate negation and is not checked here.)
        val res = File("src/main/res")
        val forbidden = mapOf(
            "values" to listOf("is safe", "it's safe", "looks safe", "safe message"),
            "values-hi" to listOf("सुरक्षित है"),
            "values-mr" to listOf("सुरक्षित आहे"),
        )
        forbidden.forEach { (dir, words) ->
            val xml = File(res, "$dir/strings.xml").readText()
            val none = Regex("""name="verdict_none">([^<]*)<""").findAll(xml).joinToString(" ") { it.groupValues[1] }
            assertTrue("verdict_none missing in $dir", none.isNotBlank())
            words.forEach { w -> assertFalse("$dir verdict says '$w': $none", none.lowercase().contains(w)) }
        }
    }

    @Test fun askFamilyQuotesTheMessage() {
        val text = askFamilyText("  Share OTP now  ", "Is this real?")
        assertTrue(text.startsWith("\"Share OTP now\""))
        assertTrue(text.endsWith("Is this real?"))
        assertEquals("Is this real?", askFamilyText("", "Is this real?"))
        assertTrue(askFamilyText("x".repeat(2000), "?").length < 700)
    }
}
