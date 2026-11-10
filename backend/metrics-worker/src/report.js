// The cohort table for scripts/metrics-report.mjs, from /v1/aggregates rows. Pure, so it is unit-tested.
// Raw counts always go next to percentages: with 5-10 testers a percentage alone lies (docs/metrics.md).

const DAY_MS = 86_400_000;
const dayNum = (d) => Math.round(Date.parse(d + "T00:00:00Z") / DAY_MS);
export const pct = (n, d) => (d === 0 ? "-" : `${n}/${d} (${Math.round((100 * n) / d)}%)`);

/** Per install: tester source, setup day (from age_days), active day numbers, summed counts. */
export function installs(rows) {
  const by = new Map();
  for (const r of rows) {
    let i = by.get(r.install_id);
    if (!i) by.set(r.install_id, (i = { id: r.install_id, source: r.tester_source, lang: r.lang, setup: null, active: new Set(), activated: false, totals: {}, firstDay: dayNum(r.day) }));
    i.source = r.tester_source;
    i.lang = r.lang;
    const n = dayNum(r.day);
    i.firstDay = Math.min(i.firstDay, n);
    const c = r.counts || {};
    if (Number.isInteger(c.age_days) && i.setup === null) i.setup = n - c.age_days;
    if (c.active) i.active.add(n);
    if (c.activated) i.activated = true;
    for (const [k, v] of Object.entries(c)) if (k !== "age_days") i.totals[k] = (i.totals[k] || 0) + v;
  }
  return [...by.values()].map((i) => ({ ...i, start: i.setup ?? i.firstDay }));
}

/** The cohort numbers for one group of installs. [todayNum] = today's day number (UTC days since epoch). */
export function cohort(list, todayNum) {
  const out = {};
  const full7 = list.filter((i) => todayNum - i.start >= 7);
  const kept = full7.filter((i) => [...Array(7).keys()].filter((k) => i.active.has(i.start + k)).length >= 4);
  out.testers = list.length;
  out.killRule = { n: kept.length, of: full7.length, bar: full7.length === 0 ? "not enough data" : kept.length / full7.length >= 0.3 ? "PASS" : "FAIL" };
  const setupDone = list.filter((i) => i.setup !== null);
  out.activation = { n: setupDone.filter((i) => i.activated).length, of: setupDone.length };
  out.firstTry = {};
  for (const lang of ["mr", "hi", "en", "other"]) {
    const n = list.reduce((s, i) => s + (i.totals[`first_try_ok_${lang}`] || 0), 0);
    const of = list.reduce((s, i) => s + (i.totals[`first_try_${lang}`] || 0), 0);
    if (of > 0) out.firstTry[lang] = { n, of };
  }
  out.retention = {};
  for (const d of [1, 7, 30]) {
    const eligible = list.filter((i) => i.setup !== null && todayNum - i.setup > d);
    out.retention[`d${d}`] = { n: eligible.filter((i) => i.active.has(i.setup + d)).length, of: eligible.length };
  }
  const sum = (k) => list.reduce((s, i) => s + (i.totals[k] || 0), 0);
  out.crashFree = { n: Math.max(0, sum("app_open") - sum("crash")), of: sum("app_open") };
  const opened = list.reduce((s, i) => s + Object.entries(i.totals).filter(([k]) => k.startsWith("opened_")).reduce((a, [, v]) => a + v, 0), 0);
  out.wrongActions = { n: sum("wrong_action"), of: opened };
  return out;
}

/** Plain-text table: one column for everyone, then one per tester source. */
export function renderReport(rows, todayIso = new Date().toISOString().slice(0, 10)) {
  const all = installs(rows);
  const t = dayNum(todayIso);
  const groups = [["all", all], ...["family", "friend", "stranger", "unset"].map((s) => [s, all.filter((i) => i.source === s)]).filter(([, l]) => l.length)];
  const cols = groups.map(([name, list]) => [name, cohort(list, t)]);
  const r = (o) => (o ? pct(o.n, o.of) : "-");
  const lines = [
    ["metric", ...cols.map(([n]) => n)],
    ["testers", ...cols.map(([, c]) => String(c.testers))],
    ["kill rule: active 4+ of first 7 days (bar >= 3 of 10)", ...cols.map(([, c]) => `${r(c.killRule)} ${c.killRule.bar}`)],
    ["activation: voice success within 24 h of setup", ...cols.map(([, c]) => r(c.activation))],
    ...["mr", "hi", "en", "other"].map((l) => [`first-try voice success, ${l} (target 90%+)`, ...cols.map(([, c]) => r(c.firstTry[l]))]),
    ["D1 active", ...cols.map(([, c]) => r(c.retention.d1))],
    ["D7 active", ...cols.map(([, c]) => r(c.retention.d7))],
    ["D30 active", ...cols.map(([, c]) => r(c.retention.d30))],
    ["wrong action (back within 5 s) / actions opened", ...cols.map(([, c]) => r(c.wrongActions))],
    ["crash-free opens (target 99.5%+)", ...cols.map(([, c]) => r(c.crashFree))],
  ];
  const w = lines[0].map((_, i) => Math.max(...lines.map((l) => l[i].length)));
  return lines.map((l) => l.map((c, i) => c.padEnd(w[i])).join("  ")).join("\n") +
    "\n\nCounts are next to every %: with a handful of testers one family moves the % a lot. Family testers are kind; read the stranger column first.";
}
