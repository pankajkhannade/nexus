import { test } from "node:test";
import assert from "node:assert/strict";
import { installs, cohort, renderReport } from "../src/report.js";

const d = (n) => new Date(Date.UTC(2026, 10, 1) + n * 86_400_000).toISOString().slice(0, 10);
const dn = (n) => Math.round(Date.parse(d(n) + "T00:00:00Z") / 86_400_000);
const row = (id, src, n, counts) => ({ install_id: id, tester_source: src, lang: "mr", app_version: "1.0.0", day: d(n), counts });

// A: family, setup day 0, active days 0,1,2,4,7 -> 4 of first 7, activated, D1 + D7
// B: stranger, setup day 0, active days 0 and 3 -> 2 of first 7, not activated
// C: friend, setup day 5 -> not 7 days yet
const rows = [
  row("A", "family", 0, { active: 1, age_days: 0, activated: 1, app_open: 2, first_try_mr: 2, first_try_ok_mr: 2, opened_dialer: 2 }),
  row("A", "family", 1, { active: 1, age_days: 1, app_open: 1, first_try_mr: 1, first_try_ok_mr: 0, wrong_action: 1, opened_dialer: 1 }),
  row("A", "family", 2, { active: 1, age_days: 2, app_open: 1 }),
  row("A", "family", 4, { active: 1, age_days: 4, app_open: 1, crash: 1 }),
  row("A", "family", 7, { active: 1, age_days: 7, app_open: 1 }),
  row("B", "stranger", 0, { active: 1, age_days: 0, app_open: 1, first_try_hi: 3, first_try_ok_hi: 1 }),
  row("B", "stranger", 3, { active: 1, age_days: 3, app_open: 1 }),
  row("C", "friend", 5, { active: 1, age_days: 0, activated: 1, app_open: 1 }),
];

test("kill rule counts testers active 4+ of their first 7 days, only once 7 days have passed", () => {
  const c = cohort(installs(rows), dn(10));
  assert.deepEqual(c.killRule, { n: 1, of: 2, bar: "PASS" });
  assert.equal(cohort(installs(rows.filter((r) => r.install_id === "B")), dn(10)).killRule.bar, "FAIL");
  assert.equal(cohort(installs(rows.filter((r) => r.install_id === "C")), dn(10)).killRule.bar, "not enough data");
});

test("activation, first-try by language, retention, crash-free, wrong actions", () => {
  const c = cohort(installs(rows), dn(10));
  assert.deepEqual(c.activation, { n: 2, of: 3 });
  assert.deepEqual(c.firstTry.mr, { n: 2, of: 3 });
  assert.deepEqual(c.firstTry.hi, { n: 1, of: 3 });
  assert.deepEqual(c.retention.d1, { n: 1, of: 3 });
  assert.deepEqual(c.retention.d7, { n: 1, of: 2 });
  assert.deepEqual(c.retention.d30, { n: 0, of: 0 });
  assert.deepEqual(c.crashFree, { n: 8, of: 9 });
  assert.deepEqual(c.wrongActions, { n: 1, of: 3 });
});

test("the table has a column per tester source and raw counts", () => {
  const text = renderReport(rows, d(10));
  assert.match(text, /family/);
  assert.match(text, /stranger/);
  assert.match(text, /1\/2 \(50%\) PASS/);
  assert.match(text, /2\/3 \(67%\)/);
});
