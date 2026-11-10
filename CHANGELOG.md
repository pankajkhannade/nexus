# Changelog

All notable changes to Nexus. Dates are IST.

## [Unreleased]

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
