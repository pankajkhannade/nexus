export interface PickedContact {
  name: string;
  phone: string;
  email: string;
}

interface RawContact {
  name?: string[];
  tel?: string[];
  email?: string[];
}

interface ContactPicker {
  select: (props: string[], options: { multiple: boolean }) => Promise<RawContact[]>;
}

function picker(): ContactPicker | null {
  const host = navigator as Navigator & { contacts?: ContactPicker };
  if (!host.contacts || typeof host.contacts.select !== "function") return null;
  return host.contacts;
}

export function contactsSupported() {
  return typeof navigator !== "undefined" && picker() !== null;
}

export async function pickContacts(): Promise<PickedContact[]> {
  const contacts = picker();
  if (!contacts) return [];
  const selected = await contacts.select(["name", "tel", "email"], { multiple: true });
  return selected
    .map((item) => ({
      name: (item.name?.[0] ?? "").trim(),
      phone: (item.tel?.[0] ?? "").trim(),
      email: (item.email?.[0] ?? "").trim(),
    }))
    .filter((item) => item.name && (item.phone || item.email));
}
