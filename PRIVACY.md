# Nexus privacy

Nexus is a family co-pilot for a parent's Android phone. It has no account and no ads. Usage is counted on the phone; counts leave the phone only if the family turns on daily counts (off unless they say yes). The in-app Privacy screen (Family settings → Privacy) says the same thing in English, Hindi and Marathi.

## What is saved, and where
- The people you add (name, number, relation, spoken alias), the home address and its coordinates, the optional office address, the home pincode, the language, the child's picked apps for Near home, the last cab app used, and simple usage counts are stored **only on this phone**.
- That data is excluded from Android backup and device-to-device transfer.
- Usage counts are numbers per day. They cover app opens, calls, voice commands (and ones that were not understood), messages checked, asked family, rides, WhatsApp opens, dictated messages handed to WhatsApp or SMS, and Near home tile opens (groceries, medicines, doctor, ride, home help, emergency), plus the day-3 and day-7 feedback answers. They never include names, phone numbers, message text, app names or places.

## Usage counts (How Nexus is doing)
Nexus keeps a usage log **on this phone** so the family (and, if they choose, the Nexus team) can see whether it works. Every entry is a type, a time, numbers and fixed choices. Nothing else can be stored, because the log has no text field.

**Collected on the phone** (kept 90 days, at most 50,000 entries, excluded from Android backup):
- App opens and days used; crashes (a count only, never the error text).
- Setup: started, each step reached, finished, how long it took; whether the mic, phone book and call permissions were allowed.
- Voice: when Speak was pressed and why listening ended (Done, a whole command heard, quiet, nothing said, the 60 s limit, an error, cut off, Stop); how many **characters** were heard (not the characters themselves); how long until something happened; the app language (mr / hi / en); the kind of command (call, message, ride, Near home, video call…); whether it was understood; recogniser error codes; "which one?" shown; Stop / No / Back.
- Actions: the **kind** of app Nexus opened (dialer, WhatsApp, SMS, cab, maps, grocery, pharmacy, doctor, home service, Play Store, emergency) and how long until Nexus was back on screen.
- Near home tile taps and Play Store install hand-offs, by kind.
- Whether each action started from voice or a button.

**Never collected:** voice recordings, the words said, message text, contact names, phone numbers, addresses, places or destinations, app package names, the Android ID, the advertising ID, location.

**Not measured, on purpose:** whether a call connected (that needs the phone-state or call-log permission) and which app is on screen (that needs usage access). Nexus doesn't ask for either.

**Share test summary** builds a counts-only JSON summary (with the tester tag the child sets: family, friend or stranger) and opens the Android share sheet. Nothing goes until the family picks an app and sends it.

**Daily counts upload (opt-in, off by default).** Turned on only from the consent page in How Nexus is doing. When on, once a day (on any network, retrying later if offline) Nexus sends the **aggregate counts for finished days** (whole numbers under fixed names such as `voice_attempts` or `opened_cab`), the app version, the app language, the tester tag and a **random install ID** created on the phone when the family said yes. They go to the Nexus team's own server (a Cloudflare Worker with a D1 database at `nexus-metrics.safe2eat-api.workers.dev`). The server stores no IP address, no device details and no time of day, rejects anything that isn't a known count, and deletes rows after 180 days. Only the Nexus team can read them.

**How to stop or delete:** turn off "Send daily counts" (it stops at once), or tap **Delete all usage data** in Family settings > How Nexus is doing. That deletes the log, the daily counters and the feedback answers on the phone, turns the upload off and throws away the install ID (a later opt-in gets a new one). To delete counts already uploaded, ask the Nexus team with the date you turned it on; we delete them by hand during the test.

## Voice
- When the parent presses Speak, Android's speech recogniser turns the voice into words. On phones that support on-device recognition for the chosen language, this stays on the phone. Otherwise Google's speech service may use the internet. The Privacy screen shows which one applies on this phone.
- Nexus keeps the recognised words only long enough to act on them. It never saves a recording.
- On Android 13 and later, the names of your saved people and phone-book contacts are passed to the phone's speech recogniser as hints ("expect these names"). They go only to that recogniser, the same one that hears your voice.

## Phone book (contacts)
- When the parent says a name that isn't saved in Nexus ("सुनीलला फोन लाव"), Nexus asks to look in the phone book. It explains why in the parent's language before Android's own permission dialog. The child can also allow it during setup. Saying "Not now" just means only saved people can be called by name.
- With permission, Nexus reads contact names and numbers (plus "starred", to break ties) **on the phone**, keeps that list **in memory only** while the app is open, and matches the spoken name against it on the phone.
- Contacts are **never uploaded, never sent to any server or AI service, and never saved** to Nexus's storage or backups. Closing the app drops the list.
- Nexus does not ask for the call-log permission.

## Dictated WhatsApp and SMS messages
- The parent can say who to message, which app to use, and what to say. Nexus tidies the wording with on-device rules (it does not send the text to a server or an AI service).
- The draft is held in memory while the confirm screen is open. It is not written to storage. Cancelling drops it.
- When the parent taps the Send button in Nexus, the text is handed to WhatsApp (or WhatsApp Business) or to the SMS app, already typed in. The parent taps Send in that app. Nothing goes out if they don't.
- Nexus does not have the SMS send permission and cannot send a WhatsApp message by itself.

## Near home
- The home pincode and the home coordinates are stored only on this phone. The pincode is used to label the screen ("Services for 411045") and, when there are no coordinates, as a fallback for maps search. Nexus cannot check whether an app delivers to that pincode.
- Tapping Medicines, Doctor or the nearest hospital opens Google Maps with a search around the home coordinates (or the pincode or address if coordinates are missing). The search runs in Maps, not in Nexus.
- The green "Installed" tag uses Android's on-device package visibility for a fixed list of well-known apps declared in the app manifest. Nexus does not scan the whole phone or read other apps' data.
- An "Install" action opens that app's Play Store page (`market://details?id=…`, or the https Play page if the Play Store app is missing). Opening Play is the person's action.

## "Is this safe?"
- Messages and screenshots are checked only when the parent shares them to Nexus or pastes them in. Nexus never reads SMS, chats or notifications by itself and does not ask for SMS permission.
- Screenshots are read on the phone with ML Kit text recognition (Latin and Devanagari models bundled in the app). The rule checks run on the phone.
- The last screenshot is held in the app's private cache so it can be shared with family, and is deleted the next time Nexus starts.

## Nothing is shared without your yes
- A call, WhatsApp or SMS message, cab booking, Maps search, Play Store page or usage summary leaves the phone only when the parent (or family) presses Call, Send or Share in the other app. Nexus pre-fills; a person always sends.
- Emergency opens the dial pad with 108 ready. It never places that call by itself.
- "Export my usage" shows the exact text first, then opens the Android share sheet. "Share test summary" works the same way.
- The one exception is the opt-in daily counts above, and only after the family says yes on the consent page.

## Face photos
- A person's photo comes from their phone contact (when the phone has one) or from a picture the child picks with Android's photo picker. Nexus needs no storage permission for either.
- The photo is copied as a small picture (256 x 256) into Nexus's private storage on the phone. It is never uploaded, never sent to any service, and excluded from backups. Uninstalling Nexus deletes it.

## Saving family into the phone's contacts
- For people typed in by number, setup offers "Save in phone contacts". This opens the phone's own Contacts app with the name and number filled in; the child taps Save there. Nexus has no permission to write contacts and doesn't write them itself.

## Rides to a place
- When the parent says or types a place ("स्वारगेटला जायचं"), the phone's own location service (Android Geocoder) looks it up near the home coordinates, which may use the internet. Nexus doesn't keep the results after the ride screen closes.
- The chosen place is handed to the cab app (Uber and Ola get the coordinates and the address in their link, and Ola's address is also put on the clipboard as a backup; Rapido and Namma Yatri get the address on the clipboard) or to Google Maps. The parent confirms the ride there.
- Place searches are limited to India.

## Internet use
- **Address suggestions** while typing a home address send the typed text to a map search service (ArcGIS geocoding suggest) to list matches in India. In Family settings, the saved home's coordinates are sent too, so nearby matches come first.
- **Placing home on the map:** the saved address is turned into coordinates by the phone's own location service (Android Geocoder), which may use the internet. The coordinates are stored only on the phone and are used to prefill Uber's drop-off and to centre Near home Maps searches.
- **Speech**, as above, when on-device recognition isn't available.
- **Daily counts**, only if turned on (see Usage counts).

## Other apps
- Opening Uber, Ola, Rapido, Namma Yatri, Maps, the dialer, WhatsApp, SMS, a grocery, medicine, doctor or home-service app, or the Play Store, hands the address, number, search or package name to that app. Fares, calls, orders and messages are confirmed there.
- Nexus does not read the screens of other apps and cannot see cab fares or place orders in Blinkit, Zepto, Instamart or similar apps.
- Contacts: in setup, people are chosen with the system contact picker, which needs no permission. Calling or messaging anyone else by name uses the phone book permission above.

## Permissions
- Microphone: only while Speak is pressed.
- Phone calls (`CALL_PHONE`): to place a call after the visible 3-second Cancel. Without it, the dialer opens instead. Emergency (108) always uses the dial pad, not this permission.
- Contacts (`READ_CONTACTS`): only if allowed, to call or message anyone in the phone book by name. Read on the phone, kept in memory, never uploaded or saved.
- Internet: for the address, place and speech services above, and the opt-in daily counts.
- No phone-state, call-log, usage-access or advertising-ID access.
