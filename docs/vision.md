# Nexus: final vision and phase plan (2026-11-10)
Base: commit 7fd6d17 (android/, package dev.pankajkhannade.nexus, ~1,270 lines Kotlin: Actions.kt, Model.kt, Plan.kt, HomeScreen.kt, SetupScreen.kt, Store.kt).
Teardown: evaluation-2026-11-10.md. Design: design.md.

## Vision (one line)
Nexus is the family co-pilot for your parents' Android phone: they speak in their own language and the simple thing happens; before anything risky, it pulls you in, and nothing leaves the phone without their say.

- First user (buyer): a working adult in a metro (25-40) whose parents (60+) live elsewhere and phone them for help. Daily user: the parent.
- Core problem: parents can't do simple phone tasks alone, so children become remote tech support and fraud guards. Evidence: HelpAge India 2025, 66% of elders find tech confusing, 51% fear errors, 44% are embarrassed to ask again; seniors were 4.3% of 2025 cyber-fraud complaints but 18% of losses (Rs 4,005 cr); RBI April 2026 discussion paper proposes a "trusted person" approval for 70+ transfers above Rs 50,000.
- Why Google won't do it for free (inference): Google's agentic features target the device owner on flagship phones (screen automation: Pixel 10/11, Galaxy S26, select apps, English). The family relationship across two phones and two households is not a single-account feature. Scam Detection for calls in India is Pixel 9+ only.
- What an indie app can't do: drive other apps autonomously (Play Accessibility policy bans autonomous agents unless the app is an accessibility tool), call other apps' AppFunctions (EXECUTE_APP_FUNCTIONS is internal|privileged), or read SMS/call logs unless it's the default handler. Nexus therefore stays within: launcher/home role, Share target, call-screening role (ROLE_CALL_SCREENING), its own on-device model, and links to the child.
- Top failure risks: (1) parents don't adopt a new app; the child installs it and the parent never opens it. (2) Google or the UPI apps ship the family safety layer on all Androids (UPI Circle exists, scam detection spreads beyond Pixel), and Nexus has no technical moat.

## Status / what's built so far (2026-11-10)

Phase 1 (S1–S13) is built on main: the teardown bug fixes, parent mode, child-assisted setup, voice in English, Hindi and Marathi, calls, ride home (Uber, Ola, Rapido, Namma Yatri), "Is this safe?", "Ask family", family settings with usage export, day-3 / day-7 feedback, and the Privacy screen. 237 JVM tests pass (about 20 s warm). A debug APK is ready to sideload. There is no Play Store release yet.

Phase 1.5, added at Pankaj's request after Phase 1:
- **S14** voice-dictated WhatsApp and SMS messages (PR #6)
- **S15** Near home tiles, with a child-picked app dropdown (Installed / Install from Play) (PR #7)

Still ahead: Pankaj's real-phone test, a native Hindi and Marathi review of the strings, release prep (signed AAB, upload key Pankaj keeps, privacy policy URL, Data safety form), and the 14-day Play closed test with 12 testers (6 parent-child pairs). The Play Console fee needs Pankaj's yes.

Quick-commerce ordering by voice (Blinkit, Instamart, Zepto placing an order or paying) is deferred: none of those apps has a public ordering API, and Play Accessibility policy blocks driving other apps on the user's behalf. Near home opens the chosen app instead (or Maps, or the Play Store).

## Phase 1: Fix the core and test the family wedge (build 2-3 weeks, then 14-day Play closed test)
Goal: a working, Play-installable app that 6 parent-child pairs (12 testers) use for 14 days.
User outcome: the parent taps or says "call Pankaj" in Marathi/Hindi/English and it works. When a message looks off, they share it to Nexus, see a plain-language warning, and tap once to ask their child.
Features / work (checklist):
- [x] Fix: phone number normalisation (Model.kt international(): 10-digit IN numbers starting 91 lose the country code; leading 0) + unit tests
- [x] Fix: Edit-screen contact saved raw without the country code (HomeScreen.kt) so wa.me fails; route all saves through one normaliser
- [x] Fix: "pick me up at home" sets home as drop-off (Plan.kt); pickup vs drop-off parsing + tests
- [x] Fix: CALL regex ("Call me at" -> contact search; "Aai ko call karo" -> "karo"); add Hindi/Marathi/Hinglish call patterns ("Aai la phone kar", "Pankaj ko phone lagao") + tests
- [x] Fix: cab handoff: store lat/lng at setup; Uber link with documented dropoff params; Ola/Rapido (and now Namma Yatri) open the app + copy the address to the clipboard with a toast (no undocumented params). Real-phone test still outstanding.
- [x] Parent mode: a big-button home screen with up to 4 people (photo/initial, call, WhatsApp), large text, one primary action per screen
- [x] Voice in the parent's language: Android SpeechRecognizer with hi-IN / mr-IN / en-IN, picked at setup; spoken confirmation via TextToSpeech
- [x] Child-assisted setup: the child fills in the parent's people and language on the parent's phone in under 3 minutes (no accounts, no server)
- [x] "Is this safe?" Share target: shared text or screenshot -> on-device rule checks (links, OTP/PIN/KYC/"account blocked", UPI collect/"receive money", urgency, unknown numbers) -> plain warning in the parent's language. Use ML Kit Prompt API (Gemini Nano) for a one-line explanation only where checkStatus()==AVAILABLE; rules are the floor
- [x] "Ask family" button: one tap opens WhatsApp to the chosen child with the shared content + "Is this real?" prefilled (the parent sends; nothing automatic)
- [x] Local usage counters (opens, calls, voice commands, safety checks, ask-family taps, rides, WhatsApp opens, dictated messages and Near home tiles per day) on the phone + an "Export my usage" share button for day-7/day-14 check-ins
- [x] Day-3 / day-7 in-app feedback prompt (2 questions, stays local unless shared)
- [ ] Play release: signed AAB, upload key Pankaj keeps, privacy policy page (no data collected), Data safety form, closed testing track with 12 opted-in testers for 14 days [Play Console fee needs Pankaj's yes]
- Out of scope: accounts, servers, accessibility service, SMS/call-log permissions, call screening, payments
Exit metric: 12 testers stay opted in for 14 days (Play's production gate). In week 2, at least 4 of 6 parents use Nexus on 4+ of 7 days. At least 1 "Is this safe?" or "Ask family" per pair over the test. At least 3 of 6 children say they'd pay Rs 999/yr (deposit ask only with Pankaj's yes).
Kill rule: fewer than 2 of 6 parents use it on 4+ days a week -> the parent wedge is dead; return to discovery.

## Phase 1.5: Dictated messages and Near home (added 2026-11-10, at Pankaj's request)
Built on main after Phase 1, before the closed test, so testers get both.
- [x] S14 Dictated WhatsApp and SMS messages: the parent says who, which app and what; Nexus rewrites the wording on the phone, reads it back, and opens WhatsApp or SMS with the text typed in. The parent taps Send there. No SEND_SMS permission.
- [x] S15 Near home: six need tiles (groceries, medicines, doctor, ride, home help, emergency) in the parent's language. The child picks an app for each need from a dropdown of top Indian apps (an "Installed" tag, or Install opens the Play Store). Medicines, Doctor and Emergency always work through Maps search near home and a 108 dial pad. Voice jumps straight to a tile ("औषध पाहिजे").
- Quick-commerce ordering by voice is deferred (no public APIs; Accessibility automation is blocked). Near home opens the apps instead.

## Phase 2: Child companion and daily habit (6-8 weeks)
Goal: the child can help without a phone call; the parent has a reason to open Nexus every day.
User outcome: the child adds a contact, reminder or shortcut to the parent's phone from their own phone, and sees a weekly "all fine" note. The parent gets medicine, bill and recharge reminders read aloud.
Features: child app mode + pairing by QR; end-to-end encrypted sync of config only (contacts, reminders, shortcuts), with no content, location or usage leaving the phone unless the parent shares it; reminders with voice; "explain this screen" via screenshot share (on-device where supported, else rules + Ask family); optional Nexus as default home app for the parent; Hinglish/Marathi command coverage measured from exported logs; first paid plan test (Rs 999/yr per family, hypothesis).
Exit metric: 30 pairs; parent week-4 retention of 40% or more (4+ days a week); 10 families paying.

## Phase 3: Risky-moment guard (2-3 months)
Goal: catch the moments where elders lose money, within Play policy.
User outcome: an unknown caller rings -> the parent sees "Unknown number. Don't share OTP. Call Pankaj?"; the child gets a heads-up when the parent agrees to share alerts.
Features: CallScreeningService via ROLE_CALL_SCREENING (caller warning, no call log read); on-device scam classifier for shared messages (Gemma 4 E2B via LiteRT-LM on 6-8 GB phones, rules elsewhere); guided UPI Circle / small-balance account setup; "pause before you pay" checklist; incident log the family can share with the bank or cybercrime.gov.in / 1930.
Exit metric: 200 paying families; call-screening declaration approved by Play; at least 5 user-reported "Nexus stopped this" stories with evidence; support under 10 min per family per month.

## Phase 4: Trusted-person layer and distribution (6-12 months)
Goal: become the family's trusted-person app as the rules land.
Features: if RBI finalises trusted-person approval, partner with a bank/TPAP as the approval surface; multi-elder families; Marathi/Tamil/Bengali voice packs (Sarvam Edge-class on-device ASR/TTS if licensable); distribution via insurers, senior communities, OEM preload.
Exit metric: Rs 10 lakh ARR or one signed bank/OEM partnership.

## What we stop
The commuter/cab launcher as the headline (keep "book a ride home" as a parent action), general assistant chat, cross-app automation, any server-side data.
