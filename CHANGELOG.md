# Changelog

All notable changes to Nexus. Dates are IST.

## [Unreleased]

### Voice-dictated WhatsApp and SMS messages (2026-11-10) — feature/voice-messages
**Added**
- Parents can dictate a message: "पंकजला व्हॉट्सॲप कर आणि सांग की तू कधी येणार आहेस घरी", "pankaj ko whatsapp karo ki main late aaungi", "text Pankaj that dinner is ready". `Dictation.kt` reads the person (saved name, alias or relation word; Marathi/Hindi endings -ला/-ना/-को/-la/-ko; misheard names like "पंकच" for Pankaj), the app (WhatsApp spellings such as वाटसब/व्हॉट्सॲप/व्हाट्सएप/whats app, or SMS/मेसेज/message/msg/text) and the message after सांग की / बोल की / कह दो कि / bol ki / bata do ki / tell him that / say that, or everything after the verb.
- On-device rewrite of the message (rules only, nothing leaves the phone): drops a leading की/कि/that, splits a question from what follows at आणि/और/and, moves a trailing place word in front of the question word ("तू कधी येणार आहेस घरी" -> "तू घरी कधी येणार आहेस?"), ends questions with "?", capitalises English. The parent's words and person (तू/तुम/you) are kept.
- Message confirm screen: who it is for (initial + name), the message in large type (tap to edit, or Speak again), read back aloud in the parent's language ("Pankaj ला हा मेसेज पाठवू? ..."). "WhatsApp वर पाठवा / Send on WhatsApp" opens the chat with the text typed in (WhatsApp or WhatsApp Business); "SMS ने पाठवा / Send as SMS" opens the SMS app. SMS is first when the parent said SMS or WhatsApp isn't installed. A one-time line explains that the parent taps Send in WhatsApp. Unknown name -> "Who should get this message?" with faces; no message -> "What should I say?". No SEND_SMS permission.
- "messages" usage event in Family settings and the usage export (counts only).
- Strings in en/hi/mr (drafts, need native review).
- Tests: 38 parser/rewrite cases (`DictationTest`, including Pankaj's exact sentence, SMS, Hindi, English, unknown recipient, empty message, calls still routed to call), 12 Robolectric UI tests (`MessageUiTest`: edit, WhatsApp intent with the encoded text, WhatsApp Business, SMS fallback, who?/what?, read-back), 2 Roborazzi screenshots (mr, en). 207 JVM tests in total.

**Fixed**
- JVM tests leaked the saved language and country between tests in one fork (a "Calling Pankaj" vs Marathi TTS failure and a US/India screenshot flip depending on test order). `resetLocalDataForTest` now resets them.

### Phase 1 milestone B: parent mode S8-S13 (2026-11-10) — phase1/parent-mode
**Fixed**
- S12 feedback card: answering Q1 closed the round, so Q2 ("What would make it easier?") never showed. A round now closes when Q2 is saved or skipped, or after two skips of Q1; closed rounds are stored.
- Typed commands in Family settings no longer count as voice commands in the usage counts.
- A screenshot from an earlier "Is this safe?" check is deleted when Nexus starts.

**Added**
- S8/S9: 16 more scam tests from real Indian patterns (Hinglish "OTP batao", Hindi lottery, Marathi KYC and new-number money asks, courier/customs digital arrest, TeamViewer, OLX QR "receive" scams, lookalike bank domains) plus normal family, bank-debit and Ola-OTP messages that must stay quiet; a check that the mildest verdict never says "safe" in en/hi/mr; Ask-family quoting.
- S10 Ride home: last-used cab app as the one big button, "Other app" for the rest, a note when the address will be copied, and "Ask <family>" by WhatsApp/SMS when no cab app is installed. Saying "ghar jana hai" / "घरी जायचं आहे" / "take me home" opens it.
- S11 Family settings: Undo for a changed home address (people already had Undo), "Test the mic", rides and WhatsApp opens in the usage counts.
- S12: "Speak answer" for Q2 when the mic is allowed.
- S13 Privacy: five cards (what is saved, voice, messages you check, nothing is shared by itself, internet use) in en/hi/mr; the voice card says what this phone actually does. `PRIVACY.md` rewritten for parent mode.
- Tests: 13 Robolectric Compose UI tests for S10-S13 and 8 Roborazzi screenshots (S10-S13, en and mr). 155 JVM tests in total. Each UI test now starts from empty usage and feedback so screenshots don't depend on test order.

### Testing: fast JVM UI tests (2026-11-10) — testing/fast-ui-tests
**Added**
- Robolectric 4.16 + Compose UI test rule: 16 UI tests for parent home (4 people, call, WhatsApp, ride), the person sheet validation, the 3-second call countdown (Stop, dialer fallback, direct call with permission, spoken in the parent's language) and the "Is this safe?" verdict and Ask-family hand-off. Intents are captured on the JVM; nothing launches.
- Roborazzi 1.60.0 screenshot tests (parent home in English and Marathi, danger verdict in Marathi) with goldens in `android/app/src/test/roborazzi/`.
- `docs/testing.md`: layered strategy, timings, conventions and fakes; the emulator is now only a final smoke.

**Changed**
- Gradle build cache and configuration cache on; parallel test forks; host tests on the debug variant only.

### Phase 1 Step 0: foundations (2026-11-10) — phase1/foundations
**Fixed**
- Phone numbers: one normaliser (`Phone.kt`) for setup, the edit screen, the contacts picker and stored data. 10-digit Indian numbers starting with 91 no longer lose the country code (9123456789 is now +919123456789, not +9123456789). Leading 0, spaces, dashes, brackets, +91/0091 are handled. Numbers saved by older builds are repaired on load.
- "Pick me up at home" now sets home as the pickup, not the drop-off. Pickup/drop-off parsing handles from/to, "take me home", "ghar se", "gharun", "ghar jana hai", "घरी जायचं".
- Call parsing: "Call me at 98765 43210" offers that number instead of a person named "me". "Aai ko call karo" no longer reads "karo" as the name. Added Hinglish, Marathi and Devanagari forms ("Pankaj ko phone lagao", "Aai la phone lav", "Pankajla phone lav", "पंकज को फ़ोन लगाओ", "आईला फोन लाव"), and family words match across scripts (Aai = आई).
- Cab handoff: Uber uses its documented `dropoff[latitude|longitude|formatted_address]` (and pickup) parameters when coordinates exist; otherwise it opens Uber and copies the address. Ola and Rapido open the app and copy the address (no undocumented link parameters). A missing cab app falls back to Google Maps. Coordinates come from the on-device Geocoder at setup and are retried on launch.
- WhatsApp links always use the full international number.

**Changed**
- New design system (`Theme.kt`): light high-contrast colour tokens, parent and child type scales (no text under 16sp; 18sp in parent mode), `BigButton` with 48dp/64dp+ targets, outlined cards. The dark-mode toggle, the "Sample" button, the 3-card tour and the fare tagline are removed.
- All user-facing text is in `strings.xml` with Hindi (`values-hi`) and Marathi (`values-mr`) drafts that need native review. Per-app language on Android 13+ (`locales_config.xml`); speech input follows the app language.
- People rows show names with Call and WhatsApp buttons, not raw numbers. Office is optional. Setup accepts up to 4 people in stacked, labelled fields with inline validation.

**Added**
- JUnit tests: 46 tests for normalisation, parsing, pickup/drop-off and deep-link building.

### Added (2026-11-10) — docs/vision-design
- `docs/vision.md`: Nexus is now the family co-pilot for parents' Android phones; four phases with exit metrics and kill rules.
- `docs/design.md`: critical UI evaluation and the parent-mode revamp (13 screens, en/hi/mr key strings, design system).
- `docs/evaluation-2026-11-10.md`: teardown of commit 7fd6d17 (cab handoff, phone numbers, parsing bugs).
- `docs/decisions.md`, `docs/plan.md`: decisions log and living plan.
- Gradle wrapper (8.9) for `android/` so the app builds with `./gradlew`.
- README rewritten for the new vision with build instructions.
