# Nexus UX critique and design principles (2026-11-11)

Page: https://cursor.com/canvas/store-03dca8d3-65f6-4f2a-8a93-ac8d74b2bd01/ac7e81ab-2ca6-433d-8d19-9ca5a491aa3d
Evidence: `screens/phase1-parent/01-44`, `docs/design.md`, `docs/roadmap.md`, code on origin/main (4eedc4e). Screen numbers in brackets.

## Verdict
Strong bones, leaky edges. One-job screens (14 countdown, 30 message, 37 Which one?, 38 Where to?) are good for a 60+ parent. Nexus loses them at the edges: identical faces, a home that scrolls, failures that hide what was heard, and handoffs to other apps where Nexus's care stops.
Caveats: 16/17 predate the WhatsApp and Near home buttons now on home in code (crowding is worse). 20/21 captured emulator ANR dialogs, not Nexus states; re-capture 21.

## 1. Journey map
### Child
| Stage | Screens | What we see | Change |
|---|---|---|---|
| Discover / install | none | Not judgeable yet | Write the one-line promise and forwardable WhatsApp invite; Play screenshots in Marathi with a face |
| Language | 01, 02 | Clear, Hear it samples. Missing-voice banner pushes Continue below fold; download inside setup | Pin Continue; one download button with progress; say what skipping means |
| People | 03-06 | Phone-book pick + nickname is the best idea in setup. Toggle label hits switch (04); red duplicate error lingers (05); one feature has 3 names: Is this real? / Ask family / Ask Rahul | One name; clear resolved errors; offer Add to phone contacts |
| Home | 07 | "Shivaji Park, Dadar, Mumbai" suggests a US restaurant in Altoona, PA. Pincode asked again in 35 | India-only suggestions; pincode once |
| Permissions | 08, 09 | Explainers are right; jargon (dial pad, 3-second Stop button) | One plain line each |
| Handover | 10, 11 | 08, 09, 10 all say Step 4 of 4; 10 empty, no coaching | Honest steps; "stand next to them, your phone will ring" |
| Settings | 24, 25 | Icon-only ↑; Remove beside Edit; "Permissions: off" + generic button; `calls=1`, `Language: en` dump | Group; per-permission fix; sentences not key=value |
| Is it working | 39-44 | Honest counts but no top-line answer; consent pages clear | Lead with "Aai used Nexus 6 of 7 days, 1 problem" |
| All fine / remote fix | none | Doesn't exist yet | Phase 2 child app; interim weekly summary on request |

### Parent
| Stage | Screens | What we see | Change |
|---|---|---|---|
| First open | 11 | Exact words to say, Marathi, nickname. Good. Shows "R" while prompt says Bunty | Show the right face |
| First success | 11, 14, 15 | Spoken countdown + big Stop. Dialer then shows +91 number and pink placeholder | Save family to phone contacts at setup |
| Home | 16, 17, 26, 27 | 2 people above fold; actions below; feedback card pushes family down; settings hint visible to parent; Pankaj and Priya both blue "P" | Fixed screen: 4 distinct faces, 3 tiles, sticky Speak |
| Message | 36, 30, 31 | Live words and big draft are the best screens. Done vs Stop ambiguous; "Online: Google…"; "Send बटण" | झालं / रद्द करा; move tech note to Privacy |
| Ride | 22, 23, 38 | 38 excellent. 22 asks the parent to paste. 23 no dead end | Deep link with drop-off; else spoken paste coach |
| Near home | 32-35 | Labelled tiles. Subtitles leak plumbing; Back off-screen (33); 108 via dial pad (34) | Outcome subtitles; speak the green-button step |
| Errors | 12, 13, 37 | 13 calm fallback and 37 last-4 digits strong. 12/13 never show what was heard and say "photo" with no photos | Always show ऐकलं: … |
| Help | 17, 18, 23 | Help only inside flows; nothing on home | Permanent मदत / call child |
| Scam check | 18-20 | Clear verdict and reasons; Ask Rahul below fold; nothing for "I already paid" | Sticky action; पैसे गेले path (N3) |

## 2. Ranked fixes
| # | Problem | Sev | Effort | Screens | Why |
|---|---|---|---|---|---|
| 1 | Faces identical: letter avatars, one blue, no photos | High | M | 16, 24, 26, 30, 34 | design.md principle 3 fails; parents pick by face/colour |
| 2 | Home doesn't fit one screen; feedback card and hint compete | High | M | 16, 17, 26, 27 | Below-fold needs scrolling knowledge; breaks one screen one job |
| 3 | Not-understood hides heard words; mentions photos that don't exist | High | S | 12, 13 | Can't tell what went wrong; 37/38 already do it |
| 4 | Safe action below fold on scam verdict | High | S | 18, 19 | The moment that earns the subscription |
| 5 | Handoffs lose the thread (number in dialer, paste in cab, Send in WhatsApp) | High | M | 15, 22, 30 | Trust ends where Nexus's screen ends |
| 6 | Address autocomplete returns a US place | High | S | 07 | Trust lost in minute 2; breaks rides |
| 7 | Done vs Stop both read as finish | Med | S | 36 | Wrong tap loses a long dictation |
| 8 | Fixed 3-second countdown | Med | S | 14 | WCAG 2.2.1 Timing Adjustable |
| 9 | One feature, three names | Med | S | 03-05, 18, 24 | W3C consistent identification |
| 10 | Back is a small text link, top vs bottom; Speak only on home | Med | M | 03, 12, 22, 30, 33, 37 | Same place for same thing; below 64dp |
| 11 | Mixed scripts/digits (Rahul ला, Send बटण, Devanagari digits only in metrics) | Med | S | 12, 14, 30, 34, 40 | Script switches mid-word |
| 12 | Dishonest step count; banner hides Continue | Med | S | 02, 08-10 | Setup abandonment |
| 13 | No help route from home | Med | S | 16, 17 | Child is the support channel |
| 14 | Feedback Q2 needs typing | Med | S | 27 | Typing is hardest input |
| 15 | Settings: ↑ alone, Remove by Edit, key=value dump | Med | S | 24, 25 | Wrong Remove deletes a contact |
| 16 | Near home subtitles show vendors/plumbing | Low | S | 32, 33 | Say the outcome |
| 17 | Handover doesn't coach the child | Low | S | 10 | Scripts the magic moment |
| 18 | Metrics buries the answer | Low | S | 39-41 | One actionable line first |
| 19 | Emoji as icons vary by OEM | Low | M | all | One icon set, always with a word |
| 20 | Screenshot set stale (16, 17, 21) | Low | S | 16, 17, 21 | Re-capture before next review |

## 3. Principles to adopt (extends design.md 3.1)
| Principle | Today | Evidence | Change |
|---|---|---|---|
| **Heard → said back → doing → done** | Partial | 37, 38 show heard; 12, 13 don't; no done after handoff | Heard line + spoken read-back everywhere; done line |
| **Continuity across handoffs** | Fail | 15, 22, 30 | Contacts insert, deep links, spoken coach, "did it work?" on return |
| **Same place for the same thing** | Fail | Back top (03) vs bottom (12, 22, 37); Speak only on home | One bottom bar: मागे + बोला, 64dp, every parent screen |
| Recognisable at a glance | Fail | 26 | Photo or distinct colour; parent's script |
| One job per screen | Partial | 30, 14 pass; 18, 24, 39 fail | One pinned primary |
| Never a dead end | Partial | 23, 13, 37 pass; 15, 20, 21 | Back, Speak, Ask child everywhere |
| Undo window | Pass | 14, 30, 37 | Adjustable 3/5/8 s |
| Forgiving input | Partial | 04, 37 vs 07, 27 | Speak any answer; India-only places |
| Large text, big targets, tested at 200% | Partial | No font-scale tests in suite | Roborazzi at 1.0/1.5/2.0, 360x640 |
| Every icon has a word | Partial | 32 pass; 24 ↑ | Lint clickable-without-text |
| Plain local language, one script | Partial | 30, 09, 36 | Native review; banned-words list |
| The child is the support channel | Partial | 18, 23; not home | Permanent help |
| No surprise permissions | Pass | 08, 09 | Per-permission fix in 25 |
| Calm errors | Pass | 13, 23; 05 lingers | Clear resolved errors |
| Safe path is the easy path | Partial | 18 | Sticky verdict action |
| Honest progress and time | Fail | 08-10 | Count real steps |
| Design for shaky hands | Partial | 24 Edit/Remove | 16dp gaps; destructive away; ignore double taps |
| Privacy you can see | Pass | 28, 29, 42-44 | Show on home when uploads are on |
| Test with real 60+ users | Fail | none yet | 5 moderated sessions per language before closed test |

## 4. Smile or sigh
| Moment | Screens | Smile | Sigh |
|---|---|---|---|
| Try it, child's phone rings | 11, 14 | If Marathi voice works | Silent if 02 banner skipped |
| First not-understood | 12 | Calm copy, faces | No idea what was heard; "photo" with none |
| Daily call | 16, 14, 15 | Big green Call, spoken countdown | Dialer shows a number |
| Is this safe? | 18, 19 | Clear red Stop, Marathi reasons | Scroll to find Ask; nothing for "already paid" |
| Dictated WhatsApp | 36, 30 | Own words, big, To: Pankaj | Done vs Stop; find green Send elsewhere |

## 5. Next design sprint
| Change | Ships | Acceptance checks |
|---|---|---|
| 1. Recognisable faces | Contact photo, else distinct colour from fixed 8 (white text ≥4.5:1); optional Devanagari display name | Unit: same-initial people get different colours. Compose: avatar semantics name. Roborazzi home mr |
| 2. Home on one screen | 2x2 faces + Call, 3 labelled tiles, sticky Speak; hint hidden after day 1; feedback card after a success, below family | Compose on 360x640dp @1.0: 4 people, 3 tiles, Speak assertIsDisplayed, no scroll; @2.0 Speak + person 1. Roborazzi en/mr @1.0/1.5/2.0 |
| 3. Voice contract | ऐकलं on every result/failure; spoken read-back before acting; झालं / रद्द करा; countdown 3/5/8 | Compose heard-text node on not-understood; fake voice speak before intent; 5 s setting starts at 5; Roborazzi mr |
| 4. Safety-first verdict | Ask <child> pinned bottom bar; collapsed scam text; पैसे गेले secondary → N3 | Compose small qualifier: Ask displayed no scroll (mr, hi); पैसे गेले shows 1930; Roborazzi |
| 5. Handoff continuity | Setup offers Add to phone contacts (ACTION_INSERT, no permission); spoken pre-handoff line; ride prefers drop-off deep links else spoken paste coach | Robolectric: ACTION_INSERT extras; first ride intent carries drop-off; Roborazzi coach mr |

Sprint 2: India-only address + single pincode, honest steps, handover script, persistent bottom bar.

## What only real users can tell us
- Whether 3 s is enough to find Stop (and whether 5 s feels slow).
- Google recognition of 60+ Marathi/Hindi on Rs 10-25k phones; whether the restart beep confuses.
- Speak vs tap after novelty fades.
- Literacy: how much rests on voice and icons.
- Whether curious parents find the 2 s hold and break settings.
- Completion of the Ola paste step and WhatsApp Send step.
- Emoji/Devanagari rendering on Samsung, Xiaomi, Vivo budget phones at large font.
- Whether the feedback card annoys; how handover feels socially.

## Sources
- NN/g, Usability for senior citizens: https://www.nngroup.com/articles/usability-for-senior-citizens/
- Systematic review of 40 usability studies with older adults: https://pmc.ncbi.nlm.nih.gov/articles/PMC10557006/
- W3C WAI, Developing websites for older people: https://www.w3.org/WAI/older-users/developing/
- WCAG 2.2 SC 2.2.1 Timing Adjustable: https://www.w3.org/WAI/WCAG22/Understanding/timing-adjustable.html
- Android touch target size: https://support.google.com/accessibility/android/answer/7101858
Mobbin not used this pass (design.md 2.1 already holds Mobbin references).
