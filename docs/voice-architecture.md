# Nexus voice architecture: research and decision (2026-11-10)

Status: **decision proposed and adopted for Phase 1.6.** Owner: Founder CEO (agent). Approver for spend and keys: Pankaj.
Scope: speech-to-text (STT), intent understanding, contact matching, destinations, conversation UX, remote help.
Prices were checked on 2026-11-10. Conversions use **₹88 = US$1** (an assumption; recheck before budgeting).

---

## 0. TL;DR (the decision)

1. **The recorder cut-off is our own bug, not an accuracy problem.** `Listener` runs a single-shot `SpeechRecognizer` session. Google's endpointer ends the session at the first natural pause, and the 8 s `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` we set is documented as "may have no effect", and is widely reported as ignored. Also, `onError` calls `onDone(null)`, which **throws away the words already heard**, and `dictateAgain()` ignores partial results entirely. Fix it in-app first. It costs nothing and doesn't depend on any vendor.
2. **None of Pankaj's three concrete failures needs an LLM.** Cut-off is session handling. "Can't call anyone in my phone book" is a missing `READ_CONTACTS` index plus fuzzy/phonetic matching. "Ride to anywhere" is a missing destination slot plus geocoding. Build these on the phone, deterministically.
3. **Intent understanding = hybrid.** Rules first, run on the recogniser's top 5 hypotheses. Then an **opt-in cloud LLM fallback** that receives **text only** (no audio, no contact names, no phone numbers) and must return JSON from a fixed list of 7 intents. Names and places are resolved back **on the phone**. Expected cost is **₹15–26 per 1,000 fallback calls** (Sarvam 105B or Gemini 3.1 Flash-Lite, paid tier). Gemini Nano is a free bonus path where it exists, but **it exists on almost none of our users' ₹10–25k phones**.
4. **STT stays Android `SpeechRecognizer` for now** (free, already integrated, on-device where the language pack exists). We run a **bake-off on our own recorded elderly test set** against Sarvam Saaras v3 (cloud, ₹0.49/min, best published Indic numbers) and Gemini audio. We switch Marathi to cloud STT **only if** the test set shows the Google recogniser below the bar (command success under 85% for mr).
5. **Every action is confirmed out loud before it happens**, and every hand-off still needs the parent's tap (WhatsApp Send, the Call button in the dialer for non-favourites, the cab app's Book). No wrong person gets called on a mishear.
6. **Remote help: don't build screen control.** Phase it. (a) Now: coach WhatsApp video-call screen share, which is view-only and free. (b) Closed test: a "Let Pankaj fix my phone" tile that opens AnyDesk or TeamViewer QuickSupport, which the child installs from a dropdown like Near home. (c) Phase 2 (paid plan): **remote configuration of Nexus itself** (contacts, tiles, home, language) from the child's phone, which is Oscar Senior's retention lever and needs no AccessibilityService. In-house remote *control* is a Play-policy and scam-risk trap for a safety brand.

---

## 1. What's wrong today (code audit, main @ 86a37ad)

| # | Where | What happens | Effect on the phone |
|---|---|---|---|
| 1 | `Voice.kt` `Listener.start` | One `startListening()` per command. Session ends at the recogniser's endpoint (usually 0.7–2 s of silence). | "The recorder disconnects midway" when the parent pauses after "पंकजला सांग की…" to think. |
| 2 | `Voice.kt` `recognizeIntent` | `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS = 8000`. Android docs: "depending on the recognizer implementation, these values may have no effect." Reports: [SO 68865639](https://stackoverflow.com/questions/68865639/why-is-androids-speechrecognizer-automatically-stopping-itself), [SO 66319334](https://stackoverflow.com/questions/66319334/how-to-increase-the-amount-of-time-android-speech-recoognition-listens-for), [speech_to_text #294](https://github.com/csdcorp/speech_to_text/issues/294) (Android "does not provide any control" over the timeout). | The 8 s setting gives a false sense of safety. |
| 3 | `Voice.kt` `onError → onDone(null)`; `MainActivity.understood(null)` sets `heard = ""` | An error after partial results (ERROR_NO_MATCH, ERROR_SPEECH_TIMEOUT, ERROR_CLIENT, network blips) **discards the partial transcript**. | Half a message is heard on screen, then vanishes into "didn't understand". |
| 4 | `MainActivity.dictateAgain` | `onPartial = {}`. Only the final result counts. | Same loss on "Speak again". |
| 5 | No `EXTRA_MAX_RESULTS`, no `EXTRA_BIASING_STRINGS` | We parse only the top hypothesis and never tell the recogniser which names to expect. | "पंकच" vs "Pankaj" is fixed by our soundKey, but rarer names aren't. |
| 6 | No `READ_CONTACTS`; `Reading.askedName` falls back to a search | Only the 3–6 people saved in setup are callable by voice. | Pankaj's point 2. |
| 7 | `Plan.readTrip` knows HOME / OFFICE / street-pattern ADDRESS only | "Ruby Hall la ja", "स्टेशनला जायचं आहे", "take me to Phoenix Mall" are not trips. | Pankaj's point 3. |

---

## 2. Speech-to-text options (mr-IN, hi-IN, en-IN, code-mixed)

### 2.1 Comparison

| Option | mr | hi | Code-mix | Accuracy evidence | Offline | Size on phone | Cost | Privacy | Verdict |
|---|---|---|---|---|---|---|---|---|---|
| **Android `SpeechRecognizer` (Google), online** | Yes | Yes | Partial (Hinglish usually comes out in Devanagari or Latin, inconsistently) | Vistaar (2023): Google STT Hindi avg **23.9% WER** across 7 benchmarks, vs IndicWhisper 13.6% ([Vistaar](https://github.com/AI4Bharat/vistaar)). Current Google models are newer than that test. | No | 0 | Free | Audio goes to Google | **Keep as default.** Fix the session handling. |
| **`createOnDeviceSpeechRecognizer`** (API 31+) | **Often not installable** for mr-IN; check `RecognitionSupport` per phone ([expo-speech-recognition #105](https://github.com/jamsch/expo-speech-recognition/issues/105)) | Usually | Weak | Not published | Yes | Language pack (~tens of MB, managed by Google) | Free | Stays on phone | Use when `installedOnDeviceLanguages` has the tag (we already do). |
| **ML Kit GenAI Speech Recognition** (`genai-speech-recognition:1.0.0-alpha2`) | **No** (not in either mode's list) | Basic: hi-IN beta (API 31+); Advanced: hi-IN beta, **Pixel 10/11 and SM8750-class flagships only** | Unknown | None published | Yes | Shared Gemini Nano / packs | Free | On device | Not for us: no Marathi, flagship-only Advanced. Re-check yearly. ([ML Kit docs](https://developers.google.com/ml-kit/genai/speech-recognition/android)) |
| **Sarvam Saaras v3** (cloud; Saarika v2.5 is now deprecated) | Yes | Yes | **Native code-mix mode** (keeps "doctor" in Latin inside Hindi) | **19.31% WER** on the IndicVoices top-10 languages, vs ~22% for v2.5. Sarvam reports it beats GPT-4o Transcribe, Gemini 3 Pro, Deepgram Nova 3 and Scribe v2 ([Sarvam blog](https://www.sarvam.ai/blogs/asr)), but these are **company-reported numbers** ([Ascendants](https://ascendants.in/business-stories/saaras-v3-beats-global-speech-ai-benchmarks-india/)). Saarika v2.5 on Vistaar: Hindi 11.81% WER, the other 9 languages 20.15% ([docs](https://docs.sarvam.ai/api/getting-started/models/saarika)). | No | 0 | **₹0.49/min** pay-as-you-go, ₹100 free credits ([pricing](https://www.sarvam.ai/api-pricing)). A 5 s command costs ₹0.041, so **₹41 per 1,000** commands. | Audio goes to Sarvam (India). Check retention terms. | **Best cloud candidate.** Bake-off. |
| **Google Cloud STT v2, Chirp 3** | **Preview** | GA | Auto language detect | None for mr | No | 0 | $0.016/min (first 500k min), so ₹117 per 1,000 commands ([pricing](https://cloud.google.com/speech-to-text/pricing), [Chirp 3 langs](https://docs.cloud.google.com/speech-to-text/docs/models/chirp-3)) | Google Cloud | 3x Sarvam's price, and Marathi is only in preview. Second choice. |
| **Gemini audio in** (3.5 Flash-Lite at $0.30/M audio tokens; 3.5 Transcribe at ~$0.005/min) | Probably | Yes | Probably | No public Indic WER | No | 0 | About ₹35 per 1,000 for STT and intent in one call ([pricing](https://aistudio.google.com/docs/pricing)) | Paid tier: not used for training. **Free tier: "used to improve our products". Never use the free tier for parent audio.** | Bake-off candidate ("one call does both"). |
| **Bhashini (Dhruva)** | Yes (AI4Bharat conformer) | Yes | Weak | AI4Bharat models | No | 0 | Free for proofs of concept. Production needs a deal ([docs](https://bhashini.gitbook.io/bhashini-apis)) | Govt infra | Not for a consumer app on a deadline. |
| **AI4Bharat IndicConformer 600M** (MIT; AI4Bharat now recommends "Indic-Transcribe") | Yes | Yes | No | Vaani Hindi 13.2% WER ([HF](https://huggingface.co/ai4bharat/indic-conformer-600m-multilingual)). Community sherpa-onnx int8 port, mr ~11.5% on synthetic medical audio ([HF](https://huggingface.co/Docglasses-AI/indic-conformer-600m-sherpa-onnx-int8)) | Yes | **~660 MB per language** (int8) | Free | On device | Too big for ₹10k phones with 64–128 GB storage full of WhatsApp media. Not streaming. Phase 3 at best. |
| **Whisper on device** (whisper.cpp; IndicWhisper = Whisper-medium fine-tune) | Yes | Yes | Poor | IndicWhisper: mr avg 18.2%, hi 13.6% (medium, too big for phones). Whisper-small Marathi q5_1 (190 MB) runs faster than real time on an SD 720G **but has 51.2% WER on FLEURS mr**, and needs `no_timestamps` or it hallucinates ([HF](https://huggingface.co/bhaskaro/ainotes-whisper-marathi-q5_1)). Streaming is ~5x slower than real time ([whisper.cpp #3567](https://github.com/ggml-org/whisper.cpp/discussions/3567)). | Yes | 190 MB+ | Free | On device | **No.** Sizes that fit our phones are too inaccurate for Marathi. |
| **sherpa-onnx** (Dolphin multilingual CTC, IndicConformer ports) + Silero VAD | Some | Some | No | Thin | Yes | 100–700 MB | Free | On device | **Useful for its VAD/endpointing** (configurable trailing silence, default 2.4 s) ([Kotlin API](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/OnlineRecognizer.kt)), not as the recogniser. |
| **Vosk** | Small Hindi model only | Yes (old) | No | Old Kaldi models | Yes | ~40 MB | Free | On device | No Marathi. Skip. |

### 2.2 Android `SpeechRecognizer` details that matter

- **Silence extras** (`EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS`, `..._POSSIBLY_COMPLETE_...`, `..._MINIMUM_LENGTH_MILLIS`): documented as "may have no effect", and widely ignored by Google's service. **Don't rely on them.** Keep them set because they're harmless.
- **`EXTRA_SEGMENTED_SESSION`** (API 33): set it to `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` and the recogniser returns `onSegmentResults` per segment and `onEndOfSegmentedSession` at the end, so it keeps listening across pauses ([RecognizerIntent](https://developer.android.com/reference/android/speech/RecognizerIntent), [RecognitionListener](https://developer.android.com/reference/android/speech/RecognitionListener)). It's also "implementation dependent". **Probe it on each phone.** Use it when segments arrive, and fall back to the restart loop otherwise.
- **Restart loop** (the common pattern: [WebRTC.ventures 2025](https://webrtc.ventures/2025/03/real-time-speech-transcription-on-android-with-speechrecognizer/), [SO 71391119](https://stackoverflow.com/questions/71391119/voice-recognition-stops-listening-after-a-few-seconds-i-want-it-alive-until-pres)): on `onResults` *or* a recoverable `onError` (NO_MATCH, SPEECH_TIMEOUT), append the text and call `startListening` again until **our own** end condition. Caveats: a ~200–400 ms gap between sessions where words can be lost, and on some phones an earcon on every restart. Mitigation: restart in `onEndOfSpeech` rather than waiting for results, and show a steady "still listening" UI.
- **`EXTRA_BIASING_STRINGS`** (API 33): pass contact names and aliases. It's a bias, not a guarantee.
- **`EXTRA_MAX_RESULTS = 5`** plus `CONFIDENCE_SCORES`: parse every hypothesis and keep the best parse.
- **`EXTRA_AUDIO_SOURCE`** (API 33): feed a recorded WAV/PCM file to the recogniser. **This is how we replay a recorded test set through the real phone recogniser automatically** (section 9).
- `EXTRA_PREFER_OFFLINE`: a hint only. On-device is decided by `createOnDeviceSpeechRecognizer` plus `checkRecognitionSupport`, which we already use.

### 2.3 STT decision

- **Now:** Google `SpeechRecognizer`, with segmented sessions where supported, else a restart loop, plus our own end-of-turn rules (section 6), n-best, biasing, and **never discarding partials**.
- **Bake-off on the test set** (section 9): Google online, Google on-device (where present), Sarvam Saaras v3, Gemini 3.5 Flash-Lite audio. **Switch rule:** if Google's command success for a language is under 85% and Sarvam's is at least 7 points higher, route that language through a "Clearer voice (uses internet)" mode. The mode records with `AudioRecord` plus our own VAD and sends a 16 kHz clip to Sarvam. It's opt-in per family and shown on the Privacy screen.
- **Not doing:** on-device Whisper or IndicConformer on ₹10–25k phones (too big or too inaccurate), Vosk, or Bhashini.

---

## 3. Intent understanding

### 3.1 The fixed intent schema (whatever does the parsing must emit this)

```json
{"intent": "call|message|ride|near_need|safety_check|ask_family|unknown",
 "person": "as heard, e.g. पंकज / Aai / Sunil Patil",
 "relation": "son|daughter|spouse|mother|father|brother|sister|null",
 "channel": "whatsapp|sms|any|null",
 "message": "body in the speaker's language and person, or null",
 "place": "destination as heard, e.g. Ruby Hall Clinic / स्टेशन / home",
 "pickup": "as heard or null",
 "need": "grocery|medicine|doctor|ride|home_service|emergency|null",
 "confidence": 0.0}
```

The parser **never** outputs a phone number, a contact ID or coordinates. Those are resolved on the phone (sections 4 and 5).

### 3.2 Options

| Option | Coverage on our users' phones | Speed | Hindi/Marathi quality | Cost per 1,000 | Privacy | Verdict |
|---|---|---|---|---|---|---|
| **Rules (today's `Plan.kt`, `Dictation.kt`, `NearHome.readNeed`)** | 100% | <5 ms | Good on the phrasings we've seen, brittle on new ones | ₹0 | On phone | **Primary.** Extend with ride-to-X, phone-book names and n-best. |
| **Gemini Nano via ML Kit Prompt API** | **Flagships and upper-mid only.** The cheapest listed models are POCO X7 Pro, realme GT 7 and vivo T4 Ultra (≈₹25–35k). **No Redmi Note, Galaxy A/M, vivo Y/T or realme Narzo/C** ([device list, updated 2026-11-10](https://developers.google.com/ml-kit/genai)). Foreground only, per-app quota. | ~1 s | Untested in mr | ₹0 | On phone | **Optional bonus path** (behind the flag we already have). Under ~5% of our users. |
| **Gemma 3 1B (LiteRT-LM)** | 529 MB download, ~1 GB RAM. On an S24 Ultra: CPU prefill 379 tok/s, decode 55 tok/s ([HF](https://huggingface.co/litert-community/Gemma3-1B-IT)). A Helio G85/Dimensity 6100-class phone is roughly 3–5x slower (estimate), so **~3–6 s per command**. | Slow | **Weak in Marathi** (1B, and multilingual quality falls sharply at this size) | ₹0 | On phone | No: too slow, too weak, adds 0.5 GB. |
| **Gemma 3n E2B (LiteRT-LM)** | **~3 GB download, 8 GB RAM recommended.** S24 Ultra CPU decode 16 tok/s ([HF](https://huggingface.co/google/gemma-3n-E2B-it-litert-lm)) | Very slow on budget phones | Better, still untested on Marathi intents | ₹0 | On phone | No for ₹10–25k phones. |
| **Cloud LLM, text in, JSON out** | 100% with internet | 0.6–1.5 s round trip on 4G | Strong (Gemini) / Indic-tuned (Sarvam) | Gemini 3.1 Flash-Lite ≈ **₹26**; Gemini 3.5 Flash-Lite ≈ ₹36; **Sarvam 105B ≈ ₹15** (math below) | Transcript leaves the phone | **Fallback, opt-in.** |
| **Hybrid: rules → cloud LLM fallback → on-phone resolution** | 100% (offline = rules only) | Rules-hit <5 ms, fallback ~1 s | Best of both | ≈ ₹3–5 per 1,000 commands overall at a 20% fallback rate | Only the unknown 20%, text only, nothing identifying | **Chosen.** |

**Cost math** (per fallback call: ~700 input tokens for the system prompt, schema and few-shot examples plus the transcript; ~80 output tokens, thinking off):
- Gemini 3.1 Flash-Lite ($0.25 in / $1.50 out per 1M): 700×0.25e-6 + 80×1.5e-6 = **$0.000295**, so **₹26 per 1,000** calls.
- Gemini 3.5 Flash-Lite ($0.30 / $2.50): $0.00041, so **₹36 per 1,000**.
- Sarvam 105B (₹15 in / ₹60 out per 1M): ₹0.0105 + ₹0.0048 = **₹0.015**, so **₹15 per 1,000** ([pricing](https://www.sarvam.ai/api-pricing)).
- At 10 commands/day, a parent makes 300 a month. With 20% falling back, that's 60 calls, or **₹1–1.6 per parent per month**. Even with cloud STT on every command (300 × ₹0.041 = ₹12.3), the total is **≈₹14 per parent per month**, against the ₹999/yr (₹83/mo) plan hypothesis.
- Prompt caching cuts the input side further. We don't need it at this scale.

**Choosing between Gemini and Sarvam for the fallback:** run both on the test set. Pick Sarvam 105B if JSON validity is at least 99% and intent accuracy is within 2 points of Gemini (it's cheaper and India-hosted). Otherwise pick Gemini 3.1 Flash-Lite. Use paid tiers only: Gemini's free tier trains on inputs.

### 3.3 What "hybrid" means precisely

1. The recogniser returns up to 5 hypotheses.
2. The **rules parser runs on each one.** A hypothesis "wins" if it yields a complete intent whose slots resolve on the phone (a contact found, a place geocoded, a need known). The best score wins.
3. If no hypothesis resolves **and** the family turned on "Smart understanding" (default **off** in the closed test, explained on the Privacy screen), send the top hypothesis **text** to the LLM with the schema. Strip digits first, so phone numbers and OTPs never leave the phone.
4. Validate the JSON against the schema. Anything invalid is treated as `unknown`.
5. Resolve slots on the phone, then **confirm out loud** (section 6), then hand off.
6. Offline or opted out: steps 3–4 are skipped, and the parent gets "मला समजलं नाही. पुन्हा सांगा, किंवा चेहऱ्यावर टॅप करा" plus the big face buttons.
7. Every `unknown` is counted (we already count "not understood"). Weekly, with consent, the child can export *anonymised phrasing patterns* so we can grow the rules. The LLM is a net, not the engine.

**Blunt trade-off:** the cloud fallback breaks the "nothing leaves your phone" story for that 20%. That's why it's opt-in, text-only and digit-stripped, and why the rules have to carry most of the traffic. If the closed test shows rules alone reach at least 90% command success, **we ship without the LLM at all.**

---

## 4. Contact name matching (phone book)

**Permission:** `READ_CONTACTS` is a normal runtime (dangerous) permission. It isn't on Play's restricted list the way SMS and call log are. It needs a Data safety entry ("Contacts: processed on device, not collected"). **Don't request `READ_CALL_LOG`**: Play restricts it to default dialer apps.

**Index (built on the phone, refreshed on a `ContactsContract` change):**
- For each display name and nickname, store: the original, a **romanised form** (our `romanize()` for Devanagari, ICU `Transliterator.getInstance("Devanagari-Latin")` on API 29+ as a cross-check with diacritics stripped), and a **sound key** (our `soundKey()`, extended with Indic folds: v/w, sh/s/ष, retroflex = dental, aspirated = plain, aa/a, ee/i, oo/u, z/j, ph/f, final schwa dropped, "-la/-ला/-ko/-को/-na" case endings stripped).
- Relation words get their own map. The parent's saved relations come first (we already have SON/DAUGHTER/SPOUSE). Then come contacts *named* with a relation, which is very common in India: "Aai", "Mummy", "Papa", "Baba", "Bhau", "Dada", "Tai", "Didi", "Bhaiya", "Beta", "Jiju", "Mama", "Kaka", "Mavshi". Add mother, father, brother, sister, uncle and aunt relations to the setup screen.
- **Transliteration model?** AI4Bharat IndicXlit (11M params) gets only ~60% top-1 accuracy Roman→Devanagari on Dakshina ([Aksharantar](https://aclanthology.org/2023.findings-emnlp.4.pdf)), and names have many valid spellings anyway. **Matching on sound keys beats picking one spelling**, so no model is needed.

**Scoring:** Jaro-Winkler on the sound keys (≥0.90 is strong, 0.82–0.90 weak), a bonus for an exact first-token match, and multi-token support ("Sunil Patil" vs "Sunil"). Tie-breakers: a person saved in Nexus setup beats a starred contact, which beats the rest. Run it across all n-best hypotheses, and pass the top ~200 names as `EXTRA_BIASING_STRINGS` on API 33+.

**Ambiguity rules:**
- One strong match: confirm with name **and photo** ("सुनील पाटील यांना फोन लावू?"). If it's a setup-saved person *and* an exact alias, call straight away (today's behaviour).
- Two or more within 0.04 of each other: "कोणाला? पहिला: सुनील पाटील. दुसरा: सुनील काळे." Show big cards with photos and the last 4 digits. The parent answers by voice ("पहिला", "दुसरा", "पाटील") or by tapping.
- No match: "तुमच्या फोनमध्ये 'सुरेश' सापडले नाही" plus "Ask Pankaj" and "Open contacts".
- **Never auto-dial an unsaved, non-exact match.**

---

## 5. Destinations ("take me to X")

**Parsing (rules):** for Marathi `X-ला/X ला जायचं (आहे)`, `X ला ने`, `X पर्यंत रिक्षा`. For Hindi `X jana hai`, `X le chalo`, `X tak`. For English `take me to X`, `go to X`, `cab/auto to X`, `from Y to X`. Plus pickup cues ("इथून", "from here", "घरून"). Reuse the HOME and OFFICE slots.

**Geocoding options (India):**

| Option | Cost | Quality for spoken POIs ("Ruby Hall", "Swargate stand") | Notes |
|---|---|---|---|
| Android `Geocoder.getFromLocationName` | Free, no key | Fair for addresses, **poor and inconsistent for POIs and colloquial names**; can fail with no network or no backend | Use as the offline-ish fallback only. We already use it for the home PIN. |
| **Google Places Autocomplete (New)**, biased to the home location, then **Place Details Essentials** | India: Autocomplete **70,000 free/month**, then $0.85/1,000. A session that ends in Details counts the Autocomplete as free. Details Essentials: 70,000 free, then $1.50/1,000. **Text Search Essentials (IDs only): unlimited free** ([India pricing](https://developers.google.com/maps/billing-and-pricing/pricing-india)) | Best POI coverage in India | **Chosen.** At closed-test and early scale this costs ₹0. It needs a Google Cloud project with billing turned on (card on file). |
| Ola Maps (Krutrim) | **100,000 free/month per API**, then Autocomplete ₹0.056 and Geocode ₹0.099 per call ([pricing](https://maps.olakrutrim.com/pricing)) | Good India data | **Backup.** No card for the free tier. India-hosted. |
| Mappls (MapmyIndia) | Freemium, **no public price list** (sales-quoted) | Strong addresses | Skip until we need it. |

**Flow:** take the top 3 candidates near home (bias radius 25 km). Read back the **name and area** of the first ("रुबी हॉल क्लिनिक, ससून रोड. इथे जायचं?"), with "दुसरं" for the next. On yes, send the coordinates to the Uber universal link (dropoff lat/lng, which we already support) or, for Ola, Rapido and Namma Yatri, copy the address. Save "recent places" on the phone so the second trip to the same place needs no network. **The request sends the place text and the home coordinates.** Disclose that on the Privacy screen.

---

## 6. Conversation UX for 60+ users

Evidence:
- Older speakers pause more mid-sentence. At a 700 ms threshold, 19.7% of clips from speakers in their sixties and 16.6% in their seventies contain a pause long enough to look like "done", vs 8.0% for speakers in their twenties. Recognition accuracy itself did **not** get worse with age ([asr-age-gap, Common Voice, 2,760 clips](https://github.com/Kayvan-Zahiri/asr-age-gap); a single-author study, so treat it as indicative). This matches Pankaj's symptom exactly.
- Older adults' preferred system response delay is around 0.6–1.5 s, and the type of feedback matters ([Kim et al., IJHCI 2024](https://doi.org/10.1080/10447318.2024.2348225)).
- Products are moving from fixed silence timers to "does this sound finished" models, which halve premature cut-offs in the study above.

Rules for Nexus:
1. **Tap to start, auto-stop. Not push-to-hold.** Holding a button for 10 s is hard with tremor or arthritis, and lifting early cuts the message. A **giant "बोलून झालं / Done" button** is always visible, so the parent can end early.
2. **End-of-turn, set by us rather than the recogniser:**
   - Commands: stop after **2.0 s** of silence *once a complete command parses*, or after **3.5 s** of silence otherwise.
   - Dictated message body (after "सांग की", "bolo ki", "tell him that"): stop after **4.0 s** of silence, and **never** stop while the last words are a joiner ("आणि", "और", "and", "की", "ki", "म्हणजे").
   - Hard cap of 60 s. Before stopping on silence, say a soft "अजून काही?" and listen 3 s more (dictation only).
   - Personalise: keep each parent's median pause length and set the dictation timeout to max(4 s, 2.5 × median).
3. **Live transcript**, large, in the parent's script, growing as they speak. That's proof it's listening.
4. **Earcons plus haptics:** a rising tone and short vibration when the mic opens, a falling tone when it stops. **The TTS prompt must finish before the mic opens** (we already stop the speaker on "Speak again"; do it everywhere), or the recogniser hears itself.
5. **Confirm before acting, every time something leaves the phone:**
   - Read back with intent, person and payload: "पंकजला WhatsApp: 'तू घरी कधी येणार आहेस? आणि येताना एक साबण घेऊन ये.' पाठवू?" Show big Yes/No buttons and accept voice yes/no ("हो/हां/yes/ठीक", "नाही/नहीं/no").
   - Implicit confirmation (just say it and do it) only for opening a Near-home tile or calling a setup-saved person by exact alias.
6. **Recovering from a mishear:** "नाही" leads to "काय बदलू? माणूस / मेसेज / ठिकाण". Tap or say which part, then re-speak only that slot. After 2 failures, offer faces and tiles plus "Ask Pankaj". Never loop more than twice.
7. **Speak slowly:** TTS rate 0.85–0.9 for Marathi and Hindi. Keep sentences short and put the name first.

---

## 7. Remote help (Pankaj's idea 4)

**Policy reality:**
- Remote *control* on Android needs an **AccessibilityService** (to inject taps), plus **MediaProjection** for the screen. Nexus is not eligible for `isAccessibilityTool` ("assistants … launchers" are named as *not* accessibility tools). So we'd need the Play declaration, a separate prominent disclosure with affirmative consent, and a review video. **Autonomous actions are prohibited**; only user-authorised remote control within the disclosed purpose is allowed ([Play: AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491), see also the [Oct 30 2025 policy announcement](https://support.google.com/googleplay/android-developer/answer/16550159?hl=en)).
- The RBI warned in 2019 that fraudsters get victims to install AnyDesk and read out its code, then drive their UPI and banking apps ([Business Standard](https://www.business-standard.com/article/finance/digital-banking-upi-users-beware-rbi-warns-against-anydesk-all-you-need-to-know-119021800425_1.html)), and banks still carry the warning ([J&K Grameen Bank](https://www.jkgrameen.bank.in/digitalbanking.aspx/pdfs/pdfs/digitalbanking.aspx?id=17&type=Fraud+Awareness)). A safety-first app for 60+ parents that ships its own remote-control backdoor turns into a scam target, and one bad story ends the brand. Banking and UPI apps also block capture (`FLAG_SECURE`), so the moment a parent most needs help ("my UPI isn't working") is exactly the screen the child can't see.

**Existing tools:**
- **TeamViewer QuickSupport:** free for personal use. Remote control needs the Universal Add-On (Accessibility). Its official supported-manufacturer list covers Samsung (Knox only), Motorola, OnePlus, Nokia and others, but **not Xiaomi, vivo, OPPO or realme**, which are most Indian budget phones; those depend on the Universal Add-On ([TeamViewer](https://www.teamviewer.com/en/global/support/knowledge-base/teamviewer-classic/mobile/android/supported-manufacturers-for-remotely-controlling-android-devices/)).
- **AnyDesk:** free for personal use, control via the AD1 plugin (Accessibility). On Android 10+ someone must accept the screen-capture prompt on the phone itself.
- **WhatsApp video-call screen share:** free and already installed, **view-only**, and everyone already knows the app ([Android Authority](https://www.androidauthority.com/how-to-screen-share-on-whatsapp-3333172/)). Google Meet also shares the screen from Android.
- **Oscar Senior:** the family app *remotely manages the senior's launcher* (apps, layout, reminders, contacts) ([Oscar](https://www.oscarsenior.com/pricing)). **That's the retention lever Pankaj describes**, and it's configuration, not screen control. **BaldPhone** has no remote features.

**Build vs link:**

| Approach | Effort | Running cost | Risk |
|---|---|---|---|
| In-house view + control (MediaProjection + WebRTC + TURN + AccessibilityService) | 6–10 engineer-weeks, plus Play review cycles | TURN relay bandwidth, signalling server | High: policy review, scam vector, OEM background kills (Xiaomi/vivo) |
| In-house view-only + "pointer" annotations | 4–6 weeks | Same servers | Medium. Still a remote-view backdoor; FLAG_SECURE gaps |
| Deep link to WhatsApp screen share / AnyDesk / QuickSupport | 2–3 days | ₹0 | Low (their policy, their security) |
| **Remote configuration of Nexus** (child edits contacts, tiles, home, language; E2E-encrypted config sync, already in Phase 2) | 2–3 weeks (part of Phase 2) | Tiny (a config blob) | Low. No Accessibility needed |

**Phased recommendation:**
- **Phase 1.6 (now, 2–3 days):** an "Ask family → Show my screen" card. It reads out and shows 3 steps for WhatsApp video-call screen share in the parent's language and opens WhatsApp to the child's chat. It's view-only, the child guides by voice, and there's nothing new to trust.
- **Closed test:** an optional "Let Pankaj fix my phone" tile. The child picks AnyDesk or TeamViewer QuickSupport in setup (same dropdown, Installed tag and Play link as Near home). The tile opens it. A **scam guard** goes with it: Nexus says "फक्त पंकजलाच हा नंबर सांगा. बँक किंवा कोणी अनोळखी विचारत असेल तर नाही." and the Safety checks flag any SMS or call mentioning AnyDesk or TeamViewer.
- **Phase 2:** remote configuration of Nexus from the child app. That's the Oscar-style moat.
- **Revisit in-house view-only** only if closed-test families ask for it more than 3 times and we have a paid plan. **Never build remote control in-house.**

---

## 8. Recommended architecture

```
 PARENT PHONE (all of this is on device)
 ┌───────────────────────────────────────────────────────────────────────┐
 │ Mic UI: tap → earcon → live transcript → Done / auto-stop (our rules) │
 │   │                                                                   │
 │ VoiceSession                                                          │
 │   ├─ Google SpeechRecognizer: on-device if pack installed, else online│
 │   │    segmented session (API33+, probed) │ else restart loop         │
 │   │    n-best=5, biasing=contact names (API33+), partials never lost  │
 │   └─ [opt-in "Clearer voice"] AudioRecord + VAD → clip → Sarvam STT ☁ │
 │   │                                                                   │
 │ Understand                                                            │
 │   ├─ Rules on each hypothesis (call / message / ride / need / safety  │
 │   │   / ask family / go home)                                         │
 │   ├─ [Gemini Nano if AVAILABLE] same JSON schema, on device           │
 │   └─ [opt-in "Smart understanding"] text, digits stripped → LLM ☁ JSON│
 │   │                                                                   │
 │ Resolve (on device): ContactIndex (sound keys, relations, JW score)   │
 │                      Places → Autocomplete+Details ☁ (place text only)│
 │                      Needs → Near home tiles                          │
 │   │                                                                   │
 │ Confirm: TTS read-back + big Yes/No + voice yes/no → hand-off         │
 │   (dialer / WhatsApp prefill / cab deep link / Maps); a person taps   │
 └───────────────────────────────────────────────────────────────────────┘
 Offline: rules + on-device recogniser (if pack) + recent places + Geocoder.
 Cloud pieces are optional; each is shown on the Privacy screen.
 No Nexus server is needed: the phone calls vendor APIs with restricted
 keys. A tiny proxy (Cloudflare Worker) is added before public launch so
 keys aren't shipped in the APK.
```

---

## 9. Implementation plan (each step is a PR with fast JVM tests)

1. **V1 Session fix (day 1–2):** `VoiceSession` replaces single-shot `Listener`. Segmented session where it works (probed once per phone and cached), else a restart loop. Partials are accumulated and never discarded. Our own end-of-turn rules. Done button. Earcons and haptics. TTS-before-mic ordering. n-best = 5. Tests: a fake recogniser emitting partial/segment/error sequences (cut mid-message, NO_MATCH after partials, pause after "सांग की"). **This alone fixes Pankaj's point 1.**
2. **V2 Phone book (day 2–4):** the `READ_CONTACTS` permission screen (child-assisted, with a plain why), `ContactIndex`, sound-key folds, Jaro-Winkler, relation map, ambiguity chooser, biasing strings. Tests: 300+ name pairs (Devanagari vs Latin, nicknames, relation words, near-duplicates such as Sunil Patil and Sunil Kale).
3. **V3 Ride to anywhere (day 4–6):** destination grammar in mr, hi and en; Places Autocomplete + Details behind a `PlaceSearch` interface (with a Geocoder fallback and a fake for tests); read-back and choose-next; recent places. Tests: 150 destination phrasings.
4. **V4 Voice test set tooling (day 3–7, in parallel):** a debug-only "Record test phrases" screen (consent first, 16 kHz WAV plus label, shared by the child via the share sheet). A replay harness: an instrumented test that feeds each WAV to `SpeechRecognizer` via `EXTRA_AUDIO_SOURCE` (API 33+) on a real phone and writes the transcripts out. A JVM scorer that runs transcripts through Understand + Resolve against the labels.
5. **V5 Bake-off (week 2):** the same WAVs through Sarvam Saaras v3, Gemini 3.5 Flash-Lite audio and Chirp 3 (scripts on the box, not the app). The same transcripts through Sarvam 105B and Gemini 3.1 Flash-Lite for the fallback. Decide using the switch rules in 2.3 and 3.2.
6. **V6 Fallbacks (week 2–3, only if V5 says so):** "Smart understanding" (LLM JSON) and/or "Clearer voice" (Sarvam STT), both opt-in, plus a Privacy screen and PRIVACY.md update and a Data safety update. Keys go behind a Cloudflare Worker proxy before any public release.
7. **V7 Remote help, step 1 (2 days):** the WhatsApp screen-share coaching card, the AnyDesk/QuickSupport tile and the scam guard.

---

## 10. What this needs from Pankaj (exact asks and costs)

| Ask | Needed for | Cost | Needs his yes? |
|---|---|---|---|
| Nothing | V1, V2, V4, V7 | ₹0 | No |
| **Google Cloud project with billing on**, plus a Places API (New) key restricted to the app's package and SHA-1 | V3 ride to anywhere | **₹0 expected** (70k free Autocomplete and 70k free Details per month), but **a card must be on file**. Set a ₹500/month budget alert. | **Yes** (card on file) |
| *Or* an **Ola Maps** developer key | V3 alternative | ₹0 within 100k calls per API per month | Yes (account signup) |
| **Sarvam AI account** (₹100 free credits) | V5 bake-off STT and LLM | ₹0 at first. A later top-up of **₹1,000** covers ~2,000 min of STT. | Yes (account; any top-up) |
| **Gemini API key on the paid tier** (Google AI Studio with billing) | V5 bake-off, V6 if chosen | **<₹100** for the whole bake-off. Closed test: 6 parents × 60 fallback calls × ₹0.026 ≈ **₹10/month** | Yes |
| **One budget test phone** (e.g. a ₹10–12k Redmi, realme or vivo, Android 14+, 4–6 GB RAM) | Real target-hardware testing of the recogniser, timings and memory | **≈₹10,000–12,000** one-off | **Yes** (purchase) |
| **8–12 elderly volunteers** (his parents, relatives, closed-test parents) to record ~60 phrases each, about 15 minutes | V4/V5 test set | ₹0 (a sweets box is a nice thank-you) | He makes the asks. I draft the message. |

---

## 11. Test plan

**Test set ("Nexus Voice-60"):**
- **Speakers:** at least 8 people aged 60+, mixed gender, at least 3 Marathi-first, 3 Hindi-first and 2 English/Hinglish. Record on *their own* phones at home (TV noise, fans: the real conditions). Get a signed or recorded consent in their language, and offer deletion on request.
- **Script:** 60 prompts per speaker, ~500–700 clips in total.
  - 20 **read** prompts covering every intent and slot: calls by saved name, contact name and relation; messages with long bodies and a deliberate pause after "सांग की"; rides to 5 named local places; the 6 needs; safety; ask family.
  - 30 **elicited** prompts: we show a picture or situation card ("You want your son to bring soap") and they say it *their way*. This is the long tail the rules must cover.
  - 10 **long dictations** (8–20 s) with natural pauses. These catch cut-offs.
- **Labels:** the expected JSON (intent plus slots resolved to *a fixture phone book* of 150 Indian names and *fixture places* near a fixture home).
- **Split:** 70% dev and 30% held-out. The rules are never tuned on held-out data.

**Metrics** (computed per language, per speaker and overall):
1. **Command success rate, first try**: the right action with the right person, place or need reaches the confirm screen. **Target ≥90%.**
2. **Success within 2 tries** (simulating "नाही, पुन्हा"). Target ≥97%.
3. **Wrong-action rate** (confirm shows the wrong person or place). Target ≤2%. **Wrong-action-executed = 0**, because the confirm step blocks it.
4. **Cut-off rate**: the transcript is missing the last ≥2 words of the reference. **Target <2%**, and this is Pankaj's bug.
5. STT **CER and LLM-judged WER** per engine (Sarvam's point: raw WER over-penalises "doctor" vs "डॉक्टर" in code-mix ([Sarvam eval blog](https://www.sarvam.ai/blogs/evaluating-indian-language-asr))). Report raw WER too.
6. Latency, from end of speech to the confirm screen: p50 ≤1.5 s and p90 ≤3 s.
7. Fallback rate (the share of commands needing the LLM) and its JSON validity (≥99%).

**Where each runs (fast by default):**
- Parser, contact index and destination grammar: **JVM unit tests** on transcripts. They run in seconds in the existing Robolectric suite, with the test set transcripts checked in as fixtures (audio stays out of git and lives on the box).
- Session logic: JVM tests with a fake recogniser.
- Real recogniser: the `EXTRA_AUDIO_SOURCE` replay harness on Pankaj's phone and the budget test phone, run before each release.
- Cloud bake-off: scripts on the box.
- Field: closed-test usage counters (already built) for "voice command" and "not understood" per day, plus the day-3 and day-7 feedback.

---

## 12. Blunt trade-offs and risks

- **Google's online recogniser already sends audio to Google** whenever the language pack isn't on the phone (likely for Marathi). Our Privacy screen already says so. Adding Sarvam doesn't make privacy worse in kind, but it adds a second processor, and we must disclose it.
- **Rules don't scale forever.** They will plateau in the high 80s to low 90s on elicited speech. The opt-in LLM fallback is the release valve. If testers refuse to opt in, we live at the rules' ceiling plus taps.
- **Segmented sessions and biasing are "implementation dependent".** On some OEM phones only the restart loop works, and it can drop a word during the ~300 ms restart. If the cut-off rate stays above 2% on the budget phone, that's the trigger to move to our own `AudioRecord` + VAD and a cloud STT for dictation.
- **Gemini Nano is not our path** for 2026 India budget phones. Don't architect around it.
- **Places costs ₹0 until it doesn't.** At 70k+ requests/month (≈2,000+ daily ride-seeking parents) we pay ~$0.85–1.50 per 1,000. Fine for a paid plan, and Ola Maps is the cheaper fallback.
- **Remote control is where good elder-care apps go to die in India**, through scams and Play review. Remote *config* gets the retention without the risk.
