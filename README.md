# Nexus

**Nexus is the family co-pilot for your parents' Android phone.** They speak in their own language and the simple thing happens. Before anything risky, Nexus pulls you in, and nothing leaves the phone without their say.

The child sets Nexus up on the parent's phone in a few minutes. The parent uses it every day.

**Global, language-pluggable.** Nexus is built to work in every Indian language and then in other countries. Today it speaks Marathi, Hindi and English in India, starting with Marathi-speaking parents in Pune. New languages and countries are meant to arrive as packs, not rewrites; see [docs/i18n-architecture.md](docs/i18n-architecture.md).

> Status: Phase 1 is built (screens S1–S13, plus S14 dictated messages and S15 Near home), with Pankaj's first real-phone feedback fixed (voice cut-off, phone-book calling, rides anywhere). A debug APK is ready to sideload. There is no Play Store release yet. Next: a real-phone test, a native Hindi and Marathi review of the strings, then the 14-day Play closed test (the Console fee needs Pankaj's yes).

The native Android app lives in [`android/`](android/). Package: `dev.pankajkhannade.nexus`.

## What it does today

- **Parent mode:** one screen with no scrolling on a small phone: up to four faces you can tell apart (a photo, or a distinct colour with the full first name), each with its own Call, labelled tiles (Is this safe?, Ride home, Near home, WhatsApp) and a sticky Speak bar. Every other parent screen ends with the same मागे / बोला bar.
- **Says what it heard and what happens next:** "Didn't understand" shows and says "ऐकलं: …" with an example and one-tap calls; before handing over to WhatsApp, the dialer or a cab app, Nexus says where to press ("हिरवं पाठवा बटण दाबा").
- **Listening that doesn't cut off:** Speak keeps listening through pauses, shows the words live in large type and has a big Done / झालं. It stops on its own once a whole command is heard, or after a short quiet spell.
- **Calls:** tap a face or say "Pankaj la phone lav" / "पंकज को फ़ोन लगाओ". Nexus counts down (3, 5 or 8 seconds, set in Family settings; 5 by default) with a big ✕ रद्द करा, then places the call (or opens the dialer if call permission is off).
- **Anyone in the phone book:** "सुनीलला फोन लाव" finds Sunil Patil in the phone's contacts, once the phone book is allowed. Two Rahuls show a big "Which one?" with each number's last digits. Contacts stay on the phone.
- **Dictated WhatsApp and SMS:** say "पंकजला व्हॉट्सॲप कर आणि सांग की तू कधी येणार आहेस घरी". Nexus tidies the message on the phone, reads it back, and opens WhatsApp or SMS with the text typed in. The parent taps Send there. Nothing is sent automatically.
- **Rides home or anywhere:** one big button for the last cab app used. Uber, Ola, Rapido and Namma Yatri are supported. Uber and Ola get the drop-off in the link; Rapido and Namma Yatri get it copied, with the paste steps said aloud. "ghar jana hai" / "घरी जायचं आहे" goes home. "स्वारगेटला जायचं आहे" / "take me to Pune airport" finds the place near home, reads it back and opens the cab app with the drop-off. You can also type "Somewhere else". With no cab app installed, it offers "Ask family" (and Maps directions for a place).
- **Near home:** one "घराजवळ" button with big tiles for groceries, medicines, doctor, ride, home help and emergency. The child picks an app for each need from a dropdown. Apps already on the phone get an "Installed" tag; the rest open their Play Store page. Medicines, doctor and emergency always work, through Maps search near home and a 108 dial pad (never an automatic call).
- **Help and "did it work?":** home always has "🙋 मदत: Pankaj ला विचारा" (call or message the child). Back from a call, WhatsApp, a ride or Maps, home asks "फोन लागला का?" with हो / नाही; नाही opens Help.
- **पैसे गेले (money already gone):** four one-tap steps: call 1930, call the bank, complain at cybercrime.gov.in, tell the child. Dial pads and a browser link only; no new permissions.
- **Is this safe?:** share a message or screenshot into Nexus. Checks run on the phone. The wording never says "safe". "Ask <child>" stays on screen at large text, and "पैसे गेले? आता काय करायचं" opens the money-gone steps.
- **Ask family:** one tap opens WhatsApp (or SMS) to the chosen child with the shared content and "Is this real?" already typed. The parent sends.
- **Child-assisted setup:** language, family (with a face photo, an optional Devanagari name, and "Save in phone contacts" for numbers typed in), home address with the PIN code as one question (India-only suggestions), then mic and call permission only if they are not on yet. The step count is true ("Step 2 of 3" when permissions are already on). Near home apps are picked automatically and editable later. No accounts.
- **Family settings:** edit people and home (with Undo), the wait before a call (3 / 5 / 8 s), test the mic, pick Near home apps, and **Export my usage** (a preview first; counts only, no names or numbers).
- **How Nexus is doing (Family settings):** the numbers that decide whether Nexus works, in plain words in en/hi/mr with raw counts next to every percentage: days used of the last 7, first-try voice success by language and command, retries, cancels, wrong app, cut-offs, setup time, activation, voice vs buttons, crashes. A tester tag (family / friend / stranger), **Share test summary** (counts-only JSON for WhatsApp), and **Delete all usage data**. See [docs/metrics.md](docs/metrics.md).
- **Opt-in daily counts (off by default):** after a consent page, yesterday's aggregate counts go once a day to our own Cloudflare Worker under a random install ID. No words, names, numbers or places, ever. Source and tests in [`backend/metrics-worker/`](backend/metrics-worker/); `node scripts/metrics-report.mjs` prints the cohort table.
- **Day-3 / day-7 feedback:** two short questions on the home screen. Answers stay on the phone unless they are shared.
- **Privacy:** five clear cards in en/hi/mr. See [PRIVACY.md](PRIVACY.md).

## Build and test

Requirements: JDK 17+, Android SDK (platform 35, build-tools 35). The Gradle wrapper (8.9) is committed.

```bash
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties   # if not already present
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # 404 JVM tests: logic + Robolectric Compose UI tests (~20 s warm)
./gradlew verifyRoborazziDebug   # screenshot tests against app/src/test/roborazzi/ goldens
```

The metrics Worker has its own Node tests: `cd backend/metrics-worker && npm test` (11 tests, under 1 s, real SQL on Node's built-in SQLite).

UI checks run on the JVM first (Robolectric + Roborazzi). The emulator is only a short pre-release smoke of 10 items or fewer. See [docs/testing.md](docs/testing.md).

## Sideload the debug APK

1. Build the APK with `./gradlew assembleDebug`, or use the debug APK shared in chat.
2. Copy `android/app/build/outputs/apk/debug/app-debug.apk` to the phone (or run `adb install -r app/build/outputs/apk/debug/app-debug.apk`).
3. Open the file on the phone. If Android blocks it, allow installs from Files or Chrome for this one app.
4. A newer debug build installs over an older Nexus already on the phone. This is a debug APK, not a Play upload.

## Docs

| Doc | What it covers |
|---|---|
| [docs/vision.md](docs/vision.md) | The vision, who it's for, why it can win, the four phases, and what's built so far |
| [docs/design.md](docs/design.md) | Critical UI evaluation and the parent-mode revamp: screens S1–S15, strings (en/hi/mr), design system, and the 19 design principles (3.8) |
| [docs/ux-critique-2026-11-11.md](docs/ux-critique-2026-11-11.md) | UX critique: journey maps, 20 ranked fixes, principles, the sprint plan |
| [docs/evaluation-2026-11-10.md](docs/evaluation-2026-11-10.md) | Teardown of the original app: the bugs Phase 1 fixed first |
| [docs/decisions.md](docs/decisions.md) | Decisions log with reasons |
| [docs/plan.md](docs/plan.md) | Living plan: status, milestones, exit and kill rules |
| [docs/testing.md](docs/testing.md) | Fast-testing playbook: JVM first, emulator last |
| [docs/metrics.md](docs/metrics.md) | Success metrics: definitions, targets, data sources, caveats, the event schema, the opt-in upload, check-in and survey drafts |
| [docs/i18n-architecture.md](docs/i18n-architecture.md) | Global plan for languages and countries: where language and India are hard-coded today, the language-pack and region-pack shape, effort per language and per country |
| [docs/voice-architecture.md](docs/voice-architecture.md) | Voice research and decision: STT options, rules + opt-in LLM fallback, phone-book matching, destinations, elderly voice UX, remote help, test plan |
| [CHANGELOG.md](CHANGELOG.md) | What changed, by milestone |
| [PRIVACY.md](PRIVACY.md) | What is stored, what leaves the phone, and only when |

## Privacy

People, places, the home pincode, settings and the usage log are stored only on the phone and excluded from Android backup. The only thing that can leave the phone on its own is the opt-in daily counts, and only after the family says yes. See [PRIVACY.md](PRIVACY.md).

## Legacy web prototype

The repository root also holds the earlier web prototype (`src/`, `npm run dev`). It is no longer the product direction; the Android app is.
