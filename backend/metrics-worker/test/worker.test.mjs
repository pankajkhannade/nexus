import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { validate, sha256Hex } from "../src/index.js";
import { fakeD1 } from "./fake-d1.mjs";

const ID = "3f2b8c1e-9a4d-4c2b-8e1f-0a1b2c3d4e5f";
const TOKEN = "t".repeat(40);
const HASH = await sha256Hex(TOKEN);
const env = () => ({ DB: fakeD1(), READ_TOKEN_SHA256: HASH });
const today = new Date().toISOString().slice(0, 10);
const body = (over = {}) => ({ v: 1, install_id: ID, tester_source: "family", app_version: "1.0.0", lang: "mr", days: [{ day: today, counts: { active: 1, app_open: 3, first_try_mr: 2, first_try_ok_mr: 1 } }], ...over });
const post = (e, b) => worker.fetch(new Request("https://x.workers.dev/v1/daily", { method: "POST", body: typeof b === "string" ? b : JSON.stringify(b) }), e);
const read = (e, token = TOKEN) => worker.fetch(new Request("https://x.workers.dev/v1/aggregates", { headers: token ? { authorization: `Bearer ${token}` } : {} }), e);

test("health", async () => {
  const r = await worker.fetch(new Request("https://x.workers.dev/health"), env());
  assert.equal(r.status, 200);
});

test("a valid day is stored and read back with the token", async () => {
  const e = env();
  assert.equal((await post(e, body())).status, 200);
  const r = await read(e);
  assert.equal(r.status, 200);
  const { rows } = await r.json();
  assert.equal(rows.length, 1);
  assert.deepEqual(rows[0].counts, { active: 1, app_open: 3, first_try_mr: 2, first_try_ok_mr: 1 });
  assert.equal(rows[0].tester_source, "family");
});

test("the same day sent again replaces the row (retries are idempotent)", async () => {
  const e = env();
  await post(e, body());
  await post(e, body({ days: [{ day: today, counts: { active: 1, app_open: 5 } }] }));
  const { rows } = await (await read(e)).json();
  assert.equal(rows.length, 1);
  assert.equal(rows[0].counts.app_open, 5);
});

test("reading needs the exact token", async () => {
  const e = env();
  assert.equal((await read(e, null)).status, 401);
  assert.equal((await read(e, "wrong")).status, 401);
  assert.equal((await read(e, TOKEN + "x")).status, 401);
  assert.equal((await read({ ...e, READ_TOKEN_SHA256: undefined }, TOKEN)).status, 401);
  // The hash itself is not a token
  assert.equal((await read(e, HASH)).status, 401);
});

test("anything that could carry personal data is rejected", async () => {
  const e = env();
  const bad = [
    body({ install_id: "android-id-1234" }),
    body({ name: "Pankaj" }),
    body({ tester_source: "my aunt" }),
    body({ lang: "marathi please" }),
    body({ days: [{ day: today, counts: { "Pankaj called": 1 } }] }),
    body({ days: [{ day: today, counts: { text: "bring soap" } }] }),
    body({ days: [{ day: today, counts: { calls: 1.5 } }] }),
    body({ days: [{ day: today, counts: { calls: -1 } }] }),
    body({ days: [{ day: today, counts: {}, phone: "+919876543210" }] }),
    body({ days: [{ day: "yesterday", counts: {} }] }),
    body({ days: [] }),
    body({ v: 2 }),
    "not json",
  ];
  for (const b of bad) assert.equal((await post(e, b)).status, 400, JSON.stringify(b));
  assert.equal(e.DB.raw.prepare("SELECT COUNT(*) AS n FROM daily").get().n, 0);
});

test("oversized bodies and wrong methods are refused", async () => {
  const e = env();
  assert.equal((await post(e, "x".repeat(70_000))).status, 413);
  assert.equal((await worker.fetch(new Request("https://x.workers.dev/v1/daily"), e)).status, 405);
  assert.equal((await worker.fetch(new Request("https://x.workers.dev/nope"), e)).status, 404);
});

test("rows older than 180 days are deleted on the next upload", async () => {
  const e = env();
  e.DB.raw.prepare("INSERT INTO daily VALUES (?, '2020-01-01', 'family', '1.0.0', 'mr', '{}', '2020-01-02')").run(ID);
  await post(e, body());
  const days = e.DB.raw.prepare("SELECT day FROM daily").all().map((r) => r.day);
  assert.deepEqual(days, [today]);
});

test("validate keeps only the known fields", () => {
  const p = validate(body());
  assert.deepEqual(Object.keys(p).sort(), ["app_version", "days", "install_id", "lang", "tester_source"]);
});
