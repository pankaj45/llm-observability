#!/usr/bin/env node
// scripts/wait-ready.mjs
// Polls every app service health endpoint and prints a banner when all are up.
// Usage: node scripts/wait-ready.mjs

import { setTimeout as sleep } from "timers/promises";

const COMPOSE_FILE = "infra/docker-compose/docker-compose.yml";
const POLL_INTERVAL_MS = 3000;
const TIMEOUT_MS = 3 * 60 * 1000; // 3 minutes

const SERVICES = [
  { name: "web               :3000", url: "http://localhost:3000/health" },
  { name: "inference-gateway :8080", url: "http://localhost:8080/actuator/health" },
  { name: "analytics-query   :8081", url: "http://localhost:8081/actuator/health" },
  { name: "ingestion-worker  :8082", url: "http://localhost:8082/actuator/health" },
];

const GREEN  = "\x1b[32m";
const YELLOW = "\x1b[33m";
const RED    = "\x1b[31m";
const CYAN   = "\x1b[36m";
const BOLD   = "\x1b[1m";
const RESET  = "\x1b[0m";

async function checkService(url) {
  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(4000) });
    return res.ok || res.status === 200;
  } catch {
    return false;
  }
}

function statusLine(service, ready) {
  const icon   = ready ? `${GREEN}✔${RESET}` : `${YELLOW}…${RESET}`;
  const label  = ready ? `${GREEN}ready${RESET}` : `${YELLOW}waiting${RESET}`;
  return `  ${icon}  ${service.name.padEnd(28)}  ${label}`;
}

function clearLines(n) {
  // Move cursor up n lines and clear each one
  for (let i = 0; i < n; i++) {
    process.stdout.write("\x1b[1A\x1b[2K");
  }
}

async function main() {
  const started = Date.now();
  const ready   = new Array(SERVICES.length).fill(false);
  let   printed = 0;

  console.log(`\n${BOLD}${CYAN}Waiting for the LLM Observability Platform to be ready…${RESET}\n`);

  while (true) {
    // Check every service in parallel
    const results = await Promise.all(SERVICES.map((s) => checkService(s.url)));
    results.forEach((r, i) => { if (r) ready[i] = true; });

    // Redraw status block
    if (printed > 0) clearLines(printed);
    printed = 0;

    for (let i = 0; i < SERVICES.length; i++) {
      console.log(statusLine(SERVICES[i], ready[i]));
      printed++;
    }

    const elapsed = Math.round((Date.now() - started) / 1000);
    console.log(`\n  ${YELLOW}elapsed: ${elapsed}s${RESET}`);
    printed += 2;

    if (ready.every(Boolean)) {
      clearLines(printed);
      console.log(`
${BOLD}${GREEN}╔══════════════════════════════════════════════════════════════╗${RESET}
${BOLD}${GREEN}║         ✅  Platform is ready!                               ║${RESET}
${BOLD}${GREEN}╚══════════════════════════════════════════════════════════════╝${RESET}

  ${BOLD}Chatbot UI${RESET}        →  http://localhost:3000
  ${BOLD}Analytics Dashboard${RESET}→  http://localhost:3000/analytics
  ${BOLD}Grafana${RESET}           →  http://localhost:3001   (admin / admin)
  ${BOLD}Prometheus${RESET}        →  http://localhost:9090
  ${BOLD}inference-gateway${RESET} →  http://localhost:8080/actuator/health
  ${BOLD}analytics-query${RESET}   →  http://localhost:8081/actuator/health
  ${BOLD}ingestion-worker${RESET}  →  http://localhost:8082/actuator/health
`);
      process.exit(0);
    }

    if (Date.now() - started > TIMEOUT_MS) {
      const failed = SERVICES.filter((_, i) => !ready[i]).map((s) => s.name.trim());
      console.error(`\n${RED}Timed out after ${TIMEOUT_MS / 1000}s. Still not ready: ${failed.join(", ")}${RESET}`);
      process.exit(1);
    }

    await sleep(POLL_INTERVAL_MS);
  }
}

main();
