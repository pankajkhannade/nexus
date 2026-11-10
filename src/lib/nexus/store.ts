import { create } from "zustand";
import {
  extractAddress,
  extractEmail,
  extractPhone,
  type Capture,
  type Person,
  type SavedPlace,
} from "./actions";

const STORAGE_KEY = "nexus-work-v2";
const LEGACY_KEY = "nexus-work-v1";

function stamp() {
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date());
}

function asCaptures(value: unknown): Capture[] {
  if (!Array.isArray(value)) return [];
  return value
    .filter(
      (item) =>
        item &&
        typeof item.id === "string" &&
        typeof item.text === "string" &&
        typeof item.at === "string",
    )
    .slice(0, 40) as Capture[];
}

function asPeople(value: unknown): Person[] {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item) => item && typeof item.id === "string" && typeof item.name === "string")
    .map((item) => ({
      id: item.id as string,
      name: String(item.name),
      phone: String(item.phone ?? ""),
      email: String(item.email ?? ""),
    }))
    .slice(0, 200);
}

function asPlaces(value: unknown): SavedPlace[] {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item) => item && typeof item.id === "string" && typeof item.name === "string" && typeof item.address === "string")
    .slice(0, 20) as SavedPlace[];
}

export interface Profile {
  country: string;
  dial: string;
  setupDone: boolean;
  tourDone: boolean;
}

export const emptyProfile: Profile = { country: "", dial: "", setupDone: false, tourDone: false };

function asProfile(value: unknown): Profile {
  if (!value || typeof value !== "object") return emptyProfile;
  const item = value as Partial<Profile>;
  return {
    country: typeof item.country === "string" ? item.country : "",
    dial: typeof item.dial === "string" ? item.dial : "",
    setupDone: item.setupDone === true,
    tourDone: item.tourDone === true,
  };
}

function loadAll() {
  const empty = {
    captures: [] as Capture[],
    people: [] as Person[],
    places: [] as SavedPlace[],
    profile: emptyProfile,
  };
  if (typeof localStorage === "undefined") return empty;
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const data = JSON.parse(raw) as { captures?: unknown; people?: unknown; places?: unknown; profile?: unknown };
      return {
        captures: asCaptures(data.captures),
        people: asPeople(data.people),
        places: asPlaces(data.places),
        profile: asProfile(data.profile),
      };
    }
    const legacy = localStorage.getItem(LEGACY_KEY);
    if (!legacy) return empty;
    const old = JSON.parse(legacy) as { captures?: unknown };
    return { captures: asCaptures(old.captures), people: [], places: [], profile: emptyProfile };
  } catch {
    return empty;
  }
}

function persist(captures: Capture[], people: Person[], places: SavedPlace[], profile: Profile) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ captures, people, places, profile }));
  } catch {
    /* private mode */
  }
}

interface NexusStore {
  draft: string;
  reply: string;
  captures: Capture[];
  people: Person[];
  places: SavedPlace[];
  profile: Profile;
  query: string;
  pendingId: string | null;
  hydrated: boolean;
  hydrate: () => void;
  setDraft: (draft: string) => void;
  setReply: (reply: string) => void;
  setQuery: (query: string) => void;
  setPending: (id: string | null) => void;
  saveCurrent: () => void;
  loadCapture: (id: string) => void;
  removeCapture: (id: string) => void;
  savePerson: (person: Omit<Person, "id">) => void;
  savePeople: (people: Array<Omit<Person, "id">>) => void;
  removePerson: (id: string) => void;
  savePlace: (place: Omit<SavedPlace, "id">) => void;
  removePlace: (id: string) => void;
  setProfile: (profile: Profile) => void;
  ingest: (text: string) => void;
}

export const useNexus = create<NexusStore>((set, get) => ({
  draft: "",
  reply: "On my way.",
  captures: [],
  people: [],
  places: [],
  profile: emptyProfile,
  query: "",
  pendingId: null,
  hydrated: false,

  hydrate: () => {
    if (get().hydrated) return;
    const data = loadAll();
    set({ ...data, hydrated: true });
  },

  setDraft: (draft) => set({ draft, pendingId: null }),
  setReply: (reply) => set({ reply, pendingId: null }),
  setQuery: (query) => set({ query }),
  setPending: (pendingId) => set({ pendingId }),

  saveCurrent: () => {
    const text = get().draft.trim();
    if (!text) return;
    const item: Capture = {
      id: `cap-${Date.now()}`,
      at: stamp(),
      text,
      address: extractAddress(text),
      phone: extractPhone(text),
      email: extractEmail(text),
    };
    const captures = [item, ...get().captures].slice(0, 40);
    set({ captures });
    persist(captures, get().people, get().places, get().profile);
  },

  loadCapture: (id) => {
    const item = get().captures.find((capture) => capture.id === id);
    if (!item) return;
    set({ draft: item.text, pendingId: null });
  },

  removeCapture: (id) => {
    const captures = get().captures.filter((capture) => capture.id !== id);
    set({ captures });
    persist(captures, get().people, get().places, get().profile);
  },

  savePerson: (person) => {
    const name = person.name.trim();
    if (!name || (!person.phone.trim() && !person.email.trim())) return;
    const next = { id: `person-${Date.now()}`, name, phone: person.phone.trim(), email: person.email.trim() };
    const people = [next, ...get().people.filter((item) => item.name.toLowerCase() !== name.toLowerCase())].slice(0, 200);
    set({ people });
    persist(get().captures, people, get().places, get().profile);
  },

  savePeople: (incoming) => {
    let people = get().people;
    incoming.forEach((person, index) => {
      const name = person.name.trim();
      if (!name || (!person.phone.trim() && !person.email.trim())) return;
      const next = {
        id: `person-${Date.now()}-${index}`,
        name,
        phone: person.phone.trim(),
        email: person.email.trim(),
      };
      people = [next, ...people.filter((item) => item.name.toLowerCase() !== name.toLowerCase())];
    });
    people = people.slice(0, 200);
    set({ people });
    persist(get().captures, people, get().places, get().profile);
  },

  removePerson: (id) => {
    const people = get().people.filter((person) => person.id !== id);
    set({ people });
    persist(get().captures, people, get().places, get().profile);
  },

  savePlace: (place) => {
    const name = place.name.trim();
    const address = place.address.trim();
    if (!name || !address) return;
    const next = { id: `place-${Date.now()}`, name, address };
    const places = [next, ...get().places.filter((item) => item.name.toLowerCase() !== name.toLowerCase())].slice(0, 20);
    set({ places });
    persist(get().captures, get().people, places, get().profile);
  },

  removePlace: (id) => {
    const places = get().places.filter((place) => place.id !== id);
    set({ places });
    persist(get().captures, get().people, places, get().profile);
  },

  setProfile: (profile) => {
    set({ profile });
    persist(get().captures, get().people, get().places, profile);
  },

  ingest: (text) => {
    const next = text.trim();
    if (!next) return;
    set({ draft: next, pendingId: null });
  },
}));
