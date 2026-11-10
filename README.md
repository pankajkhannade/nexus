# Nexus

**Nexus is the family co-pilot for your parents' Android phone.** They speak in their own language and the simple thing happens. Before anything risky, Nexus pulls you in, and nothing leaves the phone without their say.

The child sets Nexus up on the parent's phone in a few minutes. The parent uses it every day.

> Status: Phase 1 is built (screens S1–S13, plus S14 dictated messages and S15 Near home). A debug APK is ready to sideload. There is no Play Store release yet. Next: a real-phone test, a native Hindi and Marathi review of the strings, then the 14-day Play closed test (the Console fee needs Pankaj's yes).

The native Android app lives in [`android/`](android/). Package: `dev.pankajkhannade.nexus`.

## What it does today

- **Parent mode:** a big-button home screen in English, Hindi or Marathi, with up to four people (photo or initial, Call, WhatsApp) and a sticky Speak bar.
- **Calls:** tap a face or say "Pankaj la phone lav" / "पंकज को फ़ोन लगाओ". Nexus counts down for 3 seconds with a Cancel, then places the call (or opens the dialer if call permission is off).
- **Dictated WhatsApp and SMS:** say "पंकजला व्हॉट्सॲप कर आणि सांग की तू कधी येणार आहेस घरी". Nexus tidies the message on the phone, reads it back, and opens WhatsApp or SMS with the text typed in. The parent taps Send there. Nothing is sent automatically.
- **Ride home:** one big button for the last cab app used. Uber, Ola, Rapido and Namma Yatri are supported. Saying "ghar jana hai" / "घरी जायचं आहे" / "take me home" opens it. With no cab app installed, it offers "Ask family".
- **Near home:** one "घराजवळ" button with big tiles for groceries, medicines, doctor, ride, home help and emergency. The child picks an app for each need from a dropdown. Apps already on the phone get an "Installed" tag; the rest open their Play Store page. Medicines, doctor and emergency always work, through Maps search near home and a 108 dial pad (never an automatic call).
- **Is this safe?:** share a message or screenshot into Nexus. Checks run on the phone. The wording never says "safe".
- **Ask family:** one tap opens WhatsApp (or SMS) to the chosen child with the shared content and "Is this real?" already typed. The parent sends.
- **Child-assisted setup:** language, family, home address, Near home apps, and a mic try-out, in a few minutes. No accounts, no server.
- **Family settings:** edit people and home (with Undo), test the mic, pick Near home apps, and **Export my usage** (a preview first; counts only, no names or numbers).
- **Day-3 / day-7 feedback:** two short questions on the home screen. Answers stay on the phone unless they are shared.
- **Privacy:** five clear cards in en/hi/mr. See [PRIVACY.md](PRIVACY.md).

## Build and test

Requirements: JDK 17+, Android SDK (platform 35, build-tools 35). The Gradle wrapper (8.9) is committed.

```bash
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties   # if not already present
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # 237 JVM tests: logic + Robolectric Compose UI tests (~20 s warm)
./gradlew verifyRoborazziDebug   # screenshot tests against app/src/test/roborazzi/ goldens
```

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
| [docs/design.md](docs/design.md) | Critical UI evaluation and the parent-mode revamp: screens S1–S15, strings (en/hi/mr), design system |
| [docs/evaluation-2026-11-10.md](docs/evaluation-2026-11-10.md) | Teardown of the original app: the bugs Phase 1 fixed first |
| [docs/decisions.md](docs/decisions.md) | Decisions log with reasons |
| [docs/plan.md](docs/plan.md) | Living plan: status, milestones, exit and kill rules |
| [docs/testing.md](docs/testing.md) | Fast-testing playbook: JVM first, emulator last |
| [CHANGELOG.md](CHANGELOG.md) | What changed, by milestone |
| [PRIVACY.md](PRIVACY.md) | What is stored, what leaves the phone, and only when |

## Privacy

People, places, the home pincode and settings are stored only on the phone and excluded from Android backup. See [PRIVACY.md](PRIVACY.md).

## Legacy web prototype

The repository root also holds the earlier web prototype (`src/`, `npm run dev`). It is no longer the product direction; the Android app is.
