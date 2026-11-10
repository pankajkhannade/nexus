# Nexus — living plan (started 2026-11-10)

> **Updated 2026-11-10:** the thesis below was the original commuter-launcher bet. It is superseded by the family co-pilot vision in `vision.md` (Phase 1: parent mode, voice in Hindi/Marathi/English, "Is this safe?", "Ask family", 12-person Play closed test with 6 parent-child pairs). Phase 1 kill rule: fewer than 2 of 6 parents use Nexus on 4+ days a week. The sections below are kept for history.


## Thesis (one line)
Busy Indian Android users will keep a local, private assistant that turns one phrase ("pick me up at home", "call Aai") into the right app action, if it saves real taps every day.

## What exists (from Grok Build handoff, 2026-11-10)
- Repo: github.com/pankajkhannade/nexus, native app in android/, package dev.pankajkhannade.nexus
- Debug APK for sideload only. No Play release.
- Onboarding: country, home, office, >=1 person (up to 5). Indian numbers 10 digits, start 6-9.
- Home/Office -> Uber, Ola, Rapido or Maps (only tapped app opens; last cab app offered first).
- Saved person -> call (dialer), SMS, WhatsApp.
- Commands via message box, speech, or Share from another app.
- Contacts search only after in-app explanation + Android permission; stays on device.
- Light theme default. Does NOT read other apps' screens, no live fares.
- Play needs: signed AAB, upload key Pankaj keeps, Play Console account (paid, needs Pankaj yes), privacy note.

## Riskiest assumptions
| # | Assumption | Status |
|---|---|---|
| 1 | People repeat a Nexus command daily (beats Assistant / app shortcuts) | UNTESTED - top risk |
| 2 | Onboarding (home, office, a person) finishes in under 60s without help | UNTESTED |
| 3 | Voice/phrase understanding is reliable enough in Indian English/Hinglish | UNTESTED |
| 4 | Someone will pay or it can earn (cab affiliate etc.) | UNTESTED - parked until #1 |

## Journey + magic moment
discover (friend shares APK) -> install (sideload friction!) -> onboard -> first value -> come back daily -> refer
Magic moment candidate: first "pick me up at home" lands in the cab app with home already set, in one step.

## Milestones
1. 12 Play closed-test testers (14 days; required for a new personal Play account), 7 days, daily-use data (by ~2026-11-20)
2. Pick the one command people repeat; cut everything else in onboarding around it
3. Play internal testing track (needs Pankaj's yes on Play Console fee)
4. First 100 weekly active users -> then monetization test

## This week's top 3
1. Recruit 5-10 testers (Pankaj sends message) and get APK on their phones
2. Add simple local usage counter / feedback prompt so we learn which command repeats (no data leaves phone unless user shares)
3. Day-3 and day-7 check-in with each tester; capture their words

## Kill rule
If after 7 days fewer than 3 of 10 testers use it on 4+ days, the launcher concept is dead; pivot the wedge or drop.
