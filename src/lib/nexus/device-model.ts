export interface ModelFill {
  place: string;
  phone: string;
  email: string;
}

interface ModelSession {
  prompt: (text: string) => Promise<string>;
  destroy?: () => void;
}

interface LanguageModelLike {
  availability: () => Promise<string>;
  create: (options?: object) => Promise<ModelSession>;
}

function engine(): LanguageModelLike | null {
  const host = globalThis as unknown as {
    LanguageModel?: LanguageModelLike;
    ai?: { languageModel?: LanguageModelLike };
  };
  return host.LanguageModel ?? host.ai?.languageModel ?? null;
}

export async function modelAvailability() {
  const model = engine();
  if (!model?.availability) return "unavailable";
  try {
    const state = await model.availability();
    return state || "unavailable";
  } catch {
    return "unavailable";
  }
}

export async function readWithModel(text: string): Promise<ModelFill | null> {
  const model = engine();
  if (!model) return null;
  const session = await model.create();
  try {
    const raw = await session.prompt(
      [
        "Extract facts from the message.",
        "Reply with JSON only, with keys place, phone, and email.",
        "Use an empty string when a fact is missing. Do not guess.",
        "",
        text.slice(0, 2000),
      ].join("\n"),
    );
    const start = raw.indexOf("{");
    const end = raw.lastIndexOf("}");
    if (start < 0 || end <= start) return null;
    const data = JSON.parse(raw.slice(start, end + 1)) as {
      place?: unknown;
      phone?: unknown;
      email?: unknown;
    };
    return {
      place: String(data.place ?? "").trim().slice(0, 120),
      phone: String(data.phone ?? "").trim().slice(0, 40),
      email: String(data.email ?? "").trim().slice(0, 80),
    };
  } finally {
    session.destroy?.();
  }
}
