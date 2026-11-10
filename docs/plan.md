# Nexus — living plan (started 2026-11-10)

> The original commuter-launcher thesis is superseded by the family co-pilot vision in `vision.md`. This file is the living status of that plan.

## Status (2026-11-10)

**Done.** Phase 1 screens S1–S13, the fast JVM test suite (Robolectric + Roborazzi), S14 voice-dictated WhatsApp and SMS messages (PR #6), and S15 Near home (PR #7). 237 JVM tests pass (about 20 s warm). Main has all of this. A debug APK is ready to sideload. There is no Play Store release yet.

**Next.**
1. Pankaj's real-phone test (cabs, home geocoding, WhatsApp keeping Marathi text, Marathi voice, a real scam screenshot, Near home Installed tags and Play Store links).
2. Native Hindi and Marathi review of the app's strings.
3. Release prep: signed AAB, upload key Pankaj keeps, privacy policy URL, Data safety form.
4. Play closed test: 12 testers (6 parent-child pairs) for 14 days. The Play Console fee needs Pankaj's yes. Outreach is drafted for Pankaj to send; it is not sent by the bot.
5. Then the exit / kill rule below.

## Thesis (one line)
Nexus is the family co-pilot for your parents' Android phone: they speak in their own language and the simple thing happens; before anything risky, it pulls you in, and nothing leaves the phone without their say.

## What exists (2026-11-10)
- Repo: github.com/pankajkhannade/nexus, native app in android/, package dev.pankajkhannade.nexus
- Parent mode, child-assisted setup, calls, dictated WhatsApp/SMS, ride home (Uber, Ola, Rapido, Namma Yatri), Near home tiles, "Is this safe?", "Ask family", family settings with usage export, day-3 / day-7 feedback, Privacy screen
- Debug APK for sideload only. No Play release.
- 237 JVM tests (logic + Robolectric Compose UI + Roborazzi). See `docs/testing.md`.
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
2. Pankaj's real-phone test and native hi/mr review, then fix what breaks
3. Release prep: signed AAB, upload key, privacy policy URL, Data safety form
4. Play closed test: 12 testers (6 parent-child pairs) for 14 days (Console fee needs Pankaj's yes)
5. Exit metric, then Phase 2 (paid family plan with the child's app) or a rethink

## Exit and kill rule
- **Go on to Phase 2** if, in week 2 of the closed test, at least 4 of 6 parents use Nexus on 4 or more of 7 days. (Also aimed for: 12 testers stay opted in for 14 days; at least 1 "Is this safe?" or "Ask family" per pair; at least 3 of 6 children say they'd pay Rs 999/yr, asked only with Pankaj's yes.)
- **Kill / return to discovery** if fewer than 2 of 6 parents use it on 4+ days a week.

## Historical note
The first plan on this day was a commuter/cab launcher ("pick me up at home") with a 7-day sideload test of 10 people. That thesis was dropped the same day in favour of the family co-pilot vision. The cab flow remains as Ride home, not as the headline.
