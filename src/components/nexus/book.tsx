import { useEffect, useState } from "react";
import { Trash2 } from "lucide-react";
import { useNexus } from "@/lib/nexus/store";
import { contactsSupported, pickContacts } from "@/lib/nexus/contacts";

export function Book() {
  const people = useNexus((s) => s.people);
  const places = useNexus((s) => s.places);
  const savePerson = useNexus((s) => s.savePerson);
  const savePeople = useNexus((s) => s.savePeople);
  const removePerson = useNexus((s) => s.removePerson);
  const savePlace = useNexus((s) => s.savePlace);
  const removePlace = useNexus((s) => s.removePlace);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [placeName, setPlaceName] = useState("");
  const [address, setAddress] = useState("");
  const [contactNote, setContactNote] = useState("");
  const [ready, setReady] = useState(false);
  useEffect(() => setReady(true), []);
  if (!ready) {
    return (
      <section className="flex flex-col gap-4" aria-label="People and places" data-setup="book">
        <h2 className="text-base font-medium">People</h2>
        <h2 className="text-base font-medium">Places</h2>
      </section>
    );
  }

  return (
    <section className="flex flex-col gap-4" aria-label="People and places">
      <div>
        <h2 className="text-base font-medium">People</h2>
        <p className="mt-1 text-sm text-muted">
          Choose people from your phone once. After that, “call Aai” uses the saved number.
        </p>
      </div>
      <button
        type="button"
        onClick={() => {
          if (!contactsSupported()) {
            setContactNote("Open Nexus in Chrome on this phone to choose contacts.");
            return;
          }
          void pickContacts()
            .then((picked) => {
              if (!picked.length) {
                setContactNote("No contact selected.");
                return;
              }
              savePeople(picked);
              setContactNote(`Saved ${picked.length} from your phone.`);
            })
            .catch(() => setContactNote("Contact picker was closed."));
        }}
        className="h-11 rounded-lg bg-accent text-sm font-medium text-accent-fg"
      >
        Choose from phone contacts
      </button>
      {contactNote ? <p className="text-sm text-muted">{contactNote}</p> : null}
      <div className="grid gap-2">
        <input
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="Name"
          aria-label="Person name"
          className="h-11 rounded-lg border border-line bg-elevated px-3 text-base"
        />
        <input
          value={phone}
          onChange={(event) => setPhone(event.target.value)}
          placeholder="Phone"
          aria-label="Person phone"
          inputMode="tel"
          className="h-11 rounded-lg border border-line bg-elevated px-3 text-base"
        />
        <input
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="Email"
          aria-label="Person email"
          inputMode="email"
          className="h-11 rounded-lg border border-line bg-elevated px-3 text-base"
        />
        <button
          type="button"
          onClick={() => {
            savePerson({ name, phone, email });
            setName("");
            setPhone("");
            setEmail("");
          }}
          className="h-11 rounded-lg border border-line text-sm"
        >
          Save person
        </button>
      </div>
      {people.length > 0 ? (
        <ul>
          {people.map((person) => (
            <li key={person.id} className="flex items-start gap-2 border-b border-line py-3">
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium">{person.name}</p>
                <p className="text-sm text-muted">{[person.phone, person.email].filter(Boolean).join(" · ")}</p>
              </div>
              <button
                type="button"
                aria-label={`Delete ${person.name}`}
                onClick={() => removePerson(person.id)}
                className="flex size-11 shrink-0 items-center justify-center rounded-lg text-muted"
              >
                <Trash2 className="size-4" aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      ) : null}

      <div>
        <h2 className="text-base font-medium">Places</h2>
        <p className="mt-1 text-sm text-muted">Name Home or Office. “Pick me up at home” uses that address.</p>
      </div>
      <div className="flex gap-2">
        {["Home", "Office"].map((label) => (
          <button
            key={label}
            type="button"
            onClick={() => setPlaceName(label)}
            className="h-11 rounded-lg border border-line px-3 text-sm"
          >
            {label}
          </button>
        ))}
      </div>
      <div className="grid gap-2">
        <input
          value={placeName}
          onChange={(event) => setPlaceName(event.target.value)}
          placeholder="Name, such as Home"
          aria-label="Place name"
          className="h-11 rounded-lg border border-line bg-elevated px-3 text-base"
        />
        <input
          value={address}
          onChange={(event) => setAddress(event.target.value)}
          placeholder="Street address"
          aria-label="Place address"
          className="h-11 rounded-lg border border-line bg-elevated px-3 text-base"
        />
        <button
          type="button"
          onClick={() => {
            savePlace({ name: placeName, address });
            setPlaceName("");
            setAddress("");
          }}
          className="h-11 rounded-lg border border-line text-sm"
        >
          Save place
        </button>
      </div>
      {places.length > 0 ? (
        <ul>
          {places.map((place) => (
            <li key={place.id} className="flex items-start gap-2 border-b border-line py-3">
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium">{place.name}</p>
                <p className="text-sm text-muted">{place.address}</p>
              </div>
              <button
                type="button"
                aria-label={`Delete ${place.name}`}
                onClick={() => removePlace(place.id)}
                className="flex size-11 shrink-0 items-center justify-center rounded-lg text-muted"
              >
                <Trash2 className="size-4" aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}
