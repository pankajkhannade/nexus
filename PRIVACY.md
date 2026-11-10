# Nexus privacy

Nexus is a family co-pilot for a parent's Android phone. It has no account, no server and no analytics. The in-app Privacy screen (Family settings → Privacy) says the same thing in English, Hindi and Marathi.

## What is saved, and where
- The people you add (name, number, relation, spoken alias), the home address, the language and simple usage counts are stored **only on this phone**.
- That data is excluded from Android backup and device-to-device transfer.
- Usage counts are numbers per day (calls, voice commands, messages checked, asked family, rides, WhatsApp opens) plus the day-3 and day-7 feedback answers. They never include names, phone numbers or message text.

## Voice
- When the parent presses Speak, Android's speech recogniser turns the voice into words. On phones that support on-device recognition for the chosen language, this stays on the phone. Otherwise Google's speech service may use the internet. The Privacy screen shows which one applies on this phone.
- Nexus keeps the recognised words only long enough to act on them. It never saves a recording.

## "Is this safe?"
- Messages and screenshots are checked only when the parent shares them to Nexus or pastes them in. Nexus never reads SMS, chats or notifications by itself and does not ask for SMS permission.
- Screenshots are read on the phone with ML Kit text recognition (Latin and Devanagari models bundled in the app). The rule checks run on the phone.
- The last screenshot is held in the app's private cache so it can be shared with family, and is deleted the next time Nexus starts.

## Nothing is shared by itself
- A call, WhatsApp or SMS message, cab booking or usage summary leaves the phone only when the parent (or family) presses Call, Send or Share in the other app. Nexus pre-fills; a person always sends.
- "Export my usage" shows the exact text first, then opens the Android share sheet.

## Internet use
- **Address suggestions** while typing a home address send the typed text to a map search service (ArcGIS geocoding suggest) to list matches.
- **Placing home on the map:** the saved address is turned into coordinates by the phone's own location service (Android Geocoder), which may use the internet. The coordinates are stored only on the phone and are used to prefill Uber's drop-off.
- **Speech**, as above, when on-device recognition isn't available.

## Other apps
- Opening Uber, Ola, Rapido, Maps, the dialer, WhatsApp or SMS hands the address or number to that app. Fares, calls and messages are confirmed there.
- Nexus does not read the screens of other apps and cannot see cab fares.
- Contacts: people are chosen with the system contact picker, so Nexus reads only the contact you pick and does not need the Contacts permission.

## Permissions
- Microphone: only while Speak is pressed.
- Phone calls (`CALL_PHONE`): to place a call after the visible 3-second Cancel. Without it, the dialer opens instead.
- Internet: for the address and speech services above.
