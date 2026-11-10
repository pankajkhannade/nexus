# Nexus privacy

Nexus is a family co-pilot for a parent's Android phone. It has no account, no server and no analytics. The in-app Privacy screen (Family settings → Privacy) says the same thing in English, Hindi and Marathi.

## What is saved, and where
- The people you add (name, number, relation, spoken alias), the home address and its coordinates, the optional office address, the home pincode, the language, the child's picked apps for Near home, the last cab app used, and simple usage counts are stored **only on this phone**.
- That data is excluded from Android backup and device-to-device transfer.
- Usage counts are numbers per day. They cover app opens, calls, voice commands (and ones that were not understood), messages checked, asked family, rides, WhatsApp opens, dictated messages handed to WhatsApp or SMS, and Near home tile opens (groceries, medicines, doctor, ride, home help, emergency), plus the day-3 and day-7 feedback answers. They never include names, phone numbers, message text, app names or places.

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

## Nothing is shared by itself
- A call, WhatsApp or SMS message, cab booking, Maps search, Play Store page or usage summary leaves the phone only when the parent (or family) presses Call, Send or Share in the other app. Nexus pre-fills; a person always sends.
- Emergency opens the dial pad with 108 ready. It never places that call by itself.
- "Export my usage" shows the exact text first, then opens the Android share sheet.

## Rides to a place
- When the parent says or types a place ("स्वारगेटला जायचं"), the phone's own location service (Android Geocoder) looks it up near the home coordinates, which may use the internet. Nexus doesn't keep the results after the ride screen closes.
- The chosen place is handed to the cab app (Uber gets the coordinates and a short name; Ola, Rapido and Namma Yatri get the address on the clipboard) or to Google Maps. The parent confirms the ride there.

## Internet use
- **Address suggestions** while typing a home address send the typed text to a map search service (ArcGIS geocoding suggest) to list matches.
- **Placing home on the map:** the saved address is turned into coordinates by the phone's own location service (Android Geocoder), which may use the internet. The coordinates are stored only on the phone and are used to prefill Uber's drop-off and to centre Near home Maps searches.
- **Speech**, as above, when on-device recognition isn't available.

## Other apps
- Opening Uber, Ola, Rapido, Namma Yatri, Maps, the dialer, WhatsApp, SMS, a grocery, medicine, doctor or home-service app, or the Play Store, hands the address, number, search or package name to that app. Fares, calls, orders and messages are confirmed there.
- Nexus does not read the screens of other apps and cannot see cab fares or place orders in Blinkit, Zepto, Instamart or similar apps.
- Contacts: in setup, people are chosen with the system contact picker, which needs no permission. Calling or messaging anyone else by name uses the phone book permission above.

## Permissions
- Microphone: only while Speak is pressed.
- Phone calls (`CALL_PHONE`): to place a call after the visible 3-second Cancel. Without it, the dialer opens instead. Emergency (108) always uses the dial pad, not this permission.
- Contacts (`READ_CONTACTS`): only if allowed, to call or message anyone in the phone book by name. Read on the phone, kept in memory, never uploaded or saved.
- Internet: for the address, place and speech services above.
