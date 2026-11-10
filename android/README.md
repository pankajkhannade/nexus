# Nexus for Android

Native app. The product direction is the family co-pilot for parents' phones (see `../docs/vision.md`). The sections below describe the current build.

## Build

```bash
./gradlew assembleDebug testDebugUnitTest
```

This is the Play-eligible design. It does not read other apps' screens. Google Play rejects accessibility services used to scrape cab fares, and those apps do not share live prices.

## Install the test build

1. Download `Nexus.apk` from the chat.
2. Open the file on the phone.
3. If Android blocks it, allow installs from Chrome or Files for this one app.
4. Open Nexus. The first launch asks for country, home, office, and at least one person.

The test file is a debug APK. It is not the file you upload to Play.

## What it does

- Go home or go to the office, then open Uber, Ola, Rapido, or Maps. Only the app you tap is opened.
- The last cab app you used is offered first next time.
- Tap a saved person to call, text, or WhatsApp. The dialer still places the call.
- Type or speak “call Aai” or “pick me up at home”.
- Share text into Nexus from another app.
- Find a name in contacts only after an in-app explanation and the Android permission.
- Light theme by default, with a dark switch.

## Publish later

Play Console needs a Google account and a one-time fee. Upload an Android App Bundle signed with a key you keep, not this debug APK. Host the privacy note at a public URL. The text is in `PRIVACY.md` at the repository root. In the store listing, declare contacts (only after the user agrees) and that place search leaves the phone. Do not declare an accessibility service.
