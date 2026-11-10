import { useEffect, useState } from "react";
import { ClipboardPaste, Download, Mic, Trash2 } from "lucide-react";
import { asksForMoney, nameMatches, plan, summarize, type Action } from "@/lib/nexus/actions";
import { contactsSupported, pickContacts } from "@/lib/nexus/contacts";
import { modelAvailability, readWithModel, type ModelFill } from "@/lib/nexus/device-model";
import { useNexus } from "@/lib/nexus/store";
import { Book } from "./book";

const SAMPLE =
  "Meet me at 418 Valencia St, apartment 3. Blue door. Call me at (415) 555-0138 if you get lost.";

function cx(...parts: Array<string | false | null | undefined>) {
  return parts.filter(Boolean).join(" ");
}

export function NexusApp() {
  const hydrate = useNexus((s) => s.hydrate);
  const ingest = useNexus((s) => s.ingest);
  useEffect(() => {
    hydrate();
    const params = new URLSearchParams(window.location.search);
    const shared = [params.get("title"), params.get("text"), params.get("url")]
      .filter((part): part is string => Boolean(part && part.trim()))
      .join("\n");
    if (shared) {
      ingest(shared);
      window.history.replaceState({}, "", window.location.pathname);
    }
  }, [hydrate, ingest]);

  return (
    <main className="min-h-dvh bg-bg text-fg">
      <div className="mx-auto flex w-full max-w-lg flex-col gap-6 px-4 pt-6 pb-16">
        <header className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="text-xs font-medium tracking-widest text-muted">ON THIS PHONE</p>
            <h1 className="text-xl font-medium tracking-tight">Nexus</h1>
          </div>
          <div className="flex flex-wrap items-center justify-end gap-2">
            <ThemeSwitch />
            <InstallButton />
          </div>
        </header>
        <p className="text-sm text-muted">
          Paste a message, or share it into Nexus. It opens Uber, Maps, Messages, WhatsApp, Mail, or Calendar after you
          confirm. It cannot press the last button inside those apps.
        </p>
        <Composer />
        <Findings />
        <Book />
        <Memory />
      </div>
    </main>
  );
}

function useMounted() {
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  return mounted;
}

function Composer() {
  const draft = useNexus((s) => s.draft);
  const setDraft = useNexus((s) => s.setDraft);
  const reply = useNexus((s) => s.reply);
  const setReply = useNexus((s) => s.setReply);
  const mounted = useMounted();
  const [listening, setListening] = useState(false);
  const [hint, setHint] = useState("");

  async function paste() {
    setHint("");
    try {
      const text = await navigator.clipboard.readText();
      if (!text.trim()) {
        setHint("Clipboard is empty.");
        return;
      }
      setDraft(text);
    } catch {
      setHint("Clipboard needs a tap and permission in Chrome.");
    }
  }

  function dictate() {
    const Ctor =
      (window as unknown as { SpeechRecognition?: new () => Recognition }).SpeechRecognition ??
      (window as unknown as { webkitSpeechRecognition?: new () => Recognition }).webkitSpeechRecognition;
    if (!Ctor) {
      setHint("Dictation needs Chrome on this phone.");
      return;
    }
    const rec = new Ctor();
    rec.lang = "en-US";
    rec.interimResults = false;
    rec.onresult = (event) => {
      const said = event.results[0]?.[0]?.transcript ?? "";
      setDraft(said);
      setListening(false);
    };
    rec.onerror = () => {
      setHint("Dictation stopped. Type or paste instead.");
      setListening(false);
    };
    rec.onend = () => setListening(false);
    setListening(true);
    setHint("");
    rec.start();
  }

  return (
    <section className="flex flex-col gap-3" aria-label="Capture">
      <label htmlFor="capture" className="text-sm font-medium">
        Text to act on
      </label>
      {mounted ? (
        <textarea
          id="capture"
          value={draft}
          rows={5}
          placeholder="Paste a text, an address, or an email"
          onChange={(event) => setDraft(event.target.value)}
          className="w-full resize-none rounded-lg border border-line bg-elevated px-3 py-3 text-base text-fg placeholder:text-faint"
        />
      ) : (
        <div className="h-36 rounded-lg border border-line bg-elevated" />
      )}
      <div className="grid grid-cols-2 gap-2">
        <button type="button" onClick={dictate} className="flex h-11 items-center justify-center gap-2 rounded-lg border border-line text-sm">
          <Mic className="size-4" aria-hidden="true" />
          {listening ? "Listening" : "Dictate"}
        </button>
        <button type="button" onClick={paste} className="flex h-11 items-center justify-center gap-2 rounded-lg border border-line text-sm">
          <ClipboardPaste className="size-4" aria-hidden="true" />
          Clipboard
        </button>
      </div>
      <button
        type="button"
        onClick={() => setDraft(SAMPLE)}
        className="h-11 rounded-lg text-sm text-muted"
      >
        Load a sample text
      </button>
      <label htmlFor="reply" className="text-sm font-medium">
        Reply to send
      </label>
      {mounted ? (
        <input
          id="reply"
          value={reply}
          onChange={(event) => setReply(event.target.value)}
          className="h-11 rounded-lg border border-line bg-elevated px-3 text-base"
        />
      ) : (
        <div className="h-11 rounded-lg border border-line bg-elevated" />
      )}
      {hint ? <p className="text-sm text-muted">{hint}</p> : null}
    </section>
  );
}

function Findings() {
  const draft = useNexus((s) => s.draft);
  const reply = useNexus((s) => s.reply);
  const captures = useNexus((s) => s.captures);
  const people = useNexus((s) => s.people);
  const places = useNexus((s) => s.places);
  const pendingId = useNexus((s) => s.pendingId);
  const setPending = useNexus((s) => s.setPending);
  const saveCurrent = useNexus((s) => s.saveCurrent);
  const savePeople = useNexus((s) => s.savePeople);
  const [placeOverride, setPlaceOverride] = useState("");
  const [modelFill, setModelFill] = useState<ModelFill | null>(null);
  const [modelState, setModelState] = useState("unavailable");
  const [modelNote, setModelNote] = useState("");
  const [modelBusy, setModelBusy] = useState(false);
  const [contactNote, setContactNote] = useState("");
  useEffect(() => {
    setPlaceOverride("");
    setModelFill(null);
    setModelNote("");
    setContactNote("");
  }, [draft]);
  useEffect(() => {
    void modelAvailability().then(setModelState);
  }, []);

  const shared = {
    captures,
    people,
    places,
    model: modelFill ?? undefined,
  };
  const automatic = plan(draft, reply, shared);
  const result = automatic.place ? automatic : plan(draft, reply, { ...shared, place: placeOverride });
  const pending = result.actions.find((action) => action.id === pendingId) ?? null;
  const money = asksForMoney(draft) && !result.place && result.phones.length === 0 && result.emails.length === 0;
  const missed =
    Boolean(draft.trim()) && !result.place && result.phones.length === 0 && result.emails.length === 0 && !result.mapLink;

  async function askModel() {
    setModelBusy(true);
    setModelNote("");
    try {
      const fill = await readWithModel(draft);
      if (!fill || (!fill.place && !fill.phone && !fill.email)) {
        setModelNote("The on-device model found nothing extra.");
        return;
      }
      setModelFill(fill);
      setModelNote("Filled the gaps the reader missed.");
    } catch {
      setModelNote("The on-device model did not answer. The reader still works.");
    } finally {
      setModelBusy(false);
    }
  }

  const placeText = result.place
    ? result.placeLabel === result.place
      ? result.place
      : `${result.placeLabel}: ${result.place}`
    : null;

  return (
    <section className="flex flex-col gap-3" aria-label="Actions">
      {draft.trim() ? (
        <div className="rounded-lg border border-line bg-elevated px-4 py-3">
          <p className="text-xs text-faint">Found on device</p>
          <dl className="mt-2 flex flex-col gap-1 text-sm">
            <Fact label="Place" value={placeText} />
            <Fact label="Phone" value={result.phones.length ? result.phones.join(", ") : null} />
            <Fact label="Email" value={result.emails.length ? result.emails.join(", ") : null} />
            {result.when ? <Fact label="When" value={result.when.label} /> : null}
          </dl>
          {result.remembered ? (
            <p className="mt-3 text-sm text-muted">Using {result.remembered}, saved on this phone.</p>
          ) : null}
          {result.ask === "call" && !result.phones.length && result.askedName ? (
            <div className="mt-3 flex flex-col gap-2">
              <p className="text-sm text-muted">
                Chrome cannot read your whole address book. Choose {result.askedName} from your contacts. Nexus then
                saves the number on this phone and can call them next time.
              </p>
              <button
                type="button"
                onClick={() => {
                  if (!contactsSupported()) {
                    setContactNote("Open Nexus in Chrome on this phone. This browser cannot open your contacts.");
                    return;
                  }
                  const wanted = result.askedName;
                  void pickContacts()
                    .then((picked) => {
                      if (!picked.length) {
                        setContactNote("No contact selected.");
                        return;
                      }
                      savePeople(picked);
                      const hit = picked.find((person) => nameMatches(person.name, wanted) && person.phone);
                      if (hit) setPending("call-0");
                      else setContactNote(`Saved ${picked.length}. None was named ${wanted}.`);
                    })
                    .catch(() => setContactNote("Contact picker was closed."));
                }}
                className="h-11 rounded-lg bg-accent text-sm font-medium text-accent-fg"
              >
                Find {result.askedName} in contacts
              </button>
              {contactNote ? <p className="text-sm text-muted">{contactNote}</p> : null}
            </div>
          ) : null}
          {result.missingNick ? (
            <p className="mt-3 text-sm text-muted">No address saved for {result.missingNick}. Add it under Places.</p>
          ) : null}
          {missed ? (
            <p className="mt-3 text-sm text-muted">
              No place, number, or email in that wording. Add the place below, save the person, or try the on-device
              model if this phone has one.
            </p>
          ) : null}
        </div>
      ) : null}
      {draft.trim() && !automatic.place ? (
        <label className="flex flex-col gap-2 text-sm">
          Place Nexus missed
          <input
            value={placeOverride}
            onChange={(event) => {
              setPlaceOverride(event.target.value);
              setPending(null);
            }}
            placeholder="Tartine Bakery, or 600 California St"
            className="h-11 rounded-lg border border-line bg-elevated px-3 text-base"
          />
        </label>
      ) : null}
      {draft.trim() ? (
        <div className="flex flex-col gap-2">
          <button
            type="button"
            disabled={modelBusy || modelState === "unavailable"}
            onClick={() => void askModel()}
            className="h-11 rounded-lg border border-line text-sm disabled:opacity-50"
          >
            {modelBusy
              ? "Reading"
              : modelState === "downloadable" || modelState === "downloading"
                ? "Download on-device model"
                : "Try on-device model"}
          </button>
          <p className="text-sm text-muted">
            {modelNote ||
              (modelState === "unavailable"
                ? "This phone has no built-in model. Nexus keeps using its reader."
                : "The built-in model only fills gaps. It stays on the phone.")}
          </p>
        </div>
      ) : null}
      {money ? <p className="text-sm text-muted">Nexus will not move money. Open your bank app yourself.</p> : null}
      {result.actions.length > 0 ? (
        <ul className="flex flex-col gap-2">
          {result.actions.map((action) => (
            <li key={action.id}>
              <button
                type="button"
                onClick={() => setPending(action.id)}
                className={cx(
                  "flex h-11 w-full items-center rounded-lg px-3 text-sm",
                  pendingId === action.id ? "bg-accent text-accent-fg" : "border border-line",
                )}
              >
                {action.label}
              </button>
            </li>
          ))}
        </ul>
      ) : null}
      {pending ? <Confirm action={pending} onClose={() => setPending(null)} /> : null}
      {draft.trim() ? (
        <button type="button" onClick={saveCurrent} className="h-11 rounded-lg border border-line text-sm">
          Save on this phone
        </button>
      ) : null}
    </section>
  );
}

function Fact({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="flex gap-3">
      <dt className="w-16 shrink-0 text-faint">{label}</dt>
      <dd>{value ?? "None"}</dd>
    </div>
  );
}

function Confirm({ action, onClose }: { action: Action; onClose: () => void }) {
  const [copied, setCopied] = useState(false);
  async function copy() {
    try {
      await navigator.clipboard.writeText(action.copy ?? "");
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }
  return (
    <div className="rounded-lg border border-line bg-subtle px-4 py-4">
      <p className="text-xs font-medium text-muted">Confirm</p>
      <p className="mt-1 text-base font-medium">{action.label}</p>
      <p className="mt-1 text-sm text-muted">{action.detail}</p>
      <div className="mt-4 grid grid-cols-2 gap-2">
        <button type="button" onClick={onClose} className="h-11 rounded-lg border border-line text-sm">
          Cancel
        </button>
        {action.copy ? (
          <button type="button" onClick={() => void copy()} className="h-11 rounded-lg bg-accent text-sm font-medium text-accent-fg">
            {copied ? "Copied" : "Copy"}
          </button>
        ) : (
          <a
            href={action.href}
            target="_blank"
            rel="noopener noreferrer"
            className="flex h-11 items-center justify-center rounded-lg bg-accent text-sm font-medium text-accent-fg"
          >
            Open
          </a>
        )}
      </div>
    </div>
  );
}

function ThemeSwitch() {
  const [theme, setTheme] = useState<"light" | "dark">("light");
  useEffect(() => {
    setTheme(document.documentElement.dataset.theme === "dark" ? "dark" : "light");
  }, []);
  function choose(next: "light" | "dark") {
    setTheme(next);
    if (next === "dark") document.documentElement.dataset.theme = "dark";
    else delete document.documentElement.dataset.theme;
    localStorage.setItem("nexus-theme", next);
    document.querySelector('meta[name="theme-color"]')?.setAttribute("content", next === "dark" ? "#0a0a0b" : "#f7f4ef");
  }
  return (
    <div className="flex rounded-lg border border-line p-1" role="radiogroup" aria-label="Theme">
      {(["light", "dark"] as const).map((id) => (
        <button
          key={id}
          type="button"
          role="radio"
          aria-checked={theme === id}
          onClick={() => choose(id)}
          className={cx("h-10 rounded-md px-3 text-sm capitalize", theme === id ? "bg-accent text-accent-fg" : "text-muted")}
        >
          {id}
        </button>
      ))}
    </div>
  );
}

function Memory() {
  const captures = useNexus((s) => s.captures);
  const query = useNexus((s) => s.query);
  const setQuery = useNexus((s) => s.setQuery);
  const loadCapture = useNexus((s) => s.loadCapture);
  const removeCapture = useNexus((s) => s.removeCapture);
  const q = query.trim().toLowerCase();
  const shown = q ? captures.filter((item) => item.text.toLowerCase().includes(q)) : captures;
  const mounted = useMounted();

  return (
    <section className="flex flex-col gap-3" aria-label="Saved text">
      <h2 className="text-base font-medium">Saved on this phone</h2>
      <label htmlFor="memory-search" className="sr-only">
        Search saved text
      </label>
      {mounted ? (
        <input
          id="memory-search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search saved text"
          className="h-11 rounded-lg border border-line bg-elevated px-3 text-sm placeholder:text-faint"
        />
      ) : (
        <div className="h-11 rounded-lg border border-line bg-elevated" />
      )}
      {shown.length === 0 ? (
        <p className="text-sm text-muted">Nothing saved yet. Captures stay in this browser, not on a server.</p>
      ) : (
        <ul>
          {shown.map((item) => (
            <li key={item.id} className="flex items-start gap-2 border-b border-line py-3">
              <button type="button" onClick={() => loadCapture(item.id)} className="min-w-0 flex-1 text-left">
                <p className="text-xs text-faint">{item.at}</p>
                <p className="mt-1 text-sm">{item.address ?? summarize(item.text)}</p>
              </button>
              <button
                type="button"
                aria-label="Delete saved text"
                onClick={() => removeCapture(item.id)}
                className="flex size-11 shrink-0 items-center justify-center rounded-lg text-muted"
              >
                <Trash2 className="size-4" aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

interface InstallPrompt extends Event {
  prompt: () => Promise<void>;
}

function InstallButton() {
  const [prompt, setPrompt] = useState<InstallPrompt | null>(null);
  const [open, setOpen] = useState(false);
  const [installed, setInstalled] = useState(false);

  useEffect(() => {
    const standalone = window.matchMedia("(display-mode: standalone)").matches;
    setInstalled(standalone);
    const onPrompt = (event: Event) => {
      event.preventDefault();
      setPrompt(event as InstallPrompt);
    };
    window.addEventListener("beforeinstallprompt", onPrompt);
    return () => window.removeEventListener("beforeinstallprompt", onPrompt);
  }, []);

  if (installed) return <p className="pt-1 text-xs text-muted">Installed</p>;

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => {
          if (prompt) {
            void prompt.prompt();
            return;
          }
          setOpen((value) => !value);
        }}
        className="flex h-10 items-center gap-2 rounded-lg bg-accent px-3 text-sm font-medium text-accent-fg"
      >
        <Download className="size-4" aria-hidden="true" />
        Install
      </button>
      {open ? (
        <div className="absolute right-0 z-10 mt-2 w-64 rounded-lg border border-line bg-elevated p-3 text-sm text-muted">
          <p>In Chrome on your phone: menu, then Install app or Add to Home screen. Open Nexus from that icon.</p>
          <p className="mt-2">From Messages, Share the text and choose Nexus.</p>
        </div>
      ) : null}
    </div>
  );
}

interface Recognition {
  lang: string;
  interimResults: boolean;
  onresult: ((event: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null;
  onerror: (() => void) | null;
  onend: (() => void) | null;
  start: () => void;
}
