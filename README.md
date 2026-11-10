# Nexus

Nexus reads a message on your phone and opens Uber, Maps, Messages, WhatsApp, Mail, or Calendar after you confirm. Notes, people, and places stay in the browser. Nothing is uploaded.

## Run

```bash
npm install
npm run dev
```

Open the app in Chrome on Android, then use Install or Add to Home screen.

## What it does

- Pulls a place, phone, email, or time out of ordinary text.
- Reuses a saved person when you mention their name.
- Uses a saved place such as Home or Office.
- Light theme by default, with a dark theme.
- Uses Chrome’s built-in on-device model when the phone has one. Otherwise the reader still works.

Nexus cannot press the last button inside another app, and it does not install a 3B or 30B model.
