# Nexus

**Nexus is the family co-pilot for your parents' Android phone.** They speak in their own language and the simple thing happens. Before anything risky, Nexus pulls you in, and nothing leaves the phone without their say.

The child sets Nexus up on the parent's phone in a few minutes. The parent uses it every day: tap a face or say "Pankaj la phone lav" to call, share a strange message to Nexus to ask "Is this safe?", and send it to family with one tap before acting on it.

> Status: Phase 1 in progress. The native Android app lives in [`android/`](android/). A debug APK is built for testing; there is no Play Store release yet.

## Docs

| Doc | What it covers |
|---|---|
| [docs/vision.md](docs/vision.md) | The vision, who it's for, why it can win, and the four phases |
| [docs/design.md](docs/design.md) | Critical UI evaluation and the parent-mode revamp: screens, strings (en/hi/mr), design system |
| [docs/evaluation-2026-11-10.md](docs/evaluation-2026-11-10.md) | Teardown of the original app: the bugs Phase 1 fixes first |
| [docs/decisions.md](docs/decisions.md) | Decisions log with reasons |
| [docs/plan.md](docs/plan.md) | Living plan: assumptions, milestones, kill rules |
| [CHANGELOG.md](CHANGELOG.md) | What changed, by milestone |

## Build the Android app

Requirements: JDK 17+, Android SDK (platform 35, build-tools 35). The Gradle wrapper (8.9) is committed.

```bash
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties   # if not already present
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Package: `dev.pankajkhannade.nexus`. See [android/README.md](android/README.md) for sideload notes.

## Privacy

People, places and settings are stored only on the phone and excluded from Android backup. See [PRIVACY.md](PRIVACY.md).

## Legacy web prototype

The repository root also holds the earlier web prototype (`src/`, `npm run dev`). It is no longer the product direction; the Android app is.
