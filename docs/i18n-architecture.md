# Nexus i18n architecture: languages and countries as packs (2026-11-12)

Pankaj, 2026-11-12: "nexus is not just marathi voice.. it will expand to everything and every language in india and later in the world. We should build a global product and not just indian." Pune Marathi parents stay the first beachhead. This doc covers two things: where language and country are hard-coded today, and how a new language or country becomes a data / config pack instead of a code change.

Status: proposal. Step 1 (the two registries below) is merged with this doc. Nothing else has been refactored yet.

## 1. Audit: where language and country are hard-coded today

Code is under `android/app/src/main/java/dev/pankajkhannade/nexus/`. Severity is how much it blocks a new language (L) or a new country (C).

| # | Where | What is hard-coded | Blocks | Severity |
|---|---|---|---|---|
| 1 | `Plan.kt` (call / message regexes, `RELATION_WORDS`, `NOT_NAMES`), `Dictation.kt` (`SMS_WORDS`, `TELL_WORDS`, `CALL_WORDS`, `CASE_SUFFIXES` ला/को/la/ko, `JOINERS` आणि/और/and, question words, polish rules), `Destination.kt` (go verbs, vehicle words, `पर्यंत`/`ला`/`कडे` suffixes), `NearHome.kt` (`NEED_EXACT` need words), `Speech.kt` (`JOINERS` that keep the mic open) | The voice grammar: about 140 lines of Marathi, Hindi, Hinglish and English word lists and regexes, written into the code and mixed together (every rule runs for every language) | L | **Highest.** This is the product's core. A Tamil or Gujarati parent gets "didn't understand" for everything. Postpositions differ by language (Tamil and Kannada glue case onto the name: "Pankajukku", "Pankajge"). Word lists don't scale to 22 languages in one file. |
| 2 | `Dictation.kt` `romanize` / `soundKey` (Devanagari consonant, vowel and matra tables), `Contacts.kt` honorifics (जी, साहेब, saheb), `Destination.kt` and `Model.kt` `'\u0900'..'\u097F'` checks, `Faces.kt` virama `\u094D` | Name matching between what was heard and the phone book assumes Devanagari or Latin script | L | **High.** Bengali, Gujarati, Gurmukhi, Odia, Tamil, Telugu, Kannada, Malayalam and Urdu (Perso-Arabic, right-to-left) names won't match contacts written in Latin. |
| 3 | `Safety.kt` | Scam patterns in English, Hindi and Marathi (OTP, KYC, "account blocked", police, prize, job, refund), Indian bank and UPI brand domains (SBI, HDFC, ICICI, PhonePe, Paytm), UPI collect requests | L + C | **High** for a new country (the scam playbook differs), medium for a new Indian language (the brands carry over, the words don't) |
| 4 | `res/values{,-hi,-mr}/strings.xml`, `res/xml/locales_config.xml`, the language picker (`Setup.kt` / `Settings.kt`: a row of 3 buttons) | 412 translatable strings in 3 languages; a 3-button row can't hold 22 languages | L | Medium. Translation is ordinary work, but the picker has to become a list, and native review is the bottleneck. |
| 5 | `Voice.kt` `speechTag` (mr-IN, hi-IN, en-IN), `Speech.kt` (the recogniser locale and on-device check), `Speaker` (TTS locale) | Speech locales. Already graceful: `checkMode` / `hasVoice` fall back to online or text when the phone lacks a language | L | Low. Now one registry entry per language (step 1). Each language still needs a real-phone check of recogniser and TTS quality. |
| 6 | `Analytics.kt` `Lang` enum (MR, HI, EN, OTHER), `MetricsScreens.kt` labels, the Worker's language column | Metrics buckets every other language as `other` | L | Low. Needs a free-form language code (validated by regex like the count keys), not an enum. |
| 7 | `Actions.kt` `suggestUrl` (`countryCode=IND`, `, IND` filter), `IndiaBox`, `Destination.kt` Geocoder box | Address search is India-only on purpose (UX sprint 1 fix #6: a US-locale phone suggested Altoona, PA) | C | Medium. Now reads `INDIA` (step 1). It must follow the saved country, not the phone's locale, which was the original bug. |
| 8 | `HelpScreens.kt` (1930, cybercrime.gov.in), the `money_*` strings, `NearHome.kt` `emergencyNumber` (108 in India, 112 elsewhere) | The money-gone flow and emergency numbers | C | Medium. The numbers now come from the region pack (step 1). The wording ("call 1930") is in the strings, so each country needs its own copy. |
| 9 | `Cabs.kt` `CabApp` enum (Uber, Ola, Rapido, Namma Yatri) with per-app deep-link builders, `NearHome.kt` `SERVICE_CATALOG` (Blinkit, Zepto, Tata 1mg, Practo, Urban Company…) | Ride and service apps are India's | C | Medium. The list of apps is data. Each app's deep link needs research (documented parameters only), so a country pack carries a handoff type per app: Uber-style link, Ola-style link, or open plus paste. |
| 10 | `NearHome.kt` PIN regex (6 digits), the setup pin question and strings ("पिन कोड"), `Phone.kt` India mobile rules (10 digits starting 6–9), `Model.kt` default country India | Postal code and phone number shapes | C | Medium. International numbers already work through `normalizePhone`. The postal code question should become "postal code" with a per-country shape, or be skipped. |
| 11 | `Practice.kt` and `Setup.kt` example sentences ("Pankaj la phone lav"), `Model.kt` `shownIn` (Devanagari only) | Examples and local display names | L | Low. Examples are strings already. `shownIn` now asks the language's script (step 1). |
| 12 | `NearHome.kt` English map queries ("pharmacy near…") | Google Maps search terms | C | Low. English works in most countries; a pack can override. |

### The biggest blockers (summary)
1. **The voice grammar is code, not data.** About 140 lines of mr/hi/en word lists and regexes are spread over `Plan.kt`, `Dictation.kt`, `Destination.kt`, `NearHome.kt` and `Speech.kt`, all run together. A new language means editing parser code, and the rules can't express glued case endings (Tamil, Kannada, Malayalam, Telugu).
2. **Name matching only knows Devanagari ↔ Latin.** The romaniser and sound keys can't match "முருகன்" or "মা" to a Latin contact, so calling by name breaks in the other Indian scripts.
3. **The scam checker is India-specific in both words and brands.** It's the safety promise, so it can't ship half-translated.
4. **Country is assumed to be India** in address search, the PIN step, 1930 / cybercrime.gov.in, 108, and the cab and service apps. The numbers and search region now sit in one India pack, but the flows (PIN question, money-gone copy, cab handoffs) still read as Indian.
5. **The language picker, metrics enum and locale config are fixed at three languages.**

## 2. Proposal: language packs and region packs

Two kinds of pack, chosen separately, because language ≠ country: a Marathi speaker can live in Dubai, and a Hindi speaker in Pune.

### Language pack (one per language, e.g. `ta`)
```
lang/ta/
  pack.json            # code, nativeName, speechTag "ta-IN", script "TAMIL", rtl false, status (draft | reviewed)
  strings.xml          # -> res/values-ta/strings.xml (Android resources stay the source of UI text)
  grammar.json         # the voice grammar as data (below)
  romanize.json        # script -> rough Latin table for name matching (consonants, vowels, matras, virama)
  safety.json          # scam phrases in this language (OTP / KYC / blocked / police / prize / job / refund / urgency)
  corpus.tsv           # 200+ real utterances with the expected Command, the pack's acceptance test
```
`grammar.json` holds what the code has as Kotlin sets today (the Tamil words below are illustrative, not reviewed):
```json
{
  "call_verbs": ["போன் பண்ணு", "கால் பண்ணு", "call"],
  "message_verbs": ["மெசேஜ் அனுப்பு", "whatsapp"],
  "tell_words": ["சொல்லு"],
  "case_markers": { "suffix": ["க்கு", "kku"], "postposition": [] },
  "joiners": ["மற்றும்", "and"],
  "go_verbs": ["போகணும்"], "home_words": ["வீடு", "veedu", "home"],
  "relations": { "son": ["மகன்", "magan"], "daughter": ["மகள்"] },
  "needs": { "medicine": ["மருந்து"], "doctor": ["டாக்டர்"], "emergency": ["ஆம்புலன்ஸ்", "108"] },
  "honorifics": ["அவர்கள்", "sir"]
}
```
At run time, `RuleIntentParser` loads the packs for the parent's language plus English (code-mixing is normal everywhere in India) and applies the same ordered rules. The `IntentParser` seam in `Commands.kt` already exists, so a later `ModelIntentParser` (an on-device or server language model returning the same `Command`) can replace rules for languages where word lists don't hold up. The corpus stays the acceptance test either way.

### Region pack (one per country, e.g. `IN`, later `AE`, `GB`, `US`)
Today's `RegionPack` in `Regions.kt` (step 1), grown into data:
```
region/IN/
  pack.json   # countryCode IN / IND, bounds, postal code {regex, label key, ask: true}, phone {mobile regex, trunk "0"},
              # emergency "108", fraud {helpline "1930", portal "https://cybercrime.gov.in"}, mapsQueryLanguage "en"
  apps.json   # rides: [{id: uber, pkg, handoff: "uber_link"}, {id: ola, handoff: "ola_link"}, {id: rapido, handoff: "paste"}]
              # services per need: grocery [...], medicine [...], doctor [...], home_service [...]
  safety.json # bank and payment brand domains (SBI, HDFC, PhonePe…), payment-request patterns (UPI collect)
  strings/    # country copy that isn't language: money-gone steps text per language ("call 1930")
```
Handoff types stay code (each needs a documented deep link and its own test). A country picks from them.

### What stays code
Screens, the ContinuousListener, the countdown, privacy rules, handoff builders, metrics maths. Packs never carry code or URLs that run. They're reviewed JSON shipped inside the APK; there's no download in v1, so Play review and offline use keep working.

## 3. Plan and effort (one engineer-agent plus a native reviewer per language)

| Step | What | Effort | Done when |
|---|---|---|---|
| 1 | Registries: `Languages.kt` (`PARENT_LANGUAGES`, script, speech tag) and `Regions.kt` (`RegionPack`, `INDIA`); speech tags, the picker, local names, 108 / 112, 1930, the portal, ArcGIS country, India's box, the PIN shape and ride apps read from them. `LanguagePackTest` checks registry vs resources. | **done 2026-11-12** | Merged with this doc; 469 tests green |
| 2 | Move the grammar to data: today's mr / hi / en word lists become `grammar.json` packs loaded by `RuleIntentParser`, with no behaviour change. Write `corpus.tsv` for mr / hi / en from the existing parser tests and real-phone feedback. | 1–1.5 weeks | Same tests green, plus each corpus at ≥ 95 % |
| 3 | Script-aware name matching: a romaniser table per script (start with Bengali, Gujarati, Gurmukhi, Tamil, Telugu, Kannada, Malayalam, Odia); a virama table for face initials; Perso-Arabic for Urdu later (needs RTL checks). | 1 week | Name-match tests per script |
| 4 | Picker as a list (22 languages) and metrics language as a validated code (not an enum); Worker accepts it. | 2–3 days | Picker and metrics tests; Worker tests |
| 5 | Each new Indian language: strings (412) + native review, `grammar.json` + 200-utterance corpus, `safety.json`, romaniser check, a real-phone recogniser and TTS check. | **4–7 days per language** after steps 2–4 (about 2 of those days are the native reviewer) | Corpus ≥ 90 % on a real phone; reviewer sign-off |
| 6 | Region packs as data, and flows that follow the saved country (address search, postal code question, money-gone copy, ride and service apps). | 1–1.5 weeks one-time | India behaviour unchanged; a test "fake country" pack works end to end |
| 7 | Each new country: phone rules, emergency and fraud numbers (official sources only), ride-app deep-link research, service catalog with Play package checks, a scam playbook with local brands, privacy / legal review. | **1.5–3 weeks per country** | A local tester family passes the leave-behind practice |

Order: steps 2–4 next (about 3 weeks, no visible change for Marathi parents). Then languages one at a time, chosen by tester demand (Gujarati, Tamil, Bengali, Telugu and Kannada are the likely first five). Country packs come only after the Indian beachhead passes its exit metric (`plan.md`).

## 4. Rules for every pack
- Native-speaker review before a language is marked `reviewed`; `draft` languages show a "help us improve" line and are off the picker in release builds.
- Official sources only for emergency and fraud numbers and portals, with the date checked, in the pack.
- No new permissions for any pack. The Is-this-safe and money-gone promises (never says "safe", dial pads only) are the same everywhere.
- Every pack ships with its corpus and passes it in the JVM suite. CI is local today, so the corpus runs in `testDebugUnitTest`.
