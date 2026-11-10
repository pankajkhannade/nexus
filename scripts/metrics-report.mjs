#!/usr/bin/env node
// Prints the Nexus cohort table from the opt-in metrics Worker (backend/metrics-worker).
//   node scripts/metrics-report.mjs [--since YYYY-MM-DD] [--json]
// Token: $METRICS_TOKEN, else the file at $METRICS_TOKEN_FILE (default /workspace/founder-ceo/nexus/secrets/metrics-token).
// URL:   $METRICS_URL (default https://nexus-metrics.safe2eat-api.workers.dev). The token is never printed.
import { readFileSync } from "node:fs";
import { renderReport } from "../backend/metrics-worker/src/report.js";

const args = process.argv.slice(2);
const since = args.includes("--since") ? args[args.indexOf("--since") + 1] : "";
const base = (process.env.METRICS_URL || "https://nexus-metrics.safe2eat-api.workers.dev").replace(/\/$/, "");
let token = process.env.METRICS_TOKEN;
if (!token) {
  const file = process.env.METRICS_TOKEN_FILE || "/workspace/founder-ceo/nexus/secrets/metrics-token";
  try { token = readFileSync(file, "utf8").trim(); } catch { console.error(`No token: set METRICS_TOKEN or put it in ${file}`); process.exit(2); }
}
const res = await fetch(`${base}/v1/aggregates${since ? `?since=${encodeURIComponent(since)}` : ""}`, { headers: { authorization: `Bearer ${token}` } });
if (!res.ok) { console.error(`metrics worker said ${res.status}`); process.exit(1); }
const { rows } = await res.json();
if (args.includes("--json")) { console.log(JSON.stringify(rows, null, 2)); process.exit(0); }
console.log(`Nexus cohort report, ${new Date().toISOString().slice(0, 10)} (UTC), ${rows.length} install-days\n`);
console.log(rows.length ? renderReport(rows) : "No uploads yet. Uploads are opt-in, so this stays empty until a tester family turns on daily counts.");
