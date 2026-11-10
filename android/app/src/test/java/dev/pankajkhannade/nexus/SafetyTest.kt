package dev.pankajkhannade.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Realistic Indian SMS / WhatsApp texts. Numbers and links are made up. */
class SafetyTest {
    private fun level(text: String) = checkMessage(text).level
    private fun reasons(text: String) = checkMessage(text).reasons

    @Test fun kycBlockWithLookalikeLinkIsDanger() {
        val v = checkMessage("Dear Customer, your SBI YONO account will be blocked today. Update PAN KYC immediately: http://sbi-kyc-update.xyz/login")
        assertEquals(Level.DANGER, v.level)
        assertTrue(Reason.LOOKALIKE_LINK in v.reasons)
        assertTrue(Reason.KYC in v.reasons)
        assertTrue(v.showNeverShare)
    }

    @Test fun electricityDisconnectionScamIsDanger() {
        val text = "Dear consumer your electricity power will be disconnected tonight at 9.30 pm because your previous month bill was not updated. Please immediately contact our electricity officer 9876543210"
        assertEquals(Level.DANGER, level(text))
        assertTrue(Reason.ACCOUNT_BLOCKED in reasons(text))
    }

    @Test fun askingForOtpIsDanger() {
        assertEquals(Level.DANGER, level("Sir I sent the OTP by mistake to your number, please share the OTP with me"))
        assertTrue(Reason.ASKS_OTP_PIN in reasons("Please tell me the OTP you received"))
    }

    @Test fun realBankOtpThatSaysDoNotShareIsNotFlagged() {
        val text = "123456 is your OTP for txn of INR 2,499.00 at AMAZON on HDFC Bank card ending 1234. Valid for 10 mins. Do not share OTP with anyone."
        assertEquals(Level.NONE, level(text))
    }

    @Test fun hindiBankOtpWithNegationIsNotFlagged() {
        val text = "आपका OTP 482913 है। यह कोड किसी को न बताएं। - SBI"
        assertEquals(Level.NONE, level(text))
    }

    @Test fun upiCollectToReceiveMoneyIsDanger() {
        val text = "Congratulations! You have received Rs 5000 cashback. Enter your UPI PIN to receive the money in your account."
        val v = checkMessage(text)
        assertEquals(Level.DANGER, v.level)
        assertTrue(Reason.UPI_COLLECT in v.reasons)
        assertTrue(v.showNeverShare)
    }

    @Test fun olxBuyerHinglishUpiIsDanger() {
        assertEquals(Level.DANGER, level("Sir maine payment request bheja hai, paise receive karne ke liye apna UPI PIN daal do"))
    }

    @Test fun anyDeskIsDanger() {
        val v = checkMessage("For refund of your Flipkart order please install AnyDesk app and share the 9 digit code")
        assertEquals(Level.DANGER, v.level)
        assertTrue(Reason.REMOTE_APP in v.reasons)
    }

    @Test fun apkLinkIsDanger() {
        val v = checkMessage("PM Kisan new list. Download app: http://bit.ly/pmkisan-list.apk")
        assertEquals(Level.DANGER, v.level)
        assertTrue(Reason.APP_FILE in v.reasons)
    }

    @Test fun digitalArrestIsDanger() {
        val v = checkMessage("This is Mumbai Cyber Crime police. A parcel in your name has drugs. You are under digital arrest. Do not tell anyone.")
        assertEquals(Level.DANGER, v.level)
        assertTrue(Reason.POLICE_THREAT in v.reasons)
    }

    @Test fun jobTaskScamIsAtLeastCareful() {
        val text = "Part time job! Earn Rs 3000 daily by liking YouTube videos. Work from home. Contact on Telegram"
        assertTrue(level(text) != Level.NONE)
        assertTrue(Reason.JOB_TASK in reasons(text))
    }

    @Test fun lotteryIsAtLeastCareful() {
        assertTrue(level("Congratulations! Your number has won KBC lottery of Rs 25,00,000. Call now to claim.") != Level.NONE)
    }

    @Test fun newNumberMoneyIsFlagged() {
        val text = "Hi Papa this is my new number. Urgent, please send 15000 on GPay, will explain later"
        assertTrue(level(text) != Level.NONE)
        assertTrue(Reason.NEW_NUMBER_MONEY in reasons(text))
    }

    @Test fun hindiKycScamIsDanger() {
        val text = "प्रिय ग्राहक, आपका बैंक खाता आज बंद हो जाएगा। तुरंत KYC अपडेट करें, नीचे लिंक पर क्लिक करें http://tinyurl.com/kyc-bank"
        assertEquals(Level.DANGER, level(text))
    }

    @Test fun marathiElectricityScamIsDanger() {
        val text = "प्रिय ग्राहक, आज रात्री 9:30 वाजता तुमचे वीज कनेक्शन बंद केले जाईल. लगेच या नंबरवर संपर्क करा 9876543210"
        assertEquals(Level.DANGER, level(text))
    }

    @Test fun marathiOtpRequestIsDanger() {
        assertEquals(Level.DANGER, level("तुमच्या फोनवर आलेला OTP मला सांगा, नाहीतर खाते बंद होईल"))
    }

    @Test fun shortLinkAloneIsCareful() {
        val v = checkMessage("See this video bit.ly/3abcXYZ")
        assertEquals(Level.CAREFUL, v.level)
        assertTrue(Reason.SHORT_LINK in v.reasons)
    }

    @Test fun officialDomainsAreNotLookalike() {
        assertFalse(Reason.LOOKALIKE_LINK in reasons("Check https://www.onlinesbi.sbi and https://www.incometax.gov.in for details"))
        assertTrue(Reason.LOOKALIKE_LINK in reasons("Login at https://www.onlinesbi.sbi.secure-login.top"))
    }

    @Test fun ipAddressLinkIsLookalike() {
        assertTrue(Reason.LOOKALIKE_LINK in reasons("Claim your refund http://103.21.58.4/refund"))
    }

    @Test fun ordinaryFamilyMessagesShowNoWarning() {
        assertEquals(Level.NONE, level("Aai, I will reach home by 8. Keep dinner ready."))
        assertEquals(Level.NONE, level("बेटा, कल सुबह डॉक्टर के पास जाना है"))
        assertEquals(Level.NONE, level("उद्या सकाळी मंदिरात जाऊया"))
        assertEquals(Level.NONE, level("Your Swiggy order is out for delivery"))
    }

    @Test fun urgencyAloneShowsNothing() {
        assertEquals(Level.NONE, level("Come home immediately, guests have arrived"))
    }

    @Test fun shownReasonsAreAtMostThreeStrongestFirst() {
        val v = checkMessage("URGENT: your SBI account blocked. Update KYC now at http://sbi-kyc.top and share OTP. Install AnyDesk.")
        assertTrue(v.shown.size <= 3)
        assertTrue(v.shown.first().weight >= v.shown.last().weight)
    }
}
