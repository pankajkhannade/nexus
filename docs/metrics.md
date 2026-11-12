# Nexus metrics: what we measure, how, and how not to fool ourselves

Added 2026-11-11 (feat/analytics). Code: `Analytics.kt` (event schema and log), `Metrics.kt` (pure metric maths),
`Upload.kt` (opt-in daily upload), `MetricsScreens.kt` (How Nexus is doing, consent), `backend/metrics-worker/`
(Cloudflare Worker + D1), `scripts/metrics-report.mjs` (cohort table).

## The one number

**How many parents use Nexus on 4 or more days a week, and whether their voice commands work on the first try.**
Everything below either explains that number or is vanity.

## Data sources

| # | Source | What it gives | Where |
|---|---|---|---|
| 1 | **On-device event log** (source of truth) | Event types, counts and timings. No audio, words, names, numbers or places. | `files/analytics/events.jsonl`, 90-day retention, 50,000-event cap, not in Android backup |
| 2 | **Opt-in anonymous daily upload** (OFF by default) | Yesterday's aggregate counts under a random install ID | Worker `nexus-metrics` on workers.dev + D1 `nexus-metrics`, 180-day retention |
| 3 | **Test summary export** | The same metrics as JSON, shared by the tester's child (WhatsApp) | How Nexus is doing > Share test summary |
| 4 | **Play Console vitals** | Installs, uninstalls, crash rate, ANR rate, ratings | Play Console, once we're on Play (fee needs Pankaj's yes) |
| 5 | **Weekly 2-question child check-in** | Did Send/booking actually happen, what annoyed them | Draft below; Pankaj sends |
| 6 | **Monthly interview** | The why behind the numbers | 20 min call per family |
| 7 | **The ₹999 question** | Willingness to pay | Asked in the week-2 interview, only with Pankaj's yes |
| 8 | **Recorded voice test set** | Accuracy on real elderly voices, replayed on every build; Nexus vs Gemini | `docs/voice-architecture.md` test plan (volunteers pending) |

## The event schema (on the phone)

Every `UsageEvent` field is an enum, a number or a boolean (`PrivacySchemaTest` fails the build if a text field is
added). Fields: time (ms), type, language (mr/hi/en/other), command type, source (voice/tile/typed), end reason,
permission, app category, ok, duration ms, utterance length (characters, not the characters themselves), recogniser
error code, setup step (or a plan's step count / position), guard reason (a fixed category, added 2026-11-12).

| Event | When | Extra fields |
|---|---|---|
| `app_open` | The ViewModel loads (one app session) | |
| `foreground` | The app comes back to the front, not after an action | |
| `crash` | Uncaught exception (count only: no stack trace, no message) | |
| `setup_start`, `setup_step`, `setup_done` | Setup began, reached a new step, finished | step; setup ms |
| `permission` | Mic, contacts or call permission answered (including "Not now") | perm, ok |
| `voice_start`, `voice_end` | Speak pressed; listening ended | language; end reason, ms, chars |
| `recognizer_error` | Every SpeechRecognizer error code in a session | code |
| `command` | What a voice session (or a tile tap) became | command type, source, ok, ms from Speak |
| `picker_shown` | "Which one?" (two Rahuls) | |
| `cancel` | Stop / No / Back / Cancel before an action | command type |
| `action_opened` | Nexus handed off to another app | category (dialer, whatsapp, sms, cab, maps, grocery, pharmacy, doctor, home_service, play_store, emergency) |
| `action_return` | Nexus came back after an action | category, ms away |
| `near_tile`, `play_redirect` | A Near home tile; an Install-from-Play hand-off | category |
| `return_check` | The parent answered "did it work?" on home after a hand-off (added 2026-11-11) | category (dialer, whatsapp, sms, cab, maps), ok (yes = true) |
| `help_opened` | Help on home opened (button, or a "No" to did-it-work) | |
| `money_gone` | The पैसे गेले steps opened | |
| `multi_step_started` | One request became a plan of 2+ steps (added 2026-11-12) | step = number of steps, source |
| `step_done` | A plan step's action opened another app | step = position (1-based), category |
| `step_skipped` | A plan step was skipped | step = position; ok = true (parent skipped, or not needed after "it worked"), false (plan cancelled or replaced) |
| `guard_blocked` | The guard stopped or questioned an outgoing message | guard = `secret_code`, `long_number`, `location` or `name_unsure` |

End reasons: `user_done` (Done tapped), `complete` (a whole command heard), `silence`, `no_speech`, `max_time` (60 s),
`error` (failed before any words), `cut_off` (failed after words were heard), `cancelled` (Stop).

**Language detected** is the recogniser language of the session (the parent's chosen language). Marathi and Hindi
share a script, so guessing from the words would be wrong more often than right.

### What we chose not to measure, and why

- **Call connected for 10 s or more.** Knowing this needs `READ_PHONE_STATE` or the call log. Both are new risky
  permissions, and the call log is restricted on Play unless Nexus is the default dialer. Skipped. Instead we keep a
  **proxy**: the time Nexus was away after placing a call (`call_10s_proxy` = away 10 s or more). A parent who hangs
  up and goes to the home screen still counts, so it's an upper bound.
- **External app stayed in the foreground for over 60 s.** Knowing which app is in front needs `PACKAGE_USAGE_STATS`
  (a special-access permission the child would have to grant in Settings). Skipped. The proxy is the same
  time-away measure (`long_away` = 60 s or more).
- **Whether WhatsApp Send was tapped or a cab was booked.** No Android API tells another app this. "Opened" is never
  counted as "sent" or "booked"; the weekly check-in asks.

## Metric definitions

Every percentage is shown with its raw counts (`7 of 9 (78%)`), on the phone, in the export and in the report.

| Metric | Definition | Target / bar |
|---|---|---|
| **Days used per week** | Distinct local days with an `app_open`, `foreground`, `command`, `voice_start` or `action_opened`. Shown as "used on N of the last 7 days" and per full week since setup. | **Kill rule:** at least 3 of 10 testers active on 4+ of their first 7 days. Phase 2 bar: 4 of 6 parents on 4+ of 7 in week 2 (`plan.md`). |
| **Voice attempt** | A `command` event with source voice. | |
| **Retry** | A voice attempt starting within 30 s of the previous attempt, when that one failed. | |
| **Failed attempt** | Not understood, or followed (before the next voice session) by a `cancel`, or by an `action_return` under 5 s. | |
| **First-try success** | Of attempts that are not retries, the share that succeeded. Split by language and by command type. | **90%+** |
| **Not understood rate** | Attempts not understood / attempts | |
| **Retry rate** | Retries / attempts | |
| **Cancel rate** | Understood attempts that were cancelled / understood attempts | |
| **Wrong action rate** | `action_return` under 5 s / `action_opened` | |
| **"Which one?" rate** | `picker_shown` / attempts | |
| **Cut-off rate** | `voice_end` with `cut_off` / `voice_end` | |
| **Recogniser errors** | Count per error code (2 network, 7 no match, 11 server disconnected, and so on) | |
| **Utterance length, time to action** | Medians of `voice_end.chars` and of `command.ms` for understood voice commands | |
| **Activation** | A first-try voice success within 24 h of `setup_done`. "Not yet" while the 24 h are still running. | Most testers |
| **Time to first value** | `setup_done` to the first successful voice command or tile tap | |
| **Setup completion and drop-off** | Started / finished, duration, furthest step if unfinished, last answer per permission | **Under 5 min** |
| **Voice vs tile** | Per command type: understood voice commands vs tile taps. If parents only tap, the voice bet is weaker. | |
| **D1 / D7 / D30** | Active on day 1 / 7 / 30 after setup. Unknown until that day arrives. | |
| **Crash-free sessions** | (`app_open` minus `crash`) / `app_open` | **99.5%+** |
| **Actions opened** | Per app category; with the call and 60 s proxies above | |
| **"Very disappointed"** | Survey below, asked of the children | **40%+** "very disappointed" |

### Did it work? (added 2026-11-11, UX sprint 2)
- **Did it work** = `return_check` yes / all `return_check` answers, overall and by app category. The parent's own word, the closest we get to "the call connected" or "the cab was booked" without risky permissions. Unanswered checks are not counted as no.
- **Help opened** and **Money gone opened**: counts per week. A rising help count after a sprint is a signal to look, not a failure by itself.
- Daily count keys: `did_it_work_yes`, `did_it_work_no`, `did_it_work_{yes|no}_{dialer|whatsapp|sms|cab|maps}`, `help_opened`, `money_gone_opened`. The Worker accepts them as-is (it checks the key shape, not a list). Summary JSON: `did_it_work`, `did_it_work_by_app`, `help_opened`, `money_gone_opened`.

### Multi-step and the guard (added 2026-11-12)
- **Multi-step share** = `multi_step_started` / voice attempts. Tells us whether parents actually say two things at once (the LFM3 report assumed they would).
- **Step completion** = `step_done` / steps done + skipped; **step skip rate** = `step_skipped` (ok=true) / steps done + skipped. A high skip rate on step 2 means the plan guessed something the parent didn't want.
- **Guard stops** by category per week. `secret_code` is the one to read: each is a moment a PIN or OTP nearly left the phone. `name_unsure` counts "Which one?" for ties and the name confirm; it should fall as families save distinct names.
- Daily count keys: `multi_step_started`, `multi_step_{2..5}`, `step_done`, `step_skipped`, `step_cancelled`, `guard_blocked`, `guard_{secret_code|long_number|location|name_unsure}`. Summary JSON: `multi_step_started`, `steps_done`, `steps_skipped`, `guard_blocked` (by category).

## The upload (opt-in)

- **Off by default.** Turned on only from the consent page (How Nexus is doing > Send daily counts). Turning it off
  cancels the job at once. "Delete all usage data" turns it off and wipes the install ID.
- **Install ID:** a random UUID made on the phone when the family says yes. Never the Android ID or advertising ID.
- **What is sent:** one JSON body per run: `v`, `install_id`, `tester_source`, `app_version`, `lang`, and per finished
  day (yesterday and earlier, at most 30 days back, each day once) a `counts` object of whole numbers under fixed keys
  built from enums (`COUNT_KEY` = `^[a-z0-9_]{1,48}$`). Example keys: `active`, `app_open`, `voice_attempts`,
  `first_try_mr`, `first_try_ok_mr`, `retry`, `cancel`, `wrong_action`, `picker_shown`, `voice_end_cut_off`,
  `recognizer_error_7`, `tile_call`, `opened_cab`, `setup_seconds`, `activated`, `age_days`.
- **How:** WorkManager, once every 24 h on any network (unmetered not required), exponential backoff from 30 min.
  2xx marks the days sent; 429, 5xx and network errors retry; other 4xx are dropped (the same body won't get better).
- **Server:** `https://nexus-metrics.safe2eat-api.workers.dev` (free-tier Worker + D1). The Worker rejects any field
  or key it doesn't expect (it does not strip and store the rest). It stores no IP, user agent or time of day, and
  deletes rows older than 180 days on every upload. Reads need a bearer token; the Worker only holds its SHA-256.
- **The URL is configurable:** `./gradlew assembleDebug -PnexusMetricsUrl=https://.../v1/daily`.

## Reading the numbers

```bash
node scripts/metrics-report.mjs            # token from /workspace/founder-ceo/nexus/secrets/metrics-token
node scripts/metrics-report.mjs --json     # raw rows
```

It prints one column for everyone and one per tester source: testers, the kill rule (active 4+ of the first 7 days
vs the 3-of-10 bar, PASS / FAIL / not enough data), activation, first-try success by language, D1 / D7 / D30,
wrong actions and crash-free opens.

## Caveats (read before quoting any number)

1. **Small samples.** With 5-10 testers one family moves a percentage by 10-20 points. Counts go next to every
   percentage, everywhere. Don't quote a percentage with a denominator under 10 without the counts.
2. **Tester-source bias.** Family testers are kind and forgiving. Every summary and upload carries the tester source
   (family / friend / stranger), and the report splits by it. Read the stranger column first.
3. **Opened isn't sent or booked.** An opened WhatsApp isn't a sent message; an opened Uber isn't a ride. The weekly
   check-in confirms these. The call and 60 s figures are proxies (see above).
4. **Opt-in is self-selecting.** Families who turn on the upload are probably the keener ones. The shared summary
   (source 3) covers the rest.
5. **The language is the setting, not detection.** A parent set to Marathi who speaks Hindi counts as Marathi.

## Draft: weekly 2-question check-in (for the tester's child; Pankaj sends, nothing is sent by the bot)

**English**
> Hi! Quick Nexus check-in for this week (2 questions, reply in one line):
> 1. Did Aai/Baba use Nexus for something real this week (a call, a WhatsApp message, a ride)? What was it, and did it actually go through (message sent, ride booked)?
> 2. What was the one most annoying or confusing moment?
> If you can, also tap Family settings > How Nexus is doing > Share test summary and send it here. Thank you!

**Marathi**
> नमस्कार! या आठवड्याची Nexus ची छोटी चौकशी (२ प्रश्न, एका ओळीत उत्तर द्या):
> १. या आठवड्यात आई/बाबांनी Nexus खरंच कशासाठी वापरलं का (फोन, WhatsApp मेसेज, गाडी)? काय केलं, आणि ते खरंच पूर्ण झालं का (मेसेज गेला, गाडी बुक झाली)?
> २. सगळ्यात जास्त त्रास झाला किंवा गोंधळ झाला असा एक क्षण कोणता?
> जमलं तर Family settings > Nexus कसं चाललंय > चाचणीचा सारांश पाठवा, हे दाबून इथे पाठवा. धन्यवाद!

## Draft: "very disappointed" survey (Sean Ellis test; asked of the children at the end of week 2)

**English**
> How would you feel if you could no longer use Nexus on your parent's phone?
> - Very disappointed
> - Somewhat disappointed
> - Not disappointed (it isn't really that useful)
>
> What is the main benefit you get from Nexus?
> What type of family do you think would benefit most from Nexus?
> How can we improve Nexus for you?

**Marathi**
> तुमच्या आई-वडिलांच्या फोनवर Nexus वापरता आलं नाही, तर तुम्हाला कसं वाटेल?
> - खूप वाईट वाटेल
> - थोडं वाईट वाटेल
> - वाईट वाटणार नाही (ते फार उपयोगी नाही)
>
> Nexus मुळे तुम्हाला मिळणारा मुख्य फायदा कोणता?
> Nexus चा सगळ्यात जास्त फायदा कोणत्या कुटुंबांना होईल असं तुम्हाला वाटतं?
> Nexus तुमच्यासाठी आणखी चांगलं कसं करता येईल?

Score: the share answering "Very disappointed". 40% or more means we have something. Report it with the counts and
the tester source, like every other number.
