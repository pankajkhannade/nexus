export interface Capture {
  id: string;
  at: string;
  text: string;
  address: string | null;
  phone: string | null;
  email: string | null;
}

export interface Action {
  id: string;
  label: string;
  detail: string;
  href: string;
  copy?: string;
}

export interface Person {
  id: string;
  name: string;
  phone: string;
  email: string;
}

export interface SavedPlace {
  id: string;
  name: string;
  address: string;
}

export interface When {
  label: string;
  start: Date;
  end: Date;
}

export interface Reading {
  places: string[];
  mapLink: string | null;
  phones: string[];
  emails: string[];
  when: When | null;
}

const SUFFIX =
  "Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Drive|Dr|Lane|Ln|Way|Court|Ct|Place|Pl|Terrace|Ter|Parkway|Pkwy|Highway|Hwy|Circle|Cir|Trail|Trl|Square|Sq|Nagar|Marg|Colony|Cross|Block|Sector";

const STREET = new RegExp(
  `\\b\\d{1,6}\\s+(?:[A-Za-z0-9.'’-]+\\s+){0,6}(?:${SUFFIX})\\b\\.?` +
    `(?:\\s*,?\\s*(?:Apt|Apartment|Unit|Suite|Ste|Flat|#)\\s*[A-Za-z0-9-]+)?`,
  "i",
);

const CUE =
  /\b(?:meet(?:\s+me)?|see you|i(?:'|’)m|i am|i(?:'|’)ll be|be|wait(?:ing)?|sitting|pick(?:\s+me)?\s+up|drop(?:\s+me)?(?:\s+off)?|come(?:\s+to)?|heading|dinner|lunch|breakfast|coffee|drinks|let(?:'|’)s go|lets go|address is|located at|location is|live at|staying at)\s+(?:at|to|near|by|in|is)?\s*([^.\n;]{2,80})/gi;

const INTERSECTION =
  /\b([A-Z][A-Za-z0-9.'’-]+(?:\s+[A-Z][A-Za-z0-9.'’-]+){0,2})\s+and\s+([A-Z0-9][A-Za-z0-9.'’-]+(?:\s+[A-Z][A-Za-z0-9.'’-]+){0,2})\b/;

const MAP_LINK =
  /https?:\/\/(?:maps\.app\.goo\.gl|goo\.gl\/maps|maps\.google\.[a-z.]+|(?:www\.)?google\.[a-z.]+\/maps)\/\S+/i;

const EMAIL = /[A-Z0-9._%+-]+\s*@\s*[A-Z0-9.-]+\.[A-Z]{2,}/gi;

const WRITTEN_EMAIL =
  /\b([a-z0-9._%+-]{1,64})\s*(?:\[|\()?\s*at\s*(?:\]|\))?\s+([a-z0-9-]{1,64})\s*(?:\[|\()?\s*dot\s*(?:\]|\))?\s+([a-z]{2,12})\b/gi;

const PAREN_AT = /\b([a-z0-9._%+-]{1,64})\s*[([]\s*at\s*[)\]]\s*([a-z0-9.-]+\.[a-z]{2,})\b/gi;

const PHONE = /[+\d(][\d\s().+-]{8,22}\d/g;

const WHEN =
  /\b(today|tomorrow|tonight|monday|tuesday|wednesday|thursday|friday|saturday|sunday)?\s*(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b|\b(today|tomorrow|tonight|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\s+at\s+(\d{1,2})(?::(\d{2}))?\b/i;

const STOP = new Set([
  "meet", "this", "that", "with", "from", "your", "have", "will", "just", "about", "please",
  "call", "text", "email", "phone", "address", "today", "tomorrow", "tonight", "the", "and",
  "you", "for", "are", "was", "but", "not", "can", "let", "lets", "dinner", "lunch", "coffee",
  "come", "pick", "drop", "near", "her", "him", "them", "she", "his", "our", "was", "get",
  "got", "out", "outside", "late", "here", "there", "home", "office", "number", "message", "whatsapp",
]);

const NOT_CORNER = /^(?:you|i|me|we|they|he|she|it|this|that|him|her|them|us|and)$/i;

function clean(value: string) {
  return value.replace(/\s+/g, " ").trim();
}

function cleanPlace(raw: string): string | null {
  let place = clean(raw).replace(/^[“"']|[”"']$/g, "");
  place = place.replace(
    /[,:]?\s*\b(?:today|tomorrow|tonight|on\s+(?:mon|tues|wednes|thurs|fri|satur|sun)day|at\s+\d{1,2}(?::\d{2})?\s*(?:am|pm)?|if\b.*|please|thanks|thank you)\b.*$/i,
    "",
  );
  place = place.replace(/[.!,]+$/, "").trim();
  if (place.length < 2 || place.length > 80) return null;
  if (/^(?:me|you|him|her|them|there|here|home|back|soon|later|ready|free|around|done|busy|outside|inside)$/i.test(place)) return null;
  if (/^(?:the\s+)?(?:meeting|office|work|home|place|house|party|event)$/i.test(place)) return null;
  return place;
}

export function extractPlaces(text: string): string[] {
  const found: string[] = [];
  const street = text.match(STREET);
  if (street) {
    const place = cleanPlace(street[0].replace(/\.$/, ""));
    if (place) found.push(place);
  }
  const corner = text.match(INTERSECTION);
  if (corner && !NOT_CORNER.test(corner[1]) && !NOT_CORNER.test(corner[2])) {
    const place = cleanPlace(`${corner[1]} and ${corner[2]}`);
    if (place && !found.includes(place)) found.push(place);
  }
  if (found.length === 0) {
    for (const cue of text.matchAll(CUE)) {
      const place = cleanPlace(cue[1] ?? "");
      if (!place || found.includes(place)) continue;
      found.push(place);
      break;
    }
  }
  if (found.length === 0 && text.trim().length <= 80) {
    const loose = text.match(/\b(\d{1,6}\s+(?:[A-Z][\w.'’-]+\s+){0,4}[A-Z][\w.'’-]+)\b/);
    const place = loose ? cleanPlace(loose[1]) : null;
    if (place) found.push(place);
  }
  return found.slice(0, 2);
}

export function extractMapLink(text: string): string | null {
  const match = text.match(MAP_LINK);
  return match ? match[0].replace(/[),.;]+$/, "") : null;
}

export function extractAddress(text: string): string | null {
  return extractPlaces(text)[0] ?? null;
}

function acceptPhone(raw: string): string | null {
  const compact = raw.replace(/[^\d+]/g, "");
  const international = compact.startsWith("+") || /^\s*00/.test(raw);
  let digits = compact.replace(/\D/g, "");
  if (/^\s*00/.test(raw)) digits = digits.slice(2);
  if (digits.length < 10 || digits.length > 15) return null;
  if (!international && digits.length > 11) return null;
  if (!international && digits.length === 11 && digits[0] !== "1" && digits[0] !== "0") return null;
  if (/^(?:19|20)\d{2}$/.test(digits)) return null;
  return clean(raw).replace(/[.\s-]+$/, "");
}

export function extractPhones(text: string): string[] {
  const found: string[] = [];
  for (const match of text.matchAll(PHONE)) {
    const raw = match[0];
    const index = match.index ?? 0;
    const before = text.slice(Math.max(0, index - 14), index).toLowerCase();
    if (/\b(order|id|pin|otp|code|ref|invoice|zip|postal|tracking)\s*$/.test(before)) continue;
    const phone = acceptPhone(raw);
    if (!phone || found.includes(phone)) continue;
    found.push(phone);
    if (found.length === 3) break;
  }
  return found;
}

export function extractPhone(text: string): string | null {
  return extractPhones(text)[0] ?? null;
}

export function extractEmails(text: string): string[] {
  const found: string[] = [];
  for (const match of text.matchAll(EMAIL)) {
    const email = match[0].replace(/\s+/g, "").toLowerCase();
    if (!found.includes(email)) found.push(email);
  }
  for (const match of text.matchAll(WRITTEN_EMAIL)) {
    const email = `${match[1]}@${match[2]}.${match[3]}`.toLowerCase();
    if (!found.includes(email)) found.push(email);
  }
  for (const match of text.matchAll(PAREN_AT)) {
    const email = `${match[1]}@${match[2]}`.toLowerCase();
    if (!found.includes(email)) found.push(email);
  }
  return found.slice(0, 2);
}

export function extractEmail(text: string): string | null {
  return extractEmails(text)[0] ?? null;
}

function nextWeekday(day: number, from: Date) {
  const date = new Date(from);
  const delta = (day - date.getDay() + 7) % 7 || 7;
  date.setDate(date.getDate() + delta);
  return date;
}

export function extractWhen(text: string, now = new Date()): When | null {
  const match = text.match(WHEN);
  if (!match) return null;
  const dayWord = (match[1] || match[5] || "").toLowerCase();
  const hourRaw = Number(match[2] || match[6]);
  const minute = Number(match[3] || match[7] || "0");
  const ampm = (match[4] || "").toLowerCase();
  if (!hourRaw || hourRaw > 12 || minute > 59) return null;
  let hour = hourRaw % 12;
  if (ampm === "pm" || (!ampm && hourRaw >= 1 && hourRaw <= 7)) hour += 12;
  if (!ampm && hourRaw >= 8 && hourRaw <= 11) hour = hourRaw;

  const start = new Date(now);
  start.setSeconds(0, 0);
  const days = ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"];
  if (dayWord === "tomorrow") start.setDate(start.getDate() + 1);
  else if (days.includes(dayWord)) {
    const jumped = nextWeekday(days.indexOf(dayWord), now);
    start.setFullYear(jumped.getFullYear(), jumped.getMonth(), jumped.getDate());
  } else if (dayWord === "today" || dayWord === "tonight" || !dayWord) {
    /* keep today */
  }
  start.setHours(hour, minute, 0, 0);
  if (!dayWord && start.getTime() < now.getTime()) start.setDate(start.getDate() + 1);

  const end = new Date(start.getTime() + 60 * 60 * 1000);
  const label = new Intl.DateTimeFormat(undefined, {
    weekday: "short",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(start);
  return { label, start, end };
}

export function readText(text: string, now = new Date()): Reading {
  return {
    places: extractPlaces(text),
    mapLink: extractMapLink(text),
    phones: extractPhones(text),
    emails: extractEmails(text),
    when: extractWhen(text, now),
  };
}

export function phoneFromMemory(text: string, captures: Array<{ text: string }>) {
  const words = text.toLowerCase().match(/[a-z][a-z'-]{3,}/g) ?? [];
  const names = words.filter((word) => !STOP.has(word));
  for (const name of names) {
    for (const capture of captures) {
      const hay = capture.text.toLowerCase();
      if (!new RegExp(`\\b${name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\b`).test(hay)) continue;
      const phone = extractPhone(capture.text);
      if (phone) return { name, phone };
    }
  }
  return null;
}

export function summarize(text: string): string {
  const cleanText = clean(text);
  if (cleanText.length <= 180) return cleanText;
  const cut = cleanText.slice(0, 180);
  const stop = Math.max(cut.lastIndexOf(". "), cut.lastIndexOf("? "));
  return `${(stop > 60 ? cut.slice(0, stop + 1) : cut).trim()}…`;
}

export function uberUrl(address: string) {
  return `https://m.uber.com/ul/?action=setPickup&pickup=my_location&dropoff[formatted_address]=${encodeURIComponent(address)}`;
}

export function mapsUrl(address: string) {
  return `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(address)}`;
}

export function smsUrl(phone: string | null, body: string) {
  const note = encodeURIComponent(body.trim().slice(0, 300) || "On my way.");
  if (!phone) return `sms:?body=${note}`;
  return `sms:${phone.replace(/[^\d+]/g, "")}?body=${note}`;
}

export function telUrl(phone: string) {
  return `tel:${phone.replace(/[^\d+]/g, "")}`;
}

export function whatsAppUrl(phone: string, body: string) {
  const digits = phone.replace(/\D/g, "").replace(/^00/, "");
  const note = encodeURIComponent(body.trim().slice(0, 300));
  return `https://wa.me/${digits}${note ? `?text=${note}` : ""}`;
}

export function mailUrl(email: string, body: string) {
  const note = encodeURIComponent(body.trim().slice(0, 500));
  return `mailto:${email}?subject=${encodeURIComponent("From Nexus")}&body=${note}`;
}

function calendarStamp(date: Date) {
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}T${pad(date.getHours())}${pad(date.getMinutes())}00`;
}

export function calendarUrl(when: When, title: string, details: string) {
  const params = new URLSearchParams({
    action: "TEMPLATE",
    text: title.slice(0, 80) || "Plan",
    dates: `${calendarStamp(when.start)}/${calendarStamp(when.end)}`,
    details: details.slice(0, 500),
  });
  return `https://calendar.google.com/calendar/render?${params.toString()}`;
}

export function nickInText(text: string, places: Array<{ name: string }>) {
  const names = [...places.map((place) => place.name.trim().toLowerCase()), "home", "office"];
  const unique = [...new Set(names.filter((name) => name.length >= 2))];
  unique.sort((a, b) => b.length - a.length);
  for (const name of unique) {
    const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    if (new RegExp(`\\b(?:at|to|from|near)\\s+(?:the\\s+|my\\s+)?${escaped}\\b`, "i").test(text)) return name;
  }
  return null;
}

export function emailFromMemory(text: string, captures: Array<{ text: string }>) {
  const words = text.toLowerCase().match(/[a-z][a-z'-]{3,}/g) ?? [];
  const names = words.filter((word) => !STOP.has(word));
  for (const name of names) {
    for (const capture of captures) {
      const hay = capture.text.toLowerCase();
      if (!new RegExp(`\\b${name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\b`).test(hay)) continue;
      const email = extractEmail(capture.text);
      if (email) return { name, email };
    }
  }
  return null;
}

export function personInText(text: string, people: Person[], captures: Array<{ text: string }>): Person | null {
  const words = text.toLowerCase().match(/[a-z][a-z'-]{2,}/g) ?? [];
  const names = words.filter((word) => !STOP.has(word));
  for (const name of names) {
    const hit = people.find((person) => person.name.toLowerCase().split(/\s+/).includes(name));
    if (hit && (hit.phone.trim() || hit.email.trim())) return hit;
  }
  const phone = phoneFromMemory(text, captures);
  const email = emailFromMemory(text, captures);
  if (!phone && !email) return null;
  const name = phone?.name ?? email?.name ?? "";
  return { id: "memory", name, phone: phone?.phone ?? "", email: email?.email ?? "" };
}

export interface Plan {
  place: string;
  placeLabel: string;
  phones: string[];
  emails: string[];
  when: When | null;
  mapLink: string | null;
  remembered: string | null;
  missingNick: string | null;
  actions: Action[];
}

export function plan(
  text: string,
  reply: string,
  extra?: {
    place?: string;
    captures?: Array<{ text: string }>;
    people?: Person[];
    places?: SavedPlace[];
    model?: { place?: string; phone?: string; email?: string };
  },
): Plan {
  const reading = readText(text);
  const note = reply.trim() || "On my way.";
  const people = extra?.people ?? [];
  const places = extra?.places ?? [];
  const captures = extra?.captures ?? [];
  const street = text.match(STREET);
  const nick = nickInText(text, places);
  const saved = nick ? places.find((place) => place.name.trim().toLowerCase() === nick) : undefined;
  const modelPlace = extra?.model?.place?.trim() ?? "";
  const typed = extra?.place?.trim() ?? "";
  const ruled = street ? cleanPlace(street[0].replace(/\.$/, "")) || "" : reading.places[0] || "";
  const place = (street && ruled) || saved?.address || ruled || modelPlace || typed;
  const placeLabel = saved ? saved.name : place;
  const person = personInText(text, people, captures);
  const modelPhone = extra?.model?.phone ? extractPhone(extra.model.phone) : null;
  const modelEmail = extra?.model?.email && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(extra.model.email) ? extra.model.email : "";
  const phones = reading.phones.length
    ? reading.phones
    : person?.phone
      ? [person.phone]
      : modelPhone
        ? [modelPhone]
        : [];
  const emails = reading.emails.length ? reading.emails : person?.email ? [person.email] : modelEmail ? [modelEmail] : [];
  const remembered = reading.phones.length === 0 && person?.phone ? person.name : null;
  const actions: Action[] = [];

  if (place) {
    const named = saved ? `${saved.name}: ${place}` : place;
    actions.push({
      id: "uber",
      label: "Open Uber",
      detail: `Dropoff: ${named}. Uber still asks you to confirm the ride.`,
      href: uberUrl(place),
    });
    actions.push({
      id: "maps",
      label: "Directions in Maps",
      detail: `From your location to ${named}.`,
      href: mapsUrl(place),
    });
  }
  if (reading.mapLink) {
    actions.push({
      id: "map-link",
      label: "Open map link",
      detail: reading.mapLink,
      href: reading.mapLink,
    });
  }
  phones.slice(0, 2).forEach((phone, index) => {
    const who = index === 0 && remembered ? remembered : phone;
    actions.push({
      id: `sms-${index}`,
      label: remembered && index === 0 ? `Text ${remembered}` : "Text this number",
      detail: `${who}: “${note}”${remembered && index === 0 ? " From someone saved on this phone." : ""}`,
      href: smsUrl(phone, note),
    });
    actions.push({
      id: `call-${index}`,
      label: remembered && index === 0 ? `Call ${remembered}` : "Call",
      detail: phone,
      href: telUrl(phone),
    });
    actions.push({
      id: `wa-${index}`,
      label: "WhatsApp",
      detail: `${phone}. Add a country code in the chat if WhatsApp does not find it.`,
      href: whatsAppUrl(phone, note),
    });
  });
  emails.forEach((email, index) => {
    actions.push({
      id: `mail-${index}`,
      label: !reading.emails.length && person?.name ? `Email ${person.name}` : "Email them",
      detail: email,
      href: mailUrl(email, note),
    });
  });
  if (reading.when) {
    actions.push({
      id: "calendar",
      label: "Add to calendar",
      detail: reading.when.label,
      href: calendarUrl(reading.when, placeLabel || "Plan", text),
    });
  }
  const copy = [placeLabel && placeLabel !== place ? `${placeLabel}: ${place}` : place, phones[0], emails[0]]
    .filter(Boolean)
    .join("\n");
  if (copy) {
    actions.push({
      id: "copy",
      label: "Copy details",
      detail: "Copies what Nexus found so you can paste it into any app.",
      href: "",
      copy,
    });
  }

  return {
    place,
    placeLabel,
    phones,
    emails,
    when: reading.when,
    mapLink: reading.mapLink,
    remembered,
    missingNick: nick && !saved && !street ? nick : null,
    actions,
  };
}

export function buildActions(
  text: string,
  reply: string,
  extra?: {
    place?: string;
    captures?: Array<{ text: string }>;
    people?: Person[];
    places?: SavedPlace[];
    model?: { place?: string; phone?: string; email?: string };
  },
): Action[] {
  return plan(text, reply, extra).actions;
}

export function asksForMoney(text: string) {
  return /\b(wallet|bank|transfer|venmo|payment|pay)\b|\$\d/.test(text.toLowerCase());
}
