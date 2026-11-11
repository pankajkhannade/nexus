# Changelog

All notable changes to Nexus. Dates are IST.

## [Unreleased]

### UX sprint 2 (2026-11-11) — feat/ux-sprint-2
The rest of the UX critique (section 5 and ranked fixes 7–20), as an 8-item sprint.

**Adjustable countdown (#1).** Family settings > "Wait before calling": 3, 5 or 8 seconds (default 5, was a fixed 3), saved on the phone and used by every call countdown: home faces, voice calls, Not understood, Help, Money gone. An unknown saved value falls back to 5. (WCAG 2.2.1: the parent can get more time.)

**Clear button names (#2).** Listening, dictating and calling use one pair everywhere: **✓ झालं** (finish) and **✕ रद्द करा** (cancel); Hindi "हो गया / रद्द करें", English "Done / Cancel". The old "Stop" is gone. On the message screen, "पुन्हा बोला" sits in the bottom bar and becomes ✓ झालं while listening, with the live words right under the name. "Ask family" has one name across settings. "Money gone" now reads "पैसे गेले? आता काय करायचं".

**Honest, shorter setup (#3).** Steps: Language, Family, Home, Mic, Calls, then "All set" (not numbered). Mic and Calls pages are skipped when the permission is already on, and the count says so ("Step 2 of 3" on a phone that has both). The Near home step left setup: Nexus picks the installed apps itself (editable in Family settings). The home pincode is one question on the Home step, pre-filled from the address or the Geocoder, or "PIN code: 411005" when the address already has it.

**Help on home and "did it work?" (#4).** Home's header is a big "🙋 मदत: Pankaj ला विचारा" button (Call with the countdown, or a prefilled message). After a call, WhatsApp, SMS, a ride or Maps, home asks "फोन लागला का?" / "गाडी बुक झाली का?" with **हो, झालं** / **नाही**. Yes says "छान"; No opens Help. The answer is logged as `return_check` (yes/no plus the app category only); Help and Money gone opens are counted (`help_opened`, `money_gone_opened`).

**The bottom bar everywhere (#5).** मागे / बोला now also on Listening, Confirm, Calling, Message, Help and Money gone (left = leave or ✕ रद्द करा, right = बोला, or ✓ झालं while listening).

**पैसे गेले (#6).** From the scam verdict or Is this safe?: four numbered steps, each one tap: (1) call 1930 (dial pad), (2) call the bank (dial pad, with "the number is on the back of the card"), (3) complain at cybercrime.gov.in (browser), (4) tell <child> (call with the countdown, or a prefilled message). A "don't do this now" panel: no OTP or PIN, no app installs, no paying anyone who promises to get the money back. No new permissions: no SEND_SMS, call log or phone state (a test checks the manifest).

**2.0x text (#7).** Home, Not understood, the scam warning and every setup page are tested at 2.0x on a 360x640dp phone in Marathi (and Hindi for the warning): no clipped or cut-off text and the primary button reachable. This found one real bug: the face letter grew with the text size and spilled out of the circle; it is now sized to the circle. Above 1.5x text, home gives each face its own row and puts Help under the date, so "Pankaj" no longer breaks into "Panka / j".

**Devanagari names (#8).** The person sheet has an optional "name in Marathi / Hindi letters" (e.g. पंकज). In mr/hi it is shown on faces, Confirm, Calling, Message and Help, said aloud, and the face letter is the whole cluster (प, श्री). Latin names are unchanged, English shows the Latin name, and voice matches either.

**Also:** Settings: "↑ Move up" has a word; "🗑 Remove <name>" sits on its own row, away from Edit; the 3 / 5 / 8 s choices are full-width buttons. The Marathi and Hindi message hints no longer say "Send बटण": "हिरवं पाठवा (➤) बटण दाबा" / "हरा भेजें (➤) बटन दबाएँ".

**Deferred:** the bar on Practice, Try it and permission cards (child-led two-button screens, where a Speak button would compete with the task), and a spoken "मदत" command.

**Tests:** 404 → 462 JVM tests. New `ui/UxSprint2UiTest` (29 acceptance checks), `ui/UxSprint2ScreenshotTest` (24 goldens in `roborazzi/ux2/`), `ui/LargeText.kt` (`assertNoClippedText`), and a Devanagari name check in `PersonSheetUiTest`. Updated on purpose: Calling (5 s, Cancel), Listening (Cancel), Settings (Remove label), UX sprint 1 money test (now opens the Money gone screen).

### Maid and caretaker (2026-11-11)
Setup can save a maid and a caretaker with the family (up to 6 people). The parent calls them by voice: “बाईला फोन लाव”, “maid ko phone lagao”, “नर्सला फोन लाव”, “call the maid”. Their role is written under the face. Two people with the same role are never dialled from the role word alone.


### UX sprint 1 (2026-11-11) — feat/ux-sprint-1
From the UX critique Pankaj asked for (`docs/ux-critique-2026-11-11.md`): the five-item sprint plus the India-only address fix and the shared bottom bar.

**Faces you can tell apart (#1).** `Faces.kt`: a contact photo when the phone has one (read through the picker's grant at pick time), or one the child picks with the system photo picker in the person sheet ("Add photo", no storage permission). Photos are copied as 256 px JPEGs into the app's private `faces/` folder, excluded from backup, never uploaded. Without a photo, each person gets one of 8 fixed colours by their place in the family list (white text at 4.5:1 or better on every colour, checked in a test), so Pankaj and Priya are no longer two identical blue "P"s. The full first name is written next to every face.

**Home on one screen (#2).** On a 360x640dp phone at default text size, home shows all 4 faces (2x2, each with its own Call), the labelled tiles (Is this safe?, Ride home, Near home, WhatsApp) and the sticky Speak bar, with no scrolling. Per-person WhatsApp buttons became one WhatsApp tile and then a face ("WhatsApp who?"), which is what makes 4 people fit. The day-3 / day-7 feedback card moved below the tiles, so it never pushes the family down. The "for family: hold 2 seconds" hint is gone from the parent's screen: the child holds the date, as the handover screen now says.

**"Didn't understand" (#3).** Shows "ऐकलं: …" (or "heard nothing") in a big panel with one example to say, says the same aloud ("मी ऐकलं: …. पण समजलं नाही. असं बोला: Pankajला फोन लाव"), puts a big Speak again first, then the people the words may have been about (a name heard comes first), each with a one-tap Call. The wrong "tap a photo" wording is gone in en/hi/mr.

**Scam verdict (#4).** "Ask <child>" is pinned above the bottom bar, so it is on screen with no scrolling on a small phone at 1.3x and 1.5x text, in Marathi, Hindi and English. The checked message is folded behind "Show the message". New: "Money already gone? Call 1930" (India's cyber-fraud helpline) opens the dial pad on warnings.

**Handoffs (#5).** One coaching line is said before Nexus hands over: "WhatsApp उघडतोय. हिरवं पाठवा बटण दाबा." (and SMS, Ask family, the dial pad when direct calling is off, Near home apps, and cab apps). Rides: Ola now gets its documented app link with the drop-off (`olacabs://app/launch`, drop_lat / drop_lng / drop_address), with the address also copied quietly as a backup because we have no Ola partner token. Rapido and Namma Yatri publish no destination link, so the paste stays, now with the steps said aloud ("“कुठे जायचे” दाबा, बोट दाबून ठेवा, मग पेस्ट दाबा"). Setup offers "Save <name> in phone contacts" for people typed in by number (the phone's own Contacts screen, prefilled with `ACTION_INSERT`; no WRITE_CONTACTS), so the dialer shows a name.

**India-only address search (#6).** The child's phone reported a US locale, so "Shivaji Park, Dadar" suggested a place in the US (screenshot 07). Suggestions now always ask ArcGIS for India (`countryCode=IND`), bias towards home when it is known, and drop anything that doesn't end in India. The phone's Geocoder searches inside India's bounding box for home and for ride destinations.

**Same bottom bar everywhere (#7).** `ParentScaffold`: मागे and बोला, each 64dp or more, at the bottom of Not understood, Is this safe? (input and verdict), Ride, Where to?, Which one?, Not found, and the Near home screens. The small Back links on those screens are gone.

**Heard → said back → doing → done.** Voice rides are read back ("घरी जायची गाडी. कॅब ॲप उघडायला मोठं बटण दाबा."), the handoff lines are the "doing", and home shows "Back in Nexus. If something went wrong, press Speak." after a handoff. The existing call and message countdowns and read-backs are unchanged.

**Docs:** `docs/design.md` gets section 3.8, the 19 design principles as the standard, with their status after this sprint. The critique is in `docs/ux-critique-2026-11-11.md`.

**Tests:** 359 → 404 JVM tests (after the leave-behind practice merge). New: `FacesAndPlacesTest` (colour contrast, same-initial faces differ, India-only URL and parsing with the recorded "Shivaji Park, Dadar" US result, the India box), `ui/UxSprint1UiTest` (19 acceptance checks on a 360x640dp device, including 1.3x and 1.5x text, mr and hi), and `ui/UxSprint1ScreenshotTest` (18 Roborazzi goldens in `roborazzi/ux1/`, including a re-take of screenshot 21). Existing tests were updated only where the behaviour changed on purpose (Ola link, feedback card position, pinned Ask, WhatsApp tile).

### Leave-behind practice (2026-11-11)
The child no longer hands the phone over after one sentence. After setup, the parent is asked, in their language, to say a call, a short message, and (if home is saved) "take me home". Each check passes only when the parser hears that action. Nothing is called, sent, or booked during the check. The result says how many worked and tells the child to stay if the call failed. Family settings can run it again. Phones already set up are not sent back through it.


### Privacy-first analytics (2026-11-11) — feat/analytics
Pankaj asked how we'll measure success and then "let's build it end to end". Definitions and caveats: `docs/metrics.md`.

**Added: an on-device event log.** `Analytics.kt`: `UsageEvent` has only enum, number and boolean fields (no text field exists, and `PrivacySchemaTest` fails the build if one is added). Events cover setup (start, steps, permissions, finish and duration), voice (start, end reason including cut-off, utterance length in characters, time to action, language, command type, recogniser error codes), "which one?", cancels, actions opened by app category, the time away after an action (a quick return under 5 s counts as a wrong action), Near home tiles, Play Store redirects, opens, and crashes (a count only). Stored as JSON lines in app storage with 90-day retention and a 50,000-event cap. Not in Android backup.

**Added: metrics maths.** `Metrics.kt` (pure Kotlin): days used per week, first-try success split by language and command (a retry = a new try within 30 s of a failed one), retry / cancel / wrong-action / cut-off rates, activation (a voice success within 24 h of setup), time to first value, setup completion and drop-off step, voice vs tile, D1 / D7 / D30, crash-free sessions. Every number carries its raw counts (`Ratio`).

**Added: "How Nexus is doing"** (Family settings, child scale, en/hi/mr). The numbers in plain words, a tester tag (family / friend / stranger), Share test summary (counts-only JSON through the share sheet), what is and isn't collected, and Delete all usage data (the log, daily counters, feedback answers, upload choice and install ID; people and places stay).

**Added: opt-in daily upload, off by default.** A consent page first. A random install ID (never the Android ID or advertising ID). WorkManager once a day on any network with exponential backoff. Only finished days, each sent once, as whole numbers under fixed keys. Our own Cloudflare Worker + D1 on workers.dev (`backend/metrics-worker/`, free tier): strict validation (anything unexpected is rejected), no IP or time of day stored, 180-day retention, reads behind a bearer token whose SHA-256 is the only thing the Worker holds. `scripts/metrics-report.mjs` prints the cohort table (kill rule, activation, first-try by language, retention) split by tester source.

**Skipped, on purpose:** "call connected 10 s" (needs READ_PHONE_STATE or the call log) and "other app in front for 60 s" (needs usage-access). Both are kept as time-away proxies and labelled as such.

**Changed:** the Privacy screen and PRIVACY.md now describe the opt-in counts. "Nothing is shared by itself" became "Nothing is shared without your yes".

**Tests:** 308 → 356 JVM tests (metrics maths, event log retention and cap, crash counter, schema and real-flow leak checks, consent-off-means-no-upload, WorkManager scheduling, delete wipes everything, the screens, and 6 screenshots in en/hi/mr), plus 11 Worker tests on Node.

### Real-phone feedback 1 (2026-11-10) — fix/real-phone-feedback-1
Pankaj tested the APK on his phone and reported three gaps. All three are fixed here.

**Fixed: the recorder cut off mid-message.**
- Root cause: `Listener` ran one SpeechRecognizer session per command. The recogniser ends a session at the first natural pause (often 1–2 s). The 8 s silence extra we set is documented as "may have no effect" and Google's service ignores it. On top of that, `onError` (usually ERROR_NO_MATCH or ERROR_SPEECH_TIMEOUT, even after words had shown) called `onDone(null)`, which threw away the partial words, and "Speak again" ignored partial results entirely.
- `Speech.kt`: a `SpeechEngine` interface (`AndroidSpeechEngine` creates a fresh recogniser for each session, with a watchdog for recognisers that go silent) and a `ContinuousListener`. It restarts after early cut-offs, joins every piece of text, never drops partial words, and recreates the recogniser on ERROR_RECOGNIZER_BUSY or ERROR_CLIENT. It stops on Done, on a whole command, after 3.5 s of quiet (command) or 4 s (dictation), and at 60 s. It never stops on quiet right after a joiner (आणि, की, and, ki, सांग…).
- Requests 5 hypotheses (`EXTRA_MAX_RESULTS`), and the parser runs over all of them. Also sets `EXTRA_LANGUAGE_PREFERENCE` (mr-IN, hi-IN or en-IN), `EXTRA_BIASING_STRINGS` with saved and phone-book names on Android 13+, and silence hints of 4 s / 3.5 s / 2 s.
- Listening screen: a big "Listening…" with the live words in large type, a big green "✓ Done / झालं / हो गया" and Stop. "Speak again" on the message screen and the feedback "Speak answer" work the same way.
- `Commands.kt`: an `IntentParser` seam (`RuleIntentParser`), so a smarter parser can replace the rules later.

**Added: call or message anyone in the phone book.**
- `READ_CONTACTS` is asked at first need, with a full-screen explanation in the parent's language, then Android's dialog. There is also an "Allow phone book" option in setup. The call-log permission is not requested.
- `Contacts.kt`: `ContactsSource` (`AndroidContactsSource` reads name, numbers, starred and times contacted), with the index held in memory only. Matching uses Devanagari and Latin sound keys, sets aside honorifics and family titles (ji, जी, saheb, kaka, tai, dada…; a matching title is a small bonus), and scores with Jaro-Winkler plus prefix and token alignment. Saved Nexus people always come first. Landlines are kept for calls.
- One clear exact match goes straight to the 3-second countdown. A clear but inexact match asks "Call X?". Close matches (two Rahuls), or one unsure match, show **"Which one? / यांपैकी कोण? / इनमें से कौन?"** with up to 3 big choices and the last 4 digits of each number. No match shows "I couldn't find X in your phone book" with Open the dial pad. Unsure matches are never auto-dialled.
- Dictated messages find unsaved recipients in the phone book too ("सुनीलला सांग की…"). If there are several, the message screen lists them.

**Added: rides to anywhere, by voice or by typing.**
- `Destination.kt`: `readDestination` understands "स्वारगेटला जायचं आहे", "मला दीनानाथ हॉस्पिटलला जायचं", "mujhe station jana hai", "Phoenix mall jaana hai rickshaw", "take me to Pune airport", "cab book kar Kothrud la" and more. "ghar jana hai" still goes home, and "office" uses the saved office. Calls, messages and Near home phrases are never read as rides.
- `PlaceFinder` (`AndroidPlaceFinder`: the Android Geocoder within about 50 km of home first, then up to about 100 km; Devanagari is retried in Latin). "Where to? / कुठे जायचं?" shows up to 3 places, each with a big "इथे जायचं / Go here", and reads the first back ("दीनानाथ मंगेशकर हॉस्पिटल, एरंडवणे. इथे जायचं?"). If nothing is found, the spoken words go to the cab app as the search text.
- Ride screen: "Go to X", with pickup from where the parent is now. Uber gets the drop-off coordinates plus `dropoff[nickname]`. Ola, Rapido and Namma Yatri get the address copied. Google Maps directions appear when no cab app is installed. A typed "📍 Somewhere else" works on every Ride screen, and the Ride tile works without a home address.

**Tests:** 308 JVM tests (up from 237): `ContinuousListenerTest` (16, with a fake recogniser and a fake clock), `ListeningUiTest` (4), `ContactsTest` (22), `ContactsUiTest` (10, fake contacts provider), `DestinationTest` (8, with 20 phrases and 14 guard phrases), `RideToUiTest` (8, fake Geocoder) and 3 new Roborazzi screenshot tests (listening in en, which contact in mr, destination confirm in mr), and the Ride home goldens were re-recorded for the new "Somewhere else" button.

### Voice architecture research (2026-11-10) — docs/voice-research
**Added**
- `docs/voice-architecture.md`: an evidence-based decision doc for voice. It finds the root causes of the mid-message cut-off: a single-shot SpeechRecognizer session, an ignored silence extra, and partial text discarded on error. It compares STT options (Google SpeechRecognizer, ML Kit GenAI, Sarvam Saaras v3, Chirp 3, Gemini audio, Whisper, IndicConformer, sherpa-onnx, Vosk, Bhashini) and picks rules first with an opt-in, text-only cloud LLM fallback (≈₹15–26 per 1,000 fallback calls). It also covers phone-book matching, Places-based destinations, elderly end-of-turn timings, phased remote help, and the Voice-60 test set and metrics.

### Near home (S15) (2026-11-10) — feature/near-home
**Added**
- Parent home has one "📍 Near home / घराजवळ / घर के पास" button. It opens up to 6 big need tiles in the parent's language (not an app grid): Milk & groceries (दूध-किराणा), Medicines (औषधे), Doctor (डॉक्टर), Rickshaw / cab (the existing Ride home flow), Home help & repairs (घरकाम / दुरुस्ती) and Emergency (आणीबाणी). The header shows "Services for 411045" when the home PIN is known.
- Child setup has a new step (setup is now 5 steps), also editable in Family settings: for each need, a dropdown of the top Indian apps. Each app shows a green "Installed" tag, or an "Install" action that opens its Play Store page (`market://details?id=`, falling back to the https Play page). The first installed app is preselected; "None" is allowed. The PIN comes from the address, then the phone's Geocoder postal code, or the child types it.
- Tiles: groceries and home help open the picked app's launcher intent. A picked app that's no longer installed shows a big "Install <app>" screen (plus "Ask <child>"). Medicines and Doctor open the picked app or Google Maps search around the home coordinates (`geo:lat,lng?q=medical store` / `doctor clinic`; the PIN or address when there are no coordinates; the Maps web URL when no app handles geo:). Emergency opens the dial pad with 108 (never an automatic call), family call buttons and the nearest hospital on Maps. Tiles with no picked app are hidden, except Medicines, Doctor and Emergency.
- Voice: "औषध पाहिजे", "dawai chahiye", "दूध मागव", "doodh mangao", "groceries", "डॉक्टर", "plumber", "electrician chahiye", "रिक्षा पाहिजे", "ambulance bulao" and more go straight to the tile. Calls, dictated messages and "ghar jana hai" are checked first, and sentences with सांग/bolo/tell/whatsapp are never read as a need.
- Usage counts per tile (near_grocery, near_medicine, near_doctor, near_ride, near_home_service, near_emergency) in Family settings and the export. Counts only, no app names or places.
- `SERVICE_CATALOG` (NearHome.kt): 21 apps, each Play package checked on 2026-11-10 (see docs/decisions.md). Manifest `<queries>` lists every package plus market: and geo: intents.
- Tests: 11 pure tests (`NearHomeTest`: catalog sanity, PIN parsing, 23 spoken phrases, 11 guard phrases, maps URIs, tile visibility, 108/112, export) and 15 Robolectric UI tests (`NearHomeUiTest`: Installed/Install tags, picking, market:// and the https fallback, setup preselect and PIN, Geocoder PIN, launch intent, Install screen, maps intents with home coordinates, 108 dial, voice routing, call/message/ride guards). 4 Roborazzi screenshots. 237 JVM tests in total.

**Fixed**
- Ola's package was `com.olacabs`, which is 404 on Play, so Ola never showed as installed. It's now `com.olacabs.customer`. Namma Yatri (`in.juspay.nammayatri`) has been added to the cab apps.

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
