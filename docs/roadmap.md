# Nexus roadmap: evidence first (2026-11-10)

Owner ask (Pankaj, 2026-11-10): "critically now think the gaps and the customer needs and basis detailed research from customer pain points from competitors are solving and reddit or such similar forums are mentioning, provide me a roadmap what needs to be build now".

Scope: Nexus is an Android family co-pilot for Indian parents aged 60+ on Rs 10-25k phones. The payer is the adult child (25-40) in another city or country. Built: parent mode, voice calls, dictated WhatsApp/SMS, ride home, Near home, "Is this safe?", Ask family, child-assisted setup, usage export, feedback cards, privacy. In progress: voice cut-off fix, phone-book contact matching, rides to any destination, voice-architecture and remote-help research.

## Summary

1. The pain is real and widely reported. Children describe phone support as "a part-time job" and a "45-minute tech support shift". Parents lose savings to digital-arrest and KYC-OTP scams, and to remote-access apps such as AnyDesk.
2. Much of the parent-side tooling is now free and good enough. Gemini (free in India, nine Indian languages including Marathi) can place calls and send WhatsApp messages. Truecaller Family Protection (free, in India since March 2026) lets the child hang up a scam call on the parent's phone. Google's scam alerts are coming to Xiaomi, vivo, OPPO, realme and other phones in this price band. WhatsApp has free screen sharing.
3. What children actually pay for is a person or signal that notices a bad day: Sentio (Rs 299-699 a month per parent for a daily AI call), Emoha (Rs 1,999-10,999 a month), Samarth (Rs 10,999-16,999 a month). Senior launchers earn far less: BIG Launcher's full version costs $13.99 a year, and BaldPhone is free and gone from Play.
4. Thesis: Nexus wins by being the parent's home screen. The parent speaks in their own language, Nexus does it on the first try, the child can see "all fine" and fix the phone from far away, and in the golden hour after a scam it pulls the child in. The voice is how the parent uses it; the family link is what the child pays for.
5. Counter-thesis: parents don't adopt new apps (Sentio and Athnova sell "nothing for them to learn"), and Google plus Truecaller give the rest away free. Nexus fails if, in the closed test, its voice is no better than free Gemini, or parents don't open it on 4 of 7 days.
6. Changes to the plan: drop our own call-screening scam detector (Phase 3 headline) and our own remote control. Move the child's "all fine" signal and remote configuration up to be the core of Phase 2.
7. The single most important thing to build now: make the Speak button work 9 times out of 10 on real parents' Marathi and Hindi, on their own phones. That means a recorded test set from the test families, scored on every build and also run against free Gemini.

## How this relates to docs/voice-architecture.md

The voice and remote-help research is in [docs/voice-architecture.md](voice-architecture.md) (merged on 2026-11-10). This roadmap doesn't redo it. N1 below is that doc's plan V1-V6 (session fix, phone book, ride to anywhere, a recorded test set of 60+ speakers, a bake-off against Sarvam and Gemini, opt-in fallbacks). N5's "Help me" is its V7 (coaching for WhatsApp screen share, an optional AnyDesk or QuickSupport tile picked by the child, a scam guard). This roadmap adds the customer evidence, the competitors, and what sits around voice.

## What I could and could not verify

- Play Store review pages render with JavaScript, so I read reviews through AppBrain's mirror of the Play listing (BIG Launcher, BaldPhone). Play listings themselves were read for Oscar Family.
- Emoha's own plans page returned 403. Its prices come from search snippets of emoha.com/plans and two third-party reviews (CareForAmma, and Athnova, which is a competitor and says so).
- I found no published prices for Seniority, Alserv or Anvayaa. I did not research Jio/Airtel senior plans in depth; nothing relevant to Nexus came up.
- Reddit quotes are verbatim from the threads linked. Upvote counts are small (single or low double digits), so treat each thread as one family's story and the pattern across threads as the evidence.

## Evidence by customer need

Strength = independent sources found (Reddit threads, forums, reviews, news, studies). Solved = whether a competitor already solves it well. Policy = whether Android and Play allow Nexus to build it. Moat = fit with voice in Marathi/Hindi plus a two-household family link.

| # | Need | Strength | Solved by a competitor? | Policy | Moat | Verdict |
|---|---|---|---|---|---|---|
| 1 | Remote family IT: the child fixes the phone from afar | Strong (6+): r/AgingParents, r/nri, r/GadgetsIndia, r/AndroidQuestions, Team-BHP, Oscar | Partly. TeamViewer QuickSupport, AnyDesk and WhatsApp screen share are free. Oscar does remote config, not control | Full remote control needs AccessibilityService and a Play declaration; autonomous actions are banned. Screen share through WhatsApp needs no permission | High for remote config of Nexus; low for screen control | Now: "Help me" via WhatsApp screen share. Next: remote config. Kill: our own remote control |
| 2 | Scams: digital arrest, KYC OTP, remote-access apps | Very strong (10+): r/LegalAdviceIndia, r/personalfinanceindia (3 threads), Scroll, Pune police, Kochi, Mangaluru and Jodhpur reports, RBI paper | Call level: yes, free. Truecaller Family Protection and Google Scam Detection (expanding to budget brands). Post-incident golden hour: no one | Dial pad and share target fine. Call screening role possible but commoditised | High for "pause and pull in family" and the golden hour; low for call classification | Now: golden-hour button and remote-access app alert. Kill: our own call screener |
| 3 | Voice that understands the parent | Strong: Pankaj's test (recorder cuts off), Google community thread on Marathi, a Gemini review on accents, Team-BHP (voice typing works for mom), r/nri (native language helps) | Gemini is free in Marathi and Hindi and can call and WhatsApp (chats only, not Live). Open-ended, no family relations, no read-back flow | RECORD_AUDIO fine. On-device ASR models (Sarvam Edge) are OEM-partnered and benchmarked on flagships | Core | Now: number 1 |
| 4 | Reachability: phone on silent, Wi-Fi off, airplane mode | Medium (4): BIG Launcher 5-star review (92-year-old mother), r/techsupport, Juno blog, Truecaller shares sound status | Juno restores volume; Truecaller shows status to the admin | Apps can raise ringer volume; DND changes need notification-policy access; apps can't toggle airplane mode or Wi-Fi on Android 10+ | Medium; high once the child sees it (Phase 2) | Now: ringer guard. Next: status in the child's app |
| 5 | Video calls and loneliness | Medium-strong: HelpAge 2025 (47% of elders name loneliness), BaldPhone 5-star review ("he cried"), BIG Launcher 2-star review asks for WhatsApp voice/video tiles | WhatsApp itself; launchers lack WhatsApp video tiles | WhatsApp video intent via contacts data rows is undocumented: works with READ_CONTACTS, may break | Medium (voice: "Pankaj la video call kar") | Now: video call button and command |
| 6 | Accidental changes, lost home screen | Strong (5): r/AndroidQuestions (grandmother), BaldPhone GitHub issue, BIG Launcher review, Juno, Team-BHP | Yes: BIG Launcher, Juno and ONY have admin PINs and locked layouts | Launcher (home role) is fine on Play | Medium; but being the home screen drives daily opens | Now: optional home-screen mode with a PIN on settings |
| 7 | Living alone, falls, "who does Amma call at 2am" | Strong: r/india (63-year-old fall), r/indiasocial, r/NRIWithParentsInIndia, Emoha, Sentio, Athnova | Yes, and people pay: Emoha, Sentio, Athnova, wearables | A passive "phone used today" signal is fine; fall detection from a phone in a handbag is unreliable | High: the family link is the product | Next: daily "all fine" signal. Kill: phone fall detection |
| 8 | Medicine reminders | Medium-strong: r/nri, r/eldercare, r/india, Sentio, BaldPhone, Oscar | Yes: Sentio (calls), Oscar, BaldPhone, alarms | Exact alarms need user-granted permission on Android 14+; inexact is fine for pills | High once the child sees adherence | Next |
| 9 | Storage full of "Good Morning" forwards | Medium (2): r/IndiaTech, Reddit search results | Files by Google, WhatsApp storage manager (free) | Scoped storage blocks deleting WhatsApp media | Low | Next: show it in the child's phone-health view only |
| 10 | WhatsApp forwards and misinformation | Medium (3): r/indiasocial, r/Coconaad, WhatsApp help | Fact-checkers; nobody inside the family flow | A true/false verdict is a liability | Medium (Ask family already covers it) | Keep Ask family; no verdicts |
| 11 | Finding apps for services (grocery, medicine, doctor) | Weak-medium: Pankaj's own view, r/nri ("no reliable service platforms") | Partly (Gemini can connect Urban Company, in English, in India) | Opening apps is fine; ordering for them is not | Medium | Built (Near home). Don't expand until testers use it |
| 12 | OTP and payment confusion | Medium (3): Team-BHP ("Copy OTP"), Axis FD thread, RBI trusted-person paper | Banks, UPI Circle; RBI rule not final | Nexus cannot read SMS unless it is the default SMS app | High later (trusted person) | Later: trusted-person approval if RBI finalises |

## Voices from the evidence (verbatim)

Remote family IT
- "Every time he says "I just want to check the news," I know I've got a 45-minute "tech support shift" coming" - r/AgingParents, https://www.reddit.com/r/AgingParents/comments/1onm9qy/helping_my_79yearold_dad_with_tech_is_turning/
- "If I were there, I could fix most of these things in minutes—but from abroad, it's just not possible." and "Half the battle is even realizing something isn't working properly" - r/nri, https://www.reddit.com/r/nri/comments/1ids6qg/how_do_you_help_parents_with_tech_service_issues/
- "sometimes she accidentally sends messages to the wrong person, makes unintended video calls, or even posts stories without realizing it. There's no one around to help her delete those mistakes." - r/GadgetsIndia, https://www.reddit.com/r/GadgetsIndia/comments/1kr065r/is_there_any_app_to_remotely_access_a_mobile/
- "you will get unattended access after one time setup — so you don't need her approval every time." (a reply recommending TeamViewer; this is also exactly what scammers exploit) - same thread
- "The clever way to "Copy OTP" from messages was something my mom could not reconcile. She will still disconnect the call, scan the SMS, then call me back." and "I taught my mom to use Google Voice typing. It is absolutely incredible" - Team-BHP, https://www.team-bhp.com/forum/gadgets-computers-software/231726-making-android-smartphone-truly-accessible-senior-citizen.html
- "The designs on touch screens are for people with good vision, good manual dexterity and the ability to quickly change modes and context. I.e. not elderly people." - r/AgingParents (same thread)

Scams
- "My father was recently a victim of a Digital Arrest scam and lost nearly 1 crore INR across 4 transactions." and "Both my parents live alone" - r/LegalAdviceIndia, https://www.reddit.com/r/LegalAdviceIndia/comments/1k5rrof/victim_of_digital_arrest_scam_journey_after/
- "Someone Video called my father (via WhatsApp)in police dess pretending to CBI officer" - r/personalfinanceindia, https://www.reddit.com/r/personalfinanceindia/comments/1h9lgw7/scam_father_lost_9lacs_can_we_do_anything/
- "Trusting them, my grand father shared an OTP that came to his phone. Within minutes, his FD was broken" - r/personalfinanceindia, https://www.reddit.com/r/personalfinanceindia/comments/1or4gxs/my_69yearold_grand_father_lost_his_541_lakh_fixed/
- "Experts say that tracing the money becomes difficult if the crime is not reported within 24 hours." and "it is advisable to create an ID even if someone has not been defrauded." - Scroll, https://scroll.in/article/1078480/my-father-lost-rs-10-lakh-in-a-digital-arrest-scam-heres-how-he-got-some-money-back
- Remote-access apps used to loot elders: Pune police (https://indianexpress.com/article/cities/pune/remote-access-apps-cyber-crime-pune-police-7937056/), Kochi (https://www.newindianexpress.com/cities/kochi/2022/Dec/11/cyber-fraudsters-employing-remote-access-apps-to-loot-elderly-persons-2526984.html), Mangaluru (https://www.thehindu.com/news/cities/Mangalore/new-con-in-town-beware-of-installing-remote-desktop-applications-on-your-mobile-phone/article66246943.ece)

Reachability and calls
- "My mother often reduce volumes to minimum by mistake. Then she does not hear call rings." and "My mother often switches off Wi-Fi or on airplane mode by mistake." - BIG Launcher 5-star review, https://www.appbrain.com/app/big-launcher-for-seniors/name.kunes.android.launcher.demo
- "My mother always put her android phone on silent unknowingly, while trying to lower media volume." - r/techsupport, https://www.reddit.com/r/techsupport/comments/jeneiy/my_mother_always_put_her_android_phone_on_silent/
- "Functions needed: - WhatsApp integration. Lots of people call via WhatsApp." and "easy toggle between voice and video" - BIG Launcher 2-star review (same link)
- "He cried when we sat down with the tablet (pre bald phone install), he couldn't read icons" - BaldPhone 5-star review, https://www.appbrain.com/app/baldphone-elderly-senior-acc/com.bald.uriah.baldphone.gp

Living alone
- "She was on the floor for about 10 minutes before she managed to get up and call my aunt" and "I didn't even find out until after she was back from the hospital" - r/india, https://www.reddit.com/r/india/comments/1nxtxfr/mom_63_had_a_serious_fall_while_home_alone_i_live/
- "If your main fear is "who does Amma call at 2am?", Emoha answers that question." - CareForAmma, https://www.careforamma.com/guides/emoha-review/

Storage and forwards
- "Their storage is always full, mainly with "Good Morning" forwards, videos of temples" - r/IndiaTech, https://www.reddit.com/r/IndiaTech/comments/1mmmuyd/how_do_i_tell_my_parents_to_manage_their_phone/
- "Her response: "Arre, chalta hai. Itna serious mat ho."" - r/indiasocial, https://www.reddit.com/r/indiasocial/comments/1t1li2u/my_mom_forwards_misinformation_on_the_family/

Adoption (counter-evidence)
- "I tried to set it up for my grandma, but failed." - BaldPhone GitHub issue 1247, https://github.com/UriahShaulMandel/BaldPhone/issues/1247
- "No app for them to learn, nothing to wear." - Sentio, https://sentiocare.in/
- "Your parent installs nothing." - Athnova, https://athnova.in/guides/emoha-alternatives/

## Competitors and pricing

| Product | What it does for families | Price | What delights | What fails |
|---|---|---|---|---|
| Gemini on Android | Free Gemini Live in 9 Indian languages incl. Marathi; Phone and WhatsApp connected apps place calls and send messages (chats only) | Free | Natural voice, Indian context | "heavy local accents can still lead to occasional comprehension slips"; open-ended; no family roles |
| Truecaller Family Protection | Admin of up to 5 gets scam-call alerts, can end a member's call (Android), sees battery and sound mode, shares blocklists | Free | The child can act during the call | Call channel only; no WhatsApp or SMS scams; no post-incident help |
| Google Scam Detection | Call and message scam alerts; chat-notification alerts coming to Xiaomi, vivo, OPPO, realme, Motorola and others; screen-share blanking on Android 15+ | Free | Built in | Rolling out unevenly by brand and phone |
| WhatsApp screen share | Share the screen in a video call | Free | Already on the parent's phone | Parent has to find the menu |
| TeamViewer QuickSupport, AnyDesk | Full remote control | Free personal | Child fixes it directly | The same apps are the top scam tool against elders |
| Oscar Family | Simplified tablet UI, family adds apps and medicine reminders remotely | 14-day trial, then paid | Remote config by family | 3.4 stars on Play; no screen control |
| BIG Launcher | Big-button launcher, SOS; 2.1M installs, 4.1 stars | US $3.49 a month, $13.99 a year, $40.99 lifetime | Simplicity, low vision | No WhatsApp video tiles; can't lock volume or Wi-Fi |
| BaldPhone | Free open-source launcher, SOS, pill reminders | Free; off Play since March 2024 | Ad-free, gentle | Frozen screens, missing icons, setup failures |
| Samsung Easy mode, Apple Assistive Access | Built-in simple modes | Free | No install | Samsung-only; iPhone-only |
| Google Family Link | Child supervision | Free | - | Not for adults over 18 |
| Sentio Care | Daily AI voice call in Hindi or English, WhatsApp summary to family | Rs 699 a month per parent (Rs 299 launch) | "Nothing for them to learn" | Hindi and English only; one call a day |
| Athnova Care | Daily WhatsApp check-in in the parent's language, alerts | Free for one parent; $12 a month | Parent installs nothing | Self-reported only |
| Emoha | 24/7 helpline, coordinator check-ins, emergency response | Rs 1,999-10,999 a month | The 2am helpline | Coverage varies by city; coordinator churn |
| Samarth | Care managers, home visits | Rs 10,999-16,999 a month | Someone at the door | Price |
| GoodFellows | Young companions for seniors | Rs 5,000 a month | Companionship | City-bound |
| Khyaal | Senior community, events, games | Rs 99 a month, Rs 349-1,499 a year | Content and community | Not phone help |

Sources: https://tech.getinfotoyou.com/google-gemini-live-indian-languages-free-android, https://support.google.com/gemini/table/17434654?hl=en, https://techcrunch.com/2026/03/12/truecallers-now-lets-you-hang-up-on-scammers-on-behalf-of-your-family/, https://blog.google/security/new-android-scam-protections/, https://www.teamviewer.com/en-in/solutions/roles/personal-use/, https://play.google.com/store/apps/details?hl=en&id=com.oscarsenior.family, https://biglauncher.com/pricing/en/, https://www.appbrain.com/app/baldphone-elderly-senior-acc/com.bald.uriah.baldphone.gp, https://support.apple.com/guide/assistive-access-iphone/add-remove-and-customize-apps-devd7d3e3b512/ios, https://families.google/familylink/faq/, https://sentiocare.in/, https://athnova.in/guides/emoha-alternatives/, https://www.careforamma.com/guides/emoha-review/, https://care.samarth.community/care-plans-for-elderly/, https://www.thehindu.com/business/Industry/no-one-minds-getting-old-till-ratan-tata-invests-in-senior-citizen-companionship-startup/article65775014.ece, https://www.legacy.khyaal.com/

## Thesis

Nexus wins if it is the screen the parent sees every time they unlock their phone. The parent speaks in their own language and the simple thing happens on the first try. The child, from another city, can see "all fine", fix the parent's setup, and get pulled in within minutes when something goes wrong (a scam call, a silent phone, a missed day).

The parent's voice is how they use Nexus. The two-household family link is what the child pays for. Price it against Sentio (Rs 299-699 a month) and the Emoha helpline (Rs 1,999 a month), not against launchers ($14 a year).

## Counter-thesis: how Nexus fails

1. Free Gemini already does "call Pankaj" in Marathi. If Gemini's success rate on real parents' commands matches Nexus's, the voice is not a moat. Test: run the same recorded command set through Gemini on the same phone during the closed test. If Gemini gets within 10 points of Nexus, Nexus's pitch becomes the family layer only.
2. Scam protection is being commoditised. Truecaller (free, remote hang-up) and Google (scam alerts on budget brands) cover the call itself. Our Phase 3 "risky-moment guard" as written would arrive late and free elsewhere. What's left for us: the moment after (the golden hour) and the remote-access app.
3. Parents don't adopt apps. Sentio and Athnova sell exactly "nothing to install", and BaldPhone's own issue tracker has "I tried to set it up for my grandma, but failed." If parents in the closed test don't open Nexus on 4 of 7 days, no feature list fixes it.
4. Children pay for people, not software. Every paid product here has a human or AI checking in. Pure software for seniors is free or about $14 a year. If no child in the closed test says they'd pay Rs 999 a year, the business is a daily-signal service, not an app.
5. Privacy versus value. The things children pay for (daily signal, remote config) need data to leave the phone. Nexus currently promises no server. Phase 2 must add an end-to-end encrypted relay without breaking that trust.

What survives: the family link (2, 4) and first-try voice for a closed set of family commands (1), as long as being the home screen solves the opening problem (3). What dies: our own call screener, our own remote control, any general assistant.

## Roadmap

### Now: next 2-3 weeks, before and during the Play closed test (6 parent-child pairs)

Goal: pass the kill rule (4 of 6 parents use Nexus on 4+ of 7 days) and get one real "Nexus helped" story per pair.

| # | Build | User problem | Evidence | Effort | Policy risk | Moves | Why here |
|---|---|---|---|---|---|---|---|
| N1 | Speak that works the first time, as planned in docs/voice-architecture.md V1-V6: a voice session that keeps the words already heard through pauses; the top 5 guesses matched against the phone book, relations and commands; confirm aloud before acting; the "Nexus Voice-60" recorded test set (target 90% first-try success), scored on every build and also run through free Gemini | Commands fail or cut off, so the parent gives up | Pankaj's test; Google community thread on Marathi; Gemini review on accents; Team-BHP | M | Low (RECORD_AUDIO; recordings stay on the phone and need consent) | Activation, D7 | Every feature goes through it; also tests counter-thesis 1 |
| N2 | Phone-book calling with "which one?" for duplicate names, plus a WhatsApp video call button and "Pankaj la video call kar" | Can only call the 4 saved people; family talks over WhatsApp video | Pankaj's feedback 2; BIG Launcher 2-star review; BaldPhone 5-star review; HelpAge loneliness 47% | S-M | Medium: READ_CONTACTS needs a disclosure and Data safety; the WhatsApp video intent is undocumented | D7 | The most frequent daily job; video is the emotional hook |
| N3 | "पैसे गेले / I think I've been cheated" button: one tap to 1930 (dial pad), a prefilled WhatsApp to the child, and the steps (call the bank, cybercrime.gov.in, keep the transaction IDs). Plus a remote-access app alert: if AnyDesk, TeamViewer QuickSupport or RustDesk shows up and the child didn't pick it in setup (voice-architecture.md V7 lets the child pick one), the parent sees "Did a caller ask you to install this? Call Pankaj first." and the child is told next time Ask family is used | After a scam, the first hours decide whether money can be frozen; scammers make parents install remote apps | Scroll (24 hours); r/LegalAdviceIndia; r/personalfinanceindia x3; Pune, Kochi and Mangaluru police reports | S | Low (dial pad, never an auto call; a named `<queries>` list, not QUERY_ALL_PACKAGES) | Child's willingness to pay, trust | Nobody else covers the golden hour; cheap to build |
| N4 | Optional home-screen mode (launcher role), with a PIN on Family settings, chosen by the child at setup | The parent has to find Nexus; accidental changes break the phone | BIG Launcher 2.1M installs; Juno admin PIN; r/AndroidQuestions; BaldPhone issue 1247 | M | Low (launchers are a normal Play category) | D7 | Directly attacks the top risk (no daily opens). This reverses the design.md call "don't replace the launcher in Phase 1", so it ships opt-in and we compare days used |
| N5 | Ringer guard and Help me: when Nexus opens and the ringer is silent or very low, or airplane mode is on, a big "आवाज बंद आहे" card with a one-tap fix (airplane mode opens Settings). "Help me" is voice-architecture.md V7: spoken coaching for WhatsApp video-call screen share, plus the optional AnyDesk or QuickSupport tile the child picks | Children can't reach parents; phone support by phone call is slow | BIG Launcher 5-star review; r/techsupport; Juno; r/nri and r/GadgetsIndia on screen sharing | S | Low (ringer volume is allowed; DND needs notification-policy access, so we only ask if needed) | D7, fewer support minutes | Small, visible wins for the child in week 1 |
| N6 | Closed-test measurement: on-device counts of command success and failure by type (no audio, no text) added to Export my usage; a 3-question child survey on day 14 including the Rs 999 a year question (asked only with Pankaj's yes) | We can't decide go or kill without numbers | plan.md kill rule | S | Low | All three | Without it the test proves nothing |

Already in flight and stays in Now: the voice cut-off fix, phone-book matching (part of N1 and N2), rides to any destination, release prep (signed AAB, upload key, privacy policy URL, Data safety), and the Play closed test (Console fee needs Pankaj's yes).

### Next: Phase 2, the paid family plan with the child's app

Goal: the child pays because they can see and fix things from far away.

| # | Build | User problem | Evidence | Effort | Policy risk | Moves | Why here |
|---|---|---|---|---|---|---|---|
| X1 | Child app and pairing by QR; remote configuration of Nexus (people, Near home apps, reminders, ringer guard) through an end-to-end encrypted relay | "There's no one around to help her delete those mistakes"; every fix needs a call | r/GadgetsIndia; r/nri; Oscar Family's model | L | Medium: first server, so Data safety and privacy copy change | Child's willingness to pay | The family link is the product; needs N1-N5 to have daily use first |
| X2 | Daily "all fine" signal: passive (Nexus used, phone unlocked, charging) with an optional one-tap "मी ठीक आहे"; no signal by a set time leads to a reminder, then an alert to the child | Living alone; nobody notices a bad day | r/india fall; Emoha "2am"; Sentio and Athnova exist and charge | M | Low-medium (background limits; no location) | Willingness to pay, retention | It's what people already pay Rs 299-1,999 a month for |
| X3 | Medicine reminders spoken in the parent's language, with "taken" visible to the child | Missed doses; the child can't know | r/nri; r/eldercare; r/india; Sentio; BaldPhone; Oscar | M | Medium (exact alarms need a user grant on Android 14+; use inexact windows) | Willingness to pay, D30 | High demand, and the child sees value |
| X4 | Phone-health view for the child: storage, battery, silent mode, Wi-Fi off, apps out of date, remote-access apps | "Half the battle is even realizing something isn't working" | r/nri; r/IndiaTech storage; BIG Launcher review; Truecaller shares sound status | M | Low | Willingness to pay | Cheap once X1 exists |
| X5 | Pricing test: Rs 999 a year against Rs 149 a month, with a family plan for 2 parents | Unknown willingness to pay | Sentio Rs 299-699 a month; Emoha Rs 1,999+; BIG Launcher $13.99 a year | S | Low (Play Billing) | Willingness to pay | Anchors the business; needs Pankaj's yes before any charge |

### Later

| # | Build | Why later |
|---|---|---|
| L1 | On-device Indic speech (Sarvam Edge-class, AI4Bharat models) if it can be licensed and runs on 4-6 GB phones; adopt only if it beats Google's recogniser on the N1 test set | Sarvam Edge is built with OEMs and measured on flagship chips; licensing is unclear |
| L2 | Trusted-person approval surface if RBI finalises it (70+, transfers above Rs 50,000) | Only a discussion paper (April 2026); no draft yet |
| L3 | "Explain this screen" from a shared screenshot (on-device OCR and rules, then Ask family) | Useful, but behind the daily-use basics |
| L4 | More languages (Gujarati, Tamil, Bengali) | After Marathi and Hindi prove out |
| L5 | Partnering with banks on verified calls if Google brings it to India | Google runs it in Brazil only today |

## Kill list: what not to build, and why

| Don't build | Why |
|---|---|
| Our own remote control (AccessibilityService) | Play bans autonomous actions and lists launchers and assistants as not accessibility tools; a declaration plus disclosure is heavy review; remote-control apps are the top scam tool against elders; WhatsApp screen share is free |
| Our own call-screening scam detector (the old Phase 3 headline) | Truecaller Family Protection (free, remote hang-up) and Google Scam Detection (coming to budget brands) already cover the call. We'd be late and second. Instead, setup turns those on |
| A general voice assistant or chat | Gemini Live is free in Marathi and Hindi. We win with a closed set of family commands that work first time |
| Fall detection from phone sensors | The phone is rarely on the body; false alarms; wearables and helplines own this |
| Ordering or paying in Blinkit, Zepto, Instamart | No public ordering APIs; Play bans driving other apps on the user's behalf |
| Location tracking of the parent | Google Maps location sharing and Truecaller already exist; it hurts the parent's dignity and trust |
| A storage cleaner | Files by Google is free; scoped storage blocks deleting WhatsApp media |
| True or false verdicts on forwards | Liability, and parents shrug ("chalta hai"). Ask family already covers it |
| Senior content, games and community | Khyaal owns it; it's not phone help |
| Depending on Gemini Nano | Its link checks need Pixel 10+, Galaxy S26 and similar, not Rs 10-25k phones |

## The single most important thing to build now

N1: the Speak button has to work 9 times out of 10 on real parents' Marathi and Hindi, on their own phones, the first time.

Why: every daily job (call, video call, message, ride, Near home) goes through it, so it decides activation and D7. It is the weakest part today (Pankaj's test). It is also the cheapest way to test the biggest counter-thesis: if free Gemini scores as well on the same recordings, we learn that in week 2, not month 6. Build it as a lever: a test set of recorded utterances from the six test families (with consent; stored on the phone or in the repo only with their permission), scored on every build, with a success rate shown in Family settings.

## Changes to docs/vision.md this implies (not made in this PR)

- Phase 2 leads with the "all fine" signal and remote configuration; the child's app is the paid surface.
- Phase 3 drops CallScreeningService as a headline. It becomes: the golden-hour flow, the remote-access app alert, and turning on Truecaller or Google protections at setup.
- Phase 1 adds N2-N6 above.
