# Nexus — living plan (started 2026-11-10)

> The original commuter-launcher thesis is superseded by the family co-pilot vision in `vision.md`. This file is the living status of that plan.

## Status (2026-11-10)

**Done.** Phase 1 screens S1–S13, the fast JVM test suite (Robolectric + Roborazzi), S14 voice-dictated WhatsApp and SMS messages (PR #6), S15 Near home (PR #7), and the fixes from Pankaj's first real-phone feedback (fix/real-phone-feedback-1): continuous listening (no mid-message cut-off), calling and messaging anyone in the phone book, and rides to any place by voice or typing. 308 JVM tests passed at that point (356 after analytics, about 20 s warm). Main has all of this. A debug APK is ready to sideload. There is no Play Store release yet.

**Analytics (2026-11-11, feat/analytics).** On-device event log, the metrics maths, How Nexus is doing (en/hi/mr), Share test summary with a tester tag, Delete all usage data, and the opt-in daily upload to our own Worker (live at nexus-metrics.safe2eat-api.workers.dev, free tier). `node scripts/metrics-report.mjs` prints the cohort table. 356 JVM tests + 11 Worker tests. Definitions and targets: `docs/metrics.md`.

**UX sprint 1 (2026-11-11, feat/ux-sprint-1).** From the UX critique: faces you can tell apart, home on one 360x640dp screen, "Didn't understand" shows and says what was heard, the verdict's Ask <child> pinned, spoken handoff coaching (and Ola's drop-off link), "Save in phone contacts" in setup, India-only address search, and the shared bottom bar. 404 JVM tests. The 19 design principles are in `design.md` 3.8.

**UX sprint 2 (2026-11-11, feat/ux-sprint-2).** Adjustable 3 / 5 / 8 s countdown (default 5), झालं / रद्द करा everywhere, an honest shorter setup (PIN as one question, true step counts), help always on home, "did it work?" after every hand-off (counted as yes/no by app kind), the bottom bar on the voice and message screens, the full पैसे गेले flow, 2.0x text tests, and Devanagari display names. 463 JVM tests.

**Global direction (2026-11-12).** Pankaj: Nexus is a global product (every Indian language, then the world); Pune Marathi parents stay the beachhead. Vision stages A (mr/hi/en, India) → B (all major Indian languages) → C (global, diaspora first) are in `vision.md`. Hard-coding audit and the language/region pack plan: `i18n-architecture.md`. Step 1 (language registry + India region pack, no behaviour change) is merged; 469 JVM tests.

**Multi-step and safety guard (2026-11-12, feat/multi-step).** One sentence, two or three steps ("ride to Dinanath and tell Pankaj I've left"; "call the doctor, if he doesn't pick up message him") through a rules splitter with per-language joiners, a saved step queue that waits on home after each handoff (30-minute expiry), and a deterministic guard on every outgoing message (no OTP / PIN / card numbers, ties and slips asked, no added location). The on-device LLM is parked (LFM3 test). 516 JVM tests.

**Next.**
1. Pankaj's second real-phone test, using the new leave-behind practice (call, message, ride home) with a parent: a long Marathi message with pauses, "Sunil la phone lav" for a phone-book contact, two contacts with one name, "स्वारगेटला जायचं आहे" into Uber or Ola, plus the earlier list (WhatsApp keeping Marathi text, Near home tags, a real scam screenshot). Also: does Ola open with Swargate filled in; is the spoken coach line helpful or too much; are the 4 faces easy to tell apart; does "Save in phone contacts" return to Nexus.
2. Native Hindi and Marathi review of the app's strings (including the new coaching lines).
3. ~~UX sprint 2~~ **done 2026-11-11**. In Pankaj's real-phone test, also check: is 5 s right, does the parent answer "did it work?", and does 1930 / cybercrime.gov.in open on his phone. Deferred: the bar on Practice / Try it, a spoken "मदत".
4. Language packs, steps 2–4 of `i18n-architecture.md` (grammar as data with an utterance corpus, script-aware name matching, a list picker and metrics language codes), about 3 weeks, no visible change for Marathi parents. New languages wait for the Phase 1 exit metric.
5. Multi-step on a real phone: does the parent come back to Nexus after Uber (or stay in Uber), is the next-step card noticed, and do parents say two things at once often enough to keep it (`multi_step_started` / voice attempts). "Show the way to X" (Maps directions by voice) is the missing intent from the LFM3 set.
6. Release prep: signed AAB, upload key Pankaj keeps, privacy policy URL, Data safety form.
7. Play closed test: 12 testers (6 parent-child pairs) for 14 days. The Play Console fee needs Pankaj's yes. Outreach is drafted for Pankaj to send; it is not sent by the bot.
8. Then the exit / kill rule below.

## Thesis (one line)
Nexus is the family co-pilot for your parents' Android phone: they speak in their own language and the simple thing happens; before anything risky, it pulls you in, and nothing leaves the phone without their say. Global and language-pluggable, starting with Marathi in Pune.

## What exists (2026-11-10)
- Repo: github.com/pankajkhannade/nexus, native app in android/, package dev.pankajkhannade.nexus
- Parent mode, child-assisted setup, calls, dictated WhatsApp/SMS, ride home (Uber, Ola, Rapido, Namma Yatri), Near home tiles, "Is this safe?", "Ask family", family settings with usage export, day-3 / day-7 feedback, Privacy screen
- Debug APK for sideload only. No Play release.
- 469 JVM tests (logic + Robolectric Compose UI + Roborazzi) and 11 Worker tests. See `docs/testing.md`.
- Metrics: on-device log + opt-in daily counts + share-able summary. See `docs/metrics.md`.
- Play needs: signed AAB, upload key Pankaj keeps, Play Console account (paid, needs Pankaj's yes), privacy policy at a public URL, Data safety form

## Riskiest assumptions
| # | Assumption | Status |
|---|---|---|
| 1 | Parents will open Nexus on their own on 4+ days a week, once the child has set it up | UNTESTED - top risk |
| 2 | Child-assisted setup finishes in under 3 minutes on a real phone | UNTESTED |
| 3 | Voice in Hindi, Marathi and Hinglish is reliable enough on mid-range Indian phones | UNTESTED on a real phone |
| 4 | Families will pay Rs 999/yr after the closed test (deposit ask only with Pankaj's yes) | UNTESTED - parked until #1 |

## Journey + magic moment
discover (child hears about it) -> child installs Nexus on the parent's phone -> setup (people, language, home, Near home apps) -> parent says "Pankaj la phone lav" and the child's phone rings -> daily use (call, message, ride home, Near home, "Is this safe?") -> ask family when something looks off
Magic moment candidate: the parent says a sentence in Marathi and a call or a prefilled WhatsApp message happens, with no hunting through apps.

## Milestones
1. ~~Phase 1 S1–S13, fast tests, S14, S15~~ **done (2026-11-10)**
2. Pankaj's real-phone test and native hi/mr review, then fix what breaks. ~~Round 1 (voice cut-off, phone book, rides anywhere)~~ **fixed 2026-11-10**; round 2 pending
3. Release prep: signed AAB, upload key, privacy policy URL, Data safety form
4. Play closed test: 12 testers (6 parent-child pairs) for 14 days (Console fee needs Pankaj's yes)
5. Exit metric, then Phase 2 (paid family plan with the child's app) or a rethink

## Exit and kill rule
Measured with `docs/metrics.md`: the How Nexus is doing screen, the shared summaries, and `scripts/metrics-report.mjs` for families who opt in. Every number is read with its counts and its tester source (family / friend / stranger).
- **Go on to Phase 2** if, in week 2 of the closed test, at least 4 of 6 parents use Nexus on 4 or more of 7 days. (Also aimed for: 12 testers stay opted in for 14 days; at least 1 "Is this safe?" or "Ask family" per pair; at least 3 of 6 children say they'd pay Rs 999/yr, asked only with Pankaj's yes.)
- **Kill / return to discovery** if fewer than 2 of 6 parents use it on 4+ days a week.

## Historical note
The first plan on this day was a commuter/cab launcher ("pick me up at home") with a 7-day sideload test of 10 people. That thesis was dropped the same day in favour of the family co-pilot vision. The cab flow remains as Ride home, not as the headline.
