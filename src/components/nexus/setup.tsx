import { useEffect, useState } from "react";
import { COUNTRIES } from "@/lib/nexus/countries";
import { contactsSupported, pickContacts } from "@/lib/nexus/contacts";
import { useNexus } from "@/lib/nexus/store";

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
  const [note, setNote] = useState("");

  const chosen = COUNTRIES.find((item) => item.name === country);

  function finish() {
    const ready = rows.every((row) => row.name.trim() && row.phone.trim());
    if (!ready) {
      setNote("Add a name and phone for all five people.");
      return;
    }
    savePlace({ name: "Home", address: home.trim() });
    savePlace({ name: "Office", address: office.trim() });
    savePeople(rows.map((row) => ({ name: row.name.trim(), phone: row.phone.trim(), email: "" })));
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
            <label htmlFor="home" className="text-sm font-medium">
              Home
            </label>
            <input
              id="home"
              value={home}
              onChange={(event) => setHome(event.target.value)}
              placeholder="Street, area, city"
              className="h-11 rounded-lg border border-line bg-elevated px-3 text-base"
            />
            <label htmlFor="office" className="text-sm font-medium">
              Office
            </label>
            <input
              id="office"
              value={office}
              onChange={(event) => setOffice(event.target.value)}
              placeholder="Street, area, city"
              className="h-11 rounded-lg border border-line bg-elevated px-3 text-base"
            />
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
          <section className="flex flex-col gap-3" aria-label="Five contacts">
            <p className="text-sm text-muted">Save five people you call often. You can choose them from the phone.</p>
            <button
              type="button"
              onClick={() => {
                if (!contactsSupported()) {
                  setNote("Type the five numbers, or open Nexus in Chrome on this phone to choose contacts.");
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
                    setNote(picked.length < 5 ? `Added ${picked.length}. Fill the rest.` : "");
                  })
                  .catch(() => setNote("Contact picker was closed."));
              }}
              className="h-11 rounded-lg border border-line text-sm"
            >
              Choose from phone contacts
            </button>
            {rows.map((row, index) => (
              <div key={index} className="grid grid-cols-2 gap-2">
                <input
                  aria-label={`Contact ${index + 1} name`}
                  value={row.name}
                  onChange={(event) =>
                    setRows((current) => current.map((item, i) => (i === index ? { ...item, name: event.target.value } : item)))
                  }
                  placeholder={`Name ${index + 1}`}
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
            ))}
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
      anchor: "",
      title: profile.country || "Your country",
      body: profile.dial
        ? `Numbers without a country code are dialed as ${profile.dial}.`
        : "Saved numbers are used as you typed them.",
    },
    {
      anchor: "composer",
      title: "Home",
      body: `Home is ${home}. Type “pick me up at home”, then confirm before Uber or Maps opens.`,
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
    const anchor = steps[step]?.anchor;
    if (!anchor) return;
    document.querySelector(`[data-setup="${anchor}"]`)?.scrollIntoView({ block: "center", behavior: "smooth" });
  }, [step]);

  function done() {
    setProfile({ ...profile, tourDone: true });
  }

  if (!current) return null;

  return (
    <div className="fixed inset-x-0 bottom-0 z-20 px-4 pb-4">
      <div className="mx-auto w-full max-w-lg rounded-lg border border-line bg-elevated px-4 py-4 shadow-lg">
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
  );
}
