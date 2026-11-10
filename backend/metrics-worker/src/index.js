// Nexus opt-in metrics: a Cloudflare Worker + D1 (free tier). See docs/metrics.md and docs/decisions.md.
// POST /v1/daily       the app's daily aggregate counts (no auth: anyone can send counts, nothing can be read back)
// GET  /v1/aggregates  every stored row, for scripts/metrics-report.mjs. Bearer token; the Worker secret
//                      READ_TOKEN_SHA256 holds only the token's SHA-256 (hex), so the token itself never leaves the box.
// GET  /health
// No IP address, user agent or time of day is stored. Rows older than RETENTION_DAYS are deleted.

export const RETENTION_DAYS = 180;
const MAX_BODY = 64 * 1024;
const MAX_DAYS = 31;
const MAX_KEYS = 200;
const MAX_COUNT = 1_000_000;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const DAY = /^\d{4}-\d{2}-\d{2}$/;
const KEY = /^[a-z0-9_]{1,48}$/;
const VERSION = /^[0-9A-Za-z.\-]{1,20}$/;
const SOURCES = new Set(["unset", "family", "friend", "stranger"]);
const LANGS = new Set(["mr", "hi", "en", "other"]);

const json = (status, body) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

/** Returns the cleaned payload, or a string saying what is wrong. Anything unexpected is rejected, not stripped. */
export function validate(p) {
  if (!p || typeof p !== "object" || Array.isArray(p)) return "body must be an object";
  const allowed = new Set(["v", "install_id", "tester_source", "app_version", "lang", "days"]);
  for (const k of Object.keys(p)) if (!allowed.has(k)) return `unexpected field ${k}`;
  if (p.v !== 1) return "v must be 1";
  if (typeof p.install_id !== "string" || !UUID.test(p.install_id)) return "install_id must be a random UUID";
  if (!SOURCES.has(p.tester_source)) return "bad tester_source";
  if (typeof p.app_version !== "string" || !VERSION.test(p.app_version)) return "bad app_version";
  if (!LANGS.has(p.lang)) return "bad lang";
  if (!Array.isArray(p.days) || p.days.length === 0 || p.days.length > MAX_DAYS) return "days must be 1-31 entries";
  const days = [];
  for (const d of p.days) {
    if (!d || typeof d !== "object" || Array.isArray(d)) return "bad day entry";
    for (const k of Object.keys(d)) if (k !== "day" && k !== "counts") return `unexpected day field ${k}`;
    if (typeof d.day !== "string" || !DAY.test(d.day) || Number.isNaN(Date.parse(d.day))) return "bad day";
    if (!d.counts || typeof d.counts !== "object" || Array.isArray(d.counts)) return "counts must be an object";
    const keys = Object.keys(d.counts);
    if (keys.length > MAX_KEYS) return "too many counts";
    for (const k of keys) {
      const n = d.counts[k];
      if (!KEY.test(k)) return `bad count key`;
      if (!Number.isInteger(n) || n < 0 || n > MAX_COUNT) return `count ${k} must be a whole number`;
    }
    days.push({ day: d.day, counts: d.counts });
  }
  return { install_id: p.install_id, tester_source: p.tester_source, app_version: p.app_version, lang: p.lang, days };
}

export async function sha256Hex(text) {
  const bytes = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)));
  return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function sameToken(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length === 0 || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

const today = () => new Date().toISOString().slice(0, 10);

async function receive(request, env) {
  const text = await request.text();
  if (text.length > MAX_BODY) return json(413, { error: "too large" });
  let body;
  try { body = JSON.parse(text); } catch { return json(400, { error: "not json" }); }
  const p = validate(body);
  if (typeof p === "string") return json(400, { error: p });
  const received = today();
  const upsert = env.DB.prepare(
    "INSERT INTO daily (install_id, day, tester_source, app_version, lang, counts, received_on) VALUES (?, ?, ?, ?, ?, ?, ?) " +
      "ON CONFLICT(install_id, day) DO UPDATE SET tester_source = excluded.tester_source, app_version = excluded.app_version, " +
      "lang = excluded.lang, counts = excluded.counts, received_on = excluded.received_on",
  );
  const cutoff = new Date(Date.now() - RETENTION_DAYS * 86_400_000).toISOString().slice(0, 10);
  await env.DB.batch([
    ...p.days.map((d) => upsert.bind(p.install_id, d.day, p.tester_source, p.app_version, p.lang, JSON.stringify(d.counts), received)),
    env.DB.prepare("DELETE FROM daily WHERE day < ?").bind(cutoff),
  ]);
  return json(200, { ok: true, days: p.days.length });
}

async function aggregates(request, env, url) {
  const auth = request.headers.get("authorization") || "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  if (!token || !env.READ_TOKEN_SHA256 || !sameToken(await sha256Hex(token), env.READ_TOKEN_SHA256.toLowerCase())) {
    return json(401, { error: "unauthorized" });
  }
  const since = url.searchParams.get("since") || "0000-00-00";
  if (!DAY.test(since) && since !== "0000-00-00") return json(400, { error: "bad since" });
  const { results } = await env.DB.prepare(
    "SELECT install_id, day, tester_source, app_version, lang, counts FROM daily WHERE day >= ? ORDER BY install_id, day",
  ).bind(since).all();
  return json(200, { rows: results.map((r) => ({ ...r, counts: JSON.parse(r.counts) })) });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    try {
      if (url.pathname === "/health" && request.method === "GET") return json(200, { ok: true });
      if (url.pathname === "/v1/daily") return request.method === "POST" ? receive(request, env) : json(405, { error: "POST only" });
      if (url.pathname === "/v1/aggregates") return request.method === "GET" ? aggregates(request, env, url) : json(405, { error: "GET only" });
      return json(404, { error: "not found" });
    } catch {
      return json(500, { error: "server error" });
    }
  },
};
