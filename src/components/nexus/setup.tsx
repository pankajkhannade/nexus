import { useEffect, useState } from "react";
import { COUNTRIES, phoneProblem } from "@/lib/nexus/countries";
import { contactsSupported, pickContacts } from "@/lib/nexus/contacts";
import { useNexus } from "@/lib/nexus/store";
import { PlaceField } from "./place-field";

const EMPTY = { name: "", phone: "" };

export function Setup() {
  const savePlace = useNexus((s) => s.savePlace);
  const savePeople = useNexus((s) => s.savePeople);
  const setProfile = useNexus((s) => s.setProfile);
  const [step, setStep] = useState(0);
  const [country, setCountry] = useState("");
  const [home, setHome] = useState("");
  const [office, setOffice] = useState("");
  const [rows, setRows] = useState(() => Array.from({ length: 5 }, () => ({ ...EMPTY })));
  const [rowNotes, setRowNotes] = useState<string[]>(() => Array.from({ length: 5 }, () => ""));
  const [note, setNote] = useState("");

  const chosen = COUNTRIES.find((item) => item.name === country);

  function finish() {
    const problems = rows.map((row) => {
      const name = row.name.trim();
      const phone = row.phone.trim();
      if (!name && !phone) return "";
      if (!name) return "Add a name for this number.";
      if (!phone) return "Add a phone number.";
      return phoneProblem(phone, chosen?.dial ?? "", true);
    });
    const saved = rows.filter((row, index) => row.name.trim() && row.phone.trim() && !problems[index]);
    setNote(problems.some(Boolean) ? "" : saved.length ? "" : "Save at least one person.");
    setRowNotes(problems);
    if (problems.some(Boolean) || saved.length === 0) return;
    savePlace({ name: "Home", address: home.trim() });
    savePlace({ name: "Office", address: office.trim() });
    savePeople(saved.map((row) => ({ name: row.name.trim(), phone: row.phone.trim(), email: "" })));
    setProfile({
      country,
      dial: chosen?.dial ?? "",
      setupDone: true,
      tourDone: false,
    });
  }

  return (
    <main className="min-h-dvh bg-bg text-fg">
      <div className="mx-auto flex w-full max-w-lg flex-col gap-5 px-4 pt-6 pb-16">
        <header>
          <p className="text-xs font-medium tracking-widest text-muted">FIRST TIME</p>
          <h1 className="text-xl font-medium tracking-tight">Set up Nexus</h1>
          <p className="mt-2 text-sm text-muted">Step {step + 1} of 3. This stays on this phone.</p>
        </header>

        {step === 0 ? (
          <section className="flex flex-col gap-3" aria-label="Country">
            <label htmlFor="country" className="text-sm font-medium">
              Country of residence
            </label>
            <select
              id="country"
              value={country}
              onChange={(event) => setCountry(event.target.value)}
              className="h-11 rounded-lg border border-line bg-elevated px-3 text-base"
            >
              <option value="">Choose a country</option>
              {COUNTRIES.map((item) => (
                <option key={item.name} value={item.name}>
                  {item.name} ({item.dial})
                </option>
              ))}
            </select>
            <p className="text-sm text-muted">
              {chosen
                ? `A number without a country code is dialed as ${chosen.dial}. You can still type a full + number later.`
                : "Nexus uses this when a saved number has no country code."}
            </p>
            {note ? <p className="text-sm text-muted">{note}</p> : null}
            <button
              type="button"
              onClick={() => {
                if (!chosen) {
                  setNote("Choose the country you live in.");
                  return;
                }
                setNote("");
                setStep(1);
              }}
              className="h-11 rounded-lg bg-accent text-sm font-medium text-accent-fg"
            >
              Continue
            </button>
          </section>
        ) : null}

        {step === 1 ? (
          <section className="flex flex-col gap-3" aria-label="Home and office">
            <PlaceField
              id="home"
              label="Home"
              value={home}
              onChange={setHome}
              countryCode={chosen?.code ?? ""}
              placeholder="Search a place"
            />
            <PlaceField
              id="office"
              label="Office"
              value={office}
              onChange={setOffice}
              countryCode={chosen?.code ?? ""}
              placeholder="Search a place"
            />
            <p className="text-sm text-muted">Start typing a building, street, or area. Pick a match, or keep your own wording.</p>
            {note ? <p className="text-sm text-muted">{note}</p> : null}
            <div className="grid grid-cols-2 gap-2">
              <button type="button" onClick={() => setStep(0)} className="h-11 rounded-lg border border-line text-sm">
                Back
              </button>
              <button
                type="button"
                onClick={() => {
                  if (home.trim().length < 4 || office.trim().length < 4) {
                    setNote("Add both addresses.");
                    return;
                  }
                  setNote("");
                  setStep(2);
                }}
                className="h-11 rounded-lg bg-accent text-sm font-medium text-accent-fg"
              >
                Continue
              </button>
            </div>
          </section>
        ) : null}

        {step === 2 ? (
          <section className="flex flex-col gap-3" aria-label="Contacts">
            <p className="text-sm text-muted">Save at least one person. The other four are optional.</p>
            <button
              type="button"
              onClick={() => {
                if (!contactsSupported()) {
                  setNote("Type at least one number, or open Nexus in Chrome on this phone to choose contacts.");
                  return;
                }
                void pickContacts()
                  .then((picked) => {
                    if (!picked.length) {
                      setNote("No contact selected.");
                      return;
                    }
                    setRows((current) =>
                      current.map((row, index) =>
                        picked[index] ? { name: picked[index].name, phone: picked[index].phone } : row,
                      ),
                    );
                    setNote(picked.length ? "" : "No contact selected.");
                  })
                  .catch(() => setNote("Contact picker was closed."));
              }}
              className="h-11 rounded-lg border border-line text-sm"
            >
              Choose from phone contacts
            </button>
            {rows.map((row, index) => {
              const live = phoneProblem(row.phone, chosen?.dial ?? "", false);
              const message = rowNotes[index] || live;
              return (
                <div key={index} className="flex flex-col gap-1">
                  <div className="grid grid-cols-2 gap-2">
                    <input
                      aria-label={`Contact ${index + 1} name`}
                      value={row.name}
                      onChange={(event) =>
                        setRows((current) => current.map((item, i) => (i === index ? { ...item, name: event.target.value } : item)))
                      }
                      placeholder={index === 0 ? "Name" : `Name ${index + 1}, optional`}
                      className="h-11 rounded-lg border border-line bg-elevated px-3 text-base"
                    />
                    <input
                      aria-label={`Contact ${index + 1} phone`}
                      value={row.phone}
                      inputMode="tel"
                      onChange={(event) =>
                        setRows((current) => current.map((item, i) => (i === index ? { ...item, phone: event.target.value } : item)))
                      }
                      placeholder="Phone"
                      className="h-11 rounded-lg border border-line bg-elevated px-3 text-base"
                    />
                  </div>
                  {message ? <p className="text-sm text-danger">{message}</p> : null}
                </div>
              );
            })}
            {note ? <p className="text-sm text-muted">{note}</p> : null}
            <div className="grid grid-cols-2 gap-2">
              <button type="button" onClick={() => setStep(1)} className="h-11 rounded-lg border border-line text-sm">
                Back
              </button>
              <button type="button" onClick={finish} className="h-11 rounded-lg bg-accent text-sm font-medium text-accent-fg">
                Save and continue
              </button>
            </div>
          </section>
        ) : null}
      </div>
    </main>
  );
}

export function Tour() {
  const profile = useNexus((s) => s.profile);
  const people = useNexus((s) => s.people);
  const places = useNexus((s) => s.places);
  const setProfile = useNexus((s) => s.setProfile);
  const [step, setStep] = useState(0);
  const home = places.find((place) => place.name.toLowerCase() === "home")?.address ?? "your home";
  const office = places.find((place) => place.name.toLowerCase() === "office")?.address ?? "your office";
  const person = people[0]?.name ?? "someone you saved";
  const steps = [
    {
      anchor: "intro",
      title: profile.country || "Your country",
      body: profile.dial
        ? `Numbers without a country code are dialed as ${profile.dial}.`
        : "Saved numbers are used as you typed them.",
    },
    {
      anchor: "composer",
      title: "Home",
      body: `Home is ${home}. Type “pick me up at home”, then open Uber, Ola, or Rapido.`,
    },
    {
      anchor: "actions",
      title: "Uber, Ola, Rapido",
      body: "These buttons are highlighted. Compare rides opens all three with the same dropoff. Their prices stay inside those apps.",
    },
    {
      anchor: "composer",
      title: "Office",
      body: `Office is ${office}. Type “pick me up at office”.`,
    },
    {
      anchor: "composer",
      title: person,
      body: `Type “call ${person}”. Nexus uses the number you just saved, then you confirm the call.`,
    },
    {
      anchor: "actions",
      title: "You confirm",
      body: "The number or address is shown here before anything leaves this screen.",
    },
    {
      anchor: "book",
      title: "Change them later",
      body: "People and places stay on this phone. Edit them in this section.",
    },
  ];
  const current = steps[step];

  useEffect(() => {
    const nodes = document.querySelectorAll("[data-setup]");
    nodes.forEach((node) => node.classList.remove("nexus-spotlight"));
    const anchor = steps[step]?.anchor;
    const target = anchor ? document.querySelector(`[data-setup="${anchor}"]`) : null;
    target?.classList.add("nexus-spotlight");
    target?.scrollIntoView({ block: "center", behavior: "smooth" });
    return () => target?.classList.remove("nexus-spotlight");
  }, [step]);

  function done() {
    setProfile({ ...profile, tourDone: true });
  }

  if (!current) return null;

  return (
    <>
      <div className="pointer-events-none fixed inset-0 z-20 bg-fg/45" />
      <div className="fixed inset-x-0 bottom-0 z-40 px-4 pb-4">
        <div className="mx-auto w-full max-w-lg rounded-lg border border-line bg-elevated px-4 py-4">
        <p className="text-xs text-faint">
          Tour {step + 1} of {steps.length}
        </p>
        <p className="mt-1 text-base font-medium">{current.title}</p>
        <p className="mt-1 text-sm text-muted">{current.body}</p>
        <div className="mt-4 grid grid-cols-2 gap-2">
          <button type="button" onClick={done} className="h-11 rounded-lg border border-line text-sm">
            Skip
          </button>
          <button
            type="button"
            onClick={() => {
              if (step === steps.length - 1) done();
              else setStep((value) => value + 1);
            }}
            className="h-11 rounded-lg bg-accent text-sm font-medium text-accent-fg"
          >
            {step === steps.length - 1 ? "Done" : "Next"}
          </button>
        </div>
        </div>
      </div>
    </>
  );
}
