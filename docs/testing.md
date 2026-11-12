# Nexus testing playbook

Last updated: 10 Nov 2026 (IST). Owner: whoever is building Nexus. Rule: **fast tests first, always.**
The emulator is the last step, never the inner loop.

## Why this changed

Until now every UI check meant a software-only emulator on the build box (no KVM, `-accel off`):
about 10 minutes to boot, then several seconds per adb tap and screenshot. Long runs kept getting
cut off. Most of those checks were "does this screen show the right thing and fire the right
intent". That runs on the JVM with Robolectric in well under a second per test.

## Measured on the build box (8 vCPU, shared with a running emulator, load avg about 10)

| Run | Wall clock |
| --- | --- |
| Software emulator boot alone (before any test) | about 10 min |
| Cold: fresh Gradle daemon, `clean`, no build/config cache, all 134 tests | 35 s |
| Warm: edit one test file, run only that class (`--tests '*CallingUiTest'`) | 7.5–7.9 s |
| Warm: edit app code, run one UI class (`--tests '*SafetyUiTest'`) | 7.9–8.9 s |
| Warm: edit one test file, run all 134 tests | 12.6–13.0 s |
| Warm: force re-run of all 134 tests (`--rerun`) | 12.3–13.1 s |
| Warm: edit app code, verify the 3 Roborazzi screenshots | 13.7–14.3 s |
| Warm: pure-logic classes only (`SafetyTest`, `PhoneTest`) | about 1 s |
| Nothing changed (up to date) or `clean` with build-cache hits | under 1.1 s |

Where the time goes: each Robolectric JVM fork pays about 8 s once to start its Android sandbox.
After that, a Compose UI test takes 0.2–0.6 s and a screenshot about 0.1–0.3 s. Pure-Kotlin tests
take milliseconds. So adding tests is cheap. Starting new forks is what costs time.

## The layers

Based on Android's 5-layer pyramid (developer.android.com/training/testing/fundamentals/strategies).
Use the lowest layer that can catch the bug.

| Layer | What | Where it runs | Tool | Target time |
| --- | --- | --- | --- | --- |
| L0 Unit | Parsing, phone rules, scam rules, plan/usage logic | JVM | JUnit4 (`app/src/test/.../*Test.kt`) | ms per test, about 1 s per run |
| L1 Component/feature UI | A screen renders, validates, counts down, fires the right intent | JVM | Robolectric + Compose `createComposeRule` (`app/src/test/.../ui/*UiTest.kt`) | 0.2–0.6 s per test after an 8 s fork start |
| L2 Screenshot | Layout, colours, large text, Devanagari in en/hi/mr | JVM | Roborazzi on Robolectric Native Graphics (`ui/ScreenshotTest.kt`) | about 0.3 s per shot |
| L3 Device smoke | Only what the JVM can't fake (list below) | Emulator, or better a real phone | adb script now; Espresso/UiAutomator or Maestro later | 10 checks at most, once before a tester build |
| L4 Real world | Real Uber/Ola/Rapido/WhatsApp, real voices, real parents | Pankaj's phone, the 12 Play closed-test phones | Manual + the in-app usage export | Before each tester round |

### What runs when

| Trigger | Command | Expected warm time |
| --- | --- | --- |
| Every edit (inner loop) | `./gradlew :app:testDebugUnitTest --tests '*ThingYouTouched*'` | under 10 s |
| Before every commit/push | `./gradlew :app:testDebugUnitTest` then `./gradlew :app:verifyRoborazziDebug` | about 15 s each |
| UI changed on purpose | `./gradlew :app:recordRoborazziDebug`, look at the PNGs, commit them with the change | about 15 s |
| Before opening or updating a PR | the two above plus `./gradlew :app:assembleDebug` | under 1 min |
| Before a tester or Play build | L3 smoke on the emulator or a real phone, then L4 | emulator: boot it **in the background at the start of the session**, never inside a long test run |

`./gradlew test` is fine as well. Release-variant host tests are switched off, so it only runs
debug once.

## Gradle setup (already in the repo)

`android/gradle.properties`
```properties
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
org.gradle.parallel=true
# Reuse task outputs (FROM-CACHE) and the configured task graph between runs.
org.gradle.caching=true
org.gradle.configuration-cache=true
```

`android/build.gradle.kts`
```kotlin
id("io.github.takahirom.roborazzi") version "1.60.0" apply false
```

`android/app/build.gradle.kts`
```kotlin
plugins { /* ... */ id("io.github.takahirom.roborazzi") }

android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true            // strings (en/hi/mr), theme, manifest
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")   // real Android graphics on the JVM
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
                it.maxHeapSize = "2g"
            }
        }
    }
}

dependencies {
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")   // hosts createComposeRule's activity
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.60.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.60.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.60.0")
}

androidComponents {   // host tests on debug only
    beforeVariants(selector().withBuildType("release")) { v ->
        (v as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = false
    }
}
```

**Version pins.** Nexus is on AGP 8.7.3 and Kotlin 2.0.21. Roborazzi 1.61 and later are built with
Kotlin 2.3 metadata, which Kotlin 2.0 can't read. 1.60.0 is the newest version that compiles with
our Kotlin. When we move to Kotlin 2.3 or later, bump Roborazzi to the latest (1.78.0 on 8 Nov 2026).
Robolectric 4.16 covers SDK 23–36, which includes our compileSdk/targetSdk 35 on JDK 17. Running
tests against SDK 36 would need JDK 21.

## How to write a Nexus UI test

Helpers live in `app/src/test/java/dev/pankajkhannade/nexus/ui/NexusTestSupport.kt`.

```kotlin
@RunWith(RobolectricTestRunner::class)
class ParentHomeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsAllFourPeople() {
        val model = newModel(people = 4)                    // real ViewModel, fixtures, pinned date
        compose.setParent { ParentHome(model, {}, {}) }    // NexusTheme(Parent) around the screen
        compose.onNodeWithContentDescription("Call Aai").performScrollTo().assertIsDisplayed()
    }
}
```

Conventions:

1. **Find nodes the way a parent or TalkBack does:** by visible text or `contentDescription` taken
   from our string resources. This also checks the accessibility labels. Add `Modifier.testTag`
   only for nodes with no text, like the countdown ring. Name tags `screen_element`, for example
   `calling_ring`. If UiAutomator or Maestro ever need them, turn on `testTagsAsResourceId` at the
   root.
2. **Always `performScrollTo()` before `performClick()`** on parent screens. They're tall, and a
   click on an off-screen node silently does nothing.
3. **Time is virtual.** For the 3-second call countdown, use `compose.mainClock.autoAdvance = false`
   and then `advanceTimeBy(1_000)`. Never use `Thread.sleep`.
4. **Pin anything that changes:** the date (`FIXED_DAY` via `model.dayOffset`), people and home.
   `newModel()` clears saved people and home first. Robolectric reuses one DataStore singleton per
   JVM fork, so without that, state leaks between tests.
   `resetLocalDataForTest()` also resets the saved language, the country (India) and one-time hints
   such as `messageExplained`. Before that, a test calling `finishSetup` in English made later Marathi
   TTS checks hear "Calling Pankaj", depending on which fork ran first. Add new saved one-time flags there too.
5. **One behaviour per test, with a name a non-engineer can read**, for example
   `stopDuringCountdownCancelsTheCall`.
6. **Locale:** use `@Config(qualifiers = "+mr")` or `"+hi"` to render in Marathi or Hindi. Remember
   that TTS speaks in the *parent's* language (`saved.language`, default `mr`), not the test JVM's
   locale. A test caught this.

### Fakes for the things Nexus hands off to Android

| Thing | JVM fake | Example |
| --- | --- | --- |
| Intents to other apps (dialer, call, WhatsApp, SMS, Play Store, cab apps, Maps) | Nexus starts activities through the application context, so read `shadowOf(app).nextStartedActivity`. Nothing launches. Assert the action, package and data URI. | `CallingUiTest`, `ParentHomeUiTest.whatsAppButton…`, `SafetyUiTest.askFamily…` |
| "Is app X installed?" (`isInstalled`) | `fakeInstall("com.whatsapp")` registers a MAIN/LAUNCHER activity in `ShadowPackageManager` | same |
| Runtime permissions (CALL_PHONE, RECORD_AUDIO) | `shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)`. Leave it ungranted to test the fallback. | `CallingUiTest.withCallPermission…` |
| Text to speech | Robolectric `ShadowTextToSpeech`: call `onInitListener.onInit(SUCCESS)`, then check `lastSpokenText` | `CallingUiTest.theCountdownIsSpokenAloud…` |
| Speech recognition | `FakeSpeechEngine` (test sources) implements the `SpeechEngine` seam. Pass it with `model.useSpeechEngine(fake) { fake.clock }`, then play what Android would send: `fake.partial("…")`, `fake.endOfSpeech()`, `fake.error(SpeechRecognizer.ERROR_NO_MATCH)`, `fake.final("best", "second")` (n-best). Check `fake.starts` for restarts and move the fake clock with `fake.advance(ms)` to test quiet-time and 60 s limits without waiting. For phrase-only checks, call `model.understood("Aai la phone lav", typed = true)`. | `ContinuousListenerTest`, `ListeningUiTest` |
| Phone book (contacts) | `FakeContacts` implements `ContactsSource`, with the `PHONE_BOOK` fixtures (two Rahuls, a Devanagari contact, titles, landlines, a duplicate number). Set `model.contacts = FakeContacts(granted = false)` for the permission path, and `model.io = Dispatchers.Unconfined` so lookups finish inside the test. | `ContactsTest`, `ContactsUiTest` |
| Place search (destinations) | Set the `PlaceFinder` seam: `model.placeFinder = PlaceFinder { query, near, lang -> listOf(Place("Swargate Bus Stand", 18.50, 73.86)) }`, plus `model.io = Dispatchers.Unconfined`. | `RideToUiTest` |
| ML Kit OCR (screenshot → text) | Don't run it on the JVM: the models are native. Test the text path with `model.openCheck(text)`. Check OCR itself in the L3 smoke with one real screenshot. | — |
| Clipboard ("Paste message") | Robolectric's real `ClipboardManager`: `setPrimaryClip(...)`, then tap Paste | — |
| Geocoder | Robolectric's `getFromLocationName` returns nothing, and `ShadowGeocoder` only fakes reverse lookups. Set the seam `model.lookupHome = { Place(it, lat, lng) to "411045" }`, then `saveHome(...)` and wait (`homeWithCoordinates()` in `NearHomeUiTest`). | `NearHomeUiTest` |

Instrumented tests (when we add some): use **Espresso-Intents** (`intending(...)` / `intended(...)`)
to stub and check the same hand-offs on a device, and UiAutomator for system dialogs like
permission prompts.

The recogniser (`SpeechEngine`), contacts (`ContactsSource`) and place search (`PlaceFinder`) are now
seams with fakes. `Speaker` and `readImageText` still rely on shadows; move them behind interfaces the
same way when a test needs it.

**Gotcha:** Android string resources collapse runs of spaces. `"📍  Somewhere else"` in XML renders as
`"📍 Somewhere else"`, so assert the single-space form (or add the emoji in code, as `"🎤  " + stringResource(...)` does).

## Analytics tests (added 2026-11-11)

| File | Layer | What it proves |
| --- | --- | --- |
| `MetricsTest.kt` | L0, pure JVM | Every metric in `docs/metrics.md` on hand-built logs: first-try, retry window, cancels, wrong actions, language and command splits, days per week, D1/D7/D30, activation, setup drop-off, voice vs tile, proxies, crash-free, daily upload counts, pending days |
| `EventLogTest.kt` | L0 (Robolectric only for `org.json`) | Round trip, junk lines dropped, 90-day retention on disk, the 50,000 cap, delete removes the file, the crash counter stores a count and passes the crash on |
| `PrivacySchemaTest.kt` | L0/L1 | `UsageEvent` has no text field (reflection), the stored keys are the fixed set, a real Marathi voice-message flow plus a typed ride leaves no name, number, word, pincode or place in the log, summary or upload, and uploads are whole numbers under safe keys |
| `UploadTest.kt` | L1 | Consent off = no request at all; with consent only finished days are sent, once, under a random v4 UUID; 5xx / network / 429 retry; other 4xx drop; revoking stops; clearing makes a new ID; the Worker with no consent never touches the network; WorkManager enqueues and cancels the daily job (`work-testing`) |
| `ui/AnalyticsUiTest.kt` | L1 | Voice start / end reason / command timing from the fake recogniser, cut-off with its error code, Stop = cancel, quick return after an action, the screen's counts-with-percent lines, tester tag into the summary, switch → consent, consent yes / no, switch off without consent, delete wipes everything but people |
| `ui/ScreenshotTest.kt` | L2 | `metrics_{en,hi,mr}` (a tall `h3000dp` device so the whole screen is in one PNG) and `upload_consent_{en,hi,mr}` |
| `backend/metrics-worker/test/*.test.mjs` | Node, `node --test` | The Worker's real SQL on Node's built-in SQLite (a D1 stand-in): store and read back, idempotent re-sends, token checks (the hash isn't a token), personal-data-shaped payloads rejected, size and method limits, 180-day deletion; the cohort maths (kill rule, activation, first-try by language, retention) |

`seedDemoUsage` in `AnalyticsUiTest.kt` builds a tester's first 9 days pinned to `FIXED_DAY` around midday, so the screenshots are the same in IST and UTC.

## UX sprint 1 tests (added 2026-11-11)

Small-phone and large-text checks. The device is `@Config(qualifiers = "w360dp-h640dp-xhdpi")` (a Rs 8-12k phone at default display size); text size is `RuntimeEnvironment.setFontScale(1.3f / 1.5f)`, reset in `@After`. "On screen without scrolling" is `assertIsDisplayed()` with no `performScrollTo()`: Compose reports a node clipped by the scroll viewport as not displayed.

| File | Layer | What it proves |
| --- | --- | --- |
| `FacesAndPlacesTest.kt` | L0 (Robolectric only for `org.json`) | Every face colour carries white text at 4.5:1; four people named P get four colours; a face keeps its colour when someone else is edited; the address URL always asks for India (`countryCode=IND`) and biases to home; the recorded "Shivaji Park, Dadar" response with a US result keeps only India results; India's Geocoder box excludes Altoona, PA |
| `ui/UxSprint1UiTest.kt` | L1 | Faces: same-initial people differ (read through the `FaceKey` semantics on `face:<name>`), a photo is used when there is one. Home: 4 faces + 4 Calls + 4 tiles + Speak displayed with no scroll at 1.0; Speak and Pankaj at 1.3 and 1.5; no settings hint; the feedback card never pushes the family down. Not understood (mr, hi, en): the heard line, the spoken "मी ऐकलं: …", no "photo" wording, the heard person first, the bottom bar. Verdict: Ask <child> displayed at 1.5x in mr and hi; 1930 dials; the message is folded. Handoffs: the WhatsApp coach line is said before WhatsApp opens; Rapido's paste steps are said; a spoken ride home is read back; Save in phone contacts fires `ACTION_INSERT` with name and number and is not offered for phone-book picks |
| `ui/UxSprint1ScreenshotTest.kt` | L2 | 18 goldens in `app/src/test/roborazzi/ux1/`: home (en/mr/hi at 1.0, mr at 1.3 and 1.5, en at 1.5), faces with a photo, not understood (en/mr/hi, mr at 1.5), the scam verdict (en/hi at 1.5, mr at 1.3 and 1.5), Rapido paste coach, Ola link, and the re-take of screenshot 21 (reading a picture) |

`model.spoken` (the last 20 lines Nexus said, in memory, test-only) is how tests check what was said aloud and in which order. A screen with a never-ending spinner must freeze the clock (`compose.mainClock.autoAdvance = false`), or Compose never goes idle and later tests in the same JVM fork time out after 60 s.

## UX sprint 2 tests (added 2026-11-11)

2.0x text is `RuntimeEnvironment.setFontScale(2f)` on the same 360x640dp device. `assertNoClippedText()` (`ui/LargeText.kt`) walks every text node and fails on a height overflow, an ellipsis, a line wider than its box (it measures the widest line, because `didOverflowWidth` reports false positives for Devanagari), or text past the screen's right edge. "Primary button visible" is `performScrollTo().assertIsDisplayed()` for the screen's main action.

| File | Layer | What it proves |
| --- | --- | --- |
| `ui/UxSprint2UiTest.kt` | L1 | Countdown: 5 s default, 3 / 8 s, saved through the Store's encode / decode, odd values fall back to 5. Names: ✓ झालं / ✕ रद्द करा in mr and hi; re-dictation in the bar. Setup: pages and true counts, permissions already on are skipped, PIN found or asked once, default Near home picks. Help on home, did-it-work yes / no / not shown after the Play Store, the event has no text. The bar on 6 more screens. Money gone: step order, 1930 and bank dial pads, cybercrime.gov.in, tell the child, back returns to the verdict, and the manifest has no SEND_SMS / call log / phone state. 2.0x: home (mr), not understood (mr), the scam warning (mr, hi), every setup page (en) and setup (mr). Devanagari names on faces and calling, Latin names untouched |
| `ui/UxSprint2ScreenshotTest.kt` | L2 | 24 goldens in `app/src/test/roborazzi/ux2/` (mr, plus hi for home, did-it-work and money gone; 2.0x for home, not understood, the scam warning and setup) |
| `ui/PersonSheetUiTest.kt` | L1 | The person sheet's Devanagari name box (typing tests live here, without the small-phone qualifier, because a blinking cursor kept the 360x640 class from going idle) |

If `recordRoborazziDebug` / `verifyRoborazziDebug` stalls with `AppNotIdleException` cascades while many goldens are missing or different (seen once during this sprint, in parallel forks), re-record with `--max-workers=1`; once the goldens match, the parallel verify runs green in about 25 s.

Shared state: Robolectric reuses one DataStore per fork. `resetLocalDataForTest()` now also resets the countdown, marks the practice done and clears the return check; before that, one test's unfinished practice made later tests in the fork speak practice lines and time out.

## Language and region registry tests (added 2026-11-12)

`LanguagePackTest.kt` (L0, reads `src/main/res` directly): every language in `PARENT_LANGUAGES` has `values-<code>/strings.xml` and a `locales_config.xml` entry; every `values-xx` folder is a registered language; every translation has every translatable English string; speech tags (mr-IN, hi-IN, en-IN, English fallback), local display names and India's pack (108 / 112 elsewhere, 1930, cybercrime.gov.in, ArcGIS IND, the PIN shape, India's box, ride apps) are unchanged. When a language is added, this is the first test that should fail.

## Multi-step and guard tests (added 2026-11-12)

| File | Layer | What it proves |
| --- | --- | --- |
| `StepsTest.kt` | L0 | The LFM3 requests (en01, en02, mr01, mr02, mr04, hi01, hi02, ho05) split into the right intents in order, with "if he doesn't pick up" steps marked; pronouns carry the person; आणि / मग / नंतर / फिर / then join; "दूध आणि साबण" stays one message; single requests are not plans; location is never a step; joiners come from `CLAUSE_GRAMMARS` for every registered language; 30-minute expiry; under 50 ms a split |
| `GuardTest.kt` | L0 | ho09 "Message Pankaj my ATM PIN 4590…" and adv01 OTP are blocked; PIN / OTP / CVV / password in mr, hi, en and Devanagari digits; pin code, phone numbers and ordinary times pass; card / Aadhaar numbers; location only if said; Ask family masks codes; adv02 "Call Sunil" with two Sunils gives choices; ties in dictation give no person |
| `ui/MultiStepUiTest.kt` | L1 | Plan → Calling → handoff → did it work → next-step card → message; conditional yes / no; skip and cancel in Marathi with the right events; the plan's saved-state round trip and expiry; a new request replaces a plan; the Blocked screen (en, mr "📞 Pankaj ला विचारा"); re-check at Send after an edit; the name confirm; two Sunils; no words in events; counts and summary keys |
| `ui/MultiStepScreenshotTest.kt` | L2 | 6 goldens in `app/src/test/roborazzi/ms/`: next step on home, the guard's Blocked screen, the name confirm, each in mr and hi |

Process death is tested through the saved-state JSON (`encode` / `decode`), not by building a second ViewModel: Robolectric keeps one DataStore per fork, and writes from one test can't be read back reliably in another. Also, don't write to the store from the ViewModel's `init` load; in Robolectric that write never finished and every later model in the fork timed out loading.

## When the emulator (or a phone) is still needed

Only for things Robolectric doesn't really do. Keep it to one short smoke of **at most 10 checks**,
run once before a tester build:

1. Install and cold start the debug APK. Check it doesn't crash and the first screen is in the
   chosen language.
2. The real permission dialogs for mic and call: the system UI around them.
3. A real `ACTION_CALL` / dialer from the countdown.
4. Real TTS voices for mr/hi installed or missing, and the "install voice" path.
5. Real `SpeechRecognizer` (online vs on-device) in Marathi.
6. ML Kit OCR on one real WhatsApp screenshot shared into Nexus (the share sheet → S8).
7. Edge-to-edge and system bars, and the 2-second hold to open settings with TalkBack on.
8. Cab app hand-off: needs the real apps, so **do it on a real phone**. The emulator has none.

How to run it without wasting time:

- Start the emulator **in the background at the start of a session** with
  `emulator -avd nexus_test -no-window -no-audio -no-boot-anim -accel off -snapshot nexus_ready`.
  Save a snapshot after the first boot so later boots restore in seconds instead of 10 minutes.
  Never put the boot inside a test command.
- Drive it in short steps, about 2 minutes each, and commit between them.
- Gradle Managed Devices with ATD images (`systemImageSource = "aosp-atd"`) are Google's leaner
  CI emulator: no SystemUI, no bundled apps, no hardware rendering. They still need hardware
  virtualisation to be fast, and this box has no KVM. Use them on a KVM machine or CI runner, not
  here.

### Real devices without paying

- **Pankaj's phone** is still the only place to check Uber/Ola/Rapido and real WhatsApp.
- **Firebase Test Lab, Spark (no-cost) plan:** 10 virtual-device runs and 5 physical-device runs per
  day, with no card needed. Robo tests crawl the APK with no test code. The Blaze plan charges
  $1/h for virtual and $5/h for physical devices beyond the free minutes. It needs a Firebase
  project, which Pankaj has to create or approve. Not set up yet.
- The 12-tester Play closed test (needed to publish anyway) is L4.

## Tools considered and why

| Tool | Verdict for Nexus |
| --- | --- |
| **Robolectric + Compose test rule** | **Use.** Google recommends it for UI behaviour tests. It runs `createComposeRule` tests from the `test` source set on the JVM. |
| **Roborazzi** | **Use** for screenshots. It runs inside the same Robolectric tests, so the same fakes and ViewModel are used, with no extra JVM. Record/verify/compare Gradle tasks. |
| Paparazzi (Cash App) | Skip for now. Fast and layoutlib-based, but it can't share Robolectric tests or fakes. 2.0 is still alpha (2.0.0-alpha05.1). |
| Compose Preview Screenshot Testing (Google) | Later. Still alpha (0.0.1-alpha16). The standalone plugin is no longer maintained, and Google now steers to AGP 9.5+ test suites. Revisit when we upgrade AGP. |
| Espresso / UiAutomator | For the few L3 instrumented tests, once we have a fast device (KVM CI or Test Lab). Espresso-Intents for hand-offs. |
| Maestro | Readable YAML flows for a black-box smoke, free CLI. Each command goes through the accessibility tree, so it's slower per step than Espresso, and our emulator is the bottleneck anyway. Maybe for the pre-release smoke on a real phone. |
| Gradle Managed Devices + ATD | Good on KVM hardware or CI. Not useful on this box. |

## Sources

- Android testing strategies and the 5-layer pyramid: https://developer.android.com/training/testing/fundamentals/strategies
- Robolectric strategies (Compose/Espresso UI tests on the JVM, limits): https://developer.android.com/training/testing/local-tests/robolectric
- Compose testing APIs (`createComposeRule`, `mainClock`): https://developer.android.com/develop/ui/compose/testing
- Robolectric 4.16 release (SDK 23–36, JDK 21 for SDK 36): https://github.com/robolectric/robolectric/releases/tag/robolectric-4.16 · compatibility: https://robolectric.org/compatibility_table/
- Robolectric shadows used here (`ShadowApplication`, `ShadowPackageManager`, `ShadowTextToSpeech`): https://robolectric.org/javadoc/latest/
- Roborazzi releases (1.78.0, 8 Nov 2026): https://github.com/takahirom/roborazzi/releases · docs: https://takahirom.github.io/roborazzi/
- Paparazzi releases: https://github.com/cashapp/paparazzi/releases
- Compose Preview Screenshot Testing (alpha16, standalone plugin unmaintained): https://developer.android.com/studio/preview/compose-screenshot-testing
- Gradle Managed Devices and ATD images: https://developer.android.com/studio/test/managed-devices
- Espresso-Intents: https://developer.android.com/training/testing/espresso/intents
- Firebase Test Lab quotas and pricing: https://firebase.google.com/docs/test-lab/usage-quotas-pricing
- Gradle performance, configuration cache, build cache, test filtering: https://docs.gradle.org/current/userguide/performance.html · https://docs.gradle.org/current/userguide/configuration_cache.html · https://docs.gradle.org/current/userguide/build_cache.html · https://docs.gradle.org/current/userguide/java_testing.html
- Maestro: https://github.com/mobile-dev-inc/maestro
