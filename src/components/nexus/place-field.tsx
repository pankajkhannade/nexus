import { useEffect, useId, useRef, useState } from "react";
import { searchPlaces } from "@/lib/nexus/place-search";

export function PlaceField({
  id,
  label,
  value,
  onChange,
  countryCode,
  placeholder,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  countryCode: string;
  placeholder: string;
}) {
  const listId = useId();
  const picked = useRef("");
  const [open, setOpen] = useState(false);
  const [hits, setHits] = useState<string[]>([]);
  const [status, setStatus] = useState("");
  const [active, setActive] = useState(0);

  useEffect(() => {
    const query = value.trim();
    if (query.length < 3 || query === picked.current) {
      setHits([]);
      setStatus("");
      setOpen(false);
      return;
    }
    let ignore = false;
    const handle = window.setTimeout(() => {
      setStatus("Searching");
      searchPlaces(query, countryCode)
        .then((places) => {
          if (ignore) return;
          setHits(places);
          setStatus(places.length ? "" : "No matching places. You can keep what you typed.");
          setOpen(true);
          setActive(0);
        })
        .catch(() => {
          if (ignore) return;
          setHits([]);
          setStatus("Place search is unavailable. You can keep what you typed.");
          setOpen(true);
        });
    }, 300);
    return () => {
      ignore = true;
      window.clearTimeout(handle);
    };
  }, [value, countryCode]);

  function choose(next: string) {
    picked.current = next;
    onChange(next);
    setOpen(false);
    setStatus("");
  }

  return (
    <div className={`relative flex flex-col gap-2 ${open ? "z-30" : ""}`}>
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        value={value}
        placeholder={placeholder}
        autoComplete="off"
        onChange={(event) => onChange(event.target.value)}
        onFocus={() => {
          if (hits.length || status) setOpen(true);
        }}
        onKeyDown={(event) => {
          if (!open) return;
          if (event.key === "ArrowDown") {
            event.preventDefault();
            setActive((index) => Math.min(index + 1, Math.max(hits.length - 1, 0)));
          } else if (event.key === "ArrowUp") {
            event.preventDefault();
            setActive((index) => Math.max(index - 1, 0));
          } else if (event.key === "Enter" && hits[active]) {
            event.preventDefault();
            choose(hits[active]);
          } else if (event.key === "Escape") {
            setOpen(false);
          }
        }}
        className="h-11 rounded-lg border border-line bg-elevated px-3 text-base"
      />
      {open ? (
        <ul id={listId} role="listbox" className="absolute top-full z-30 mt-1 max-h-64 w-full overflow-auto rounded-lg border border-line bg-elevated py-1">
          {hits.map((place, index) => (
            <li key={place} role="presentation">
              <button
                type="button"
                role="option"
                aria-selected={index === active}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => choose(place)}
                className={`block w-full px-3 py-3 text-left text-sm ${index === active ? "bg-subtle" : ""}`}
              >
                {place}
              </button>
            </li>
          ))}
          {status ? <li className="px-3 py-3 text-sm text-muted">{status}</li> : null}
        </ul>
      ) : null}
    </div>
  );
}
