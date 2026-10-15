# Upgrade: Spring Boot → 4.1.1 (spring-boot-starter-web)

| | |
|--|--|
| **Upgrade id** | `2026-10-15-spring-boot-4-1` |
| **Source** | manual request: `spring-boot-starter-web` 3.5.16 → 4.1.1 (from Snyk; report not attached) |
| **Started** | 2026-10-15 |
| **Repos in scope** | see [repos](#repos) |

## Phase tracker

| # | Phase | Skill | State | Output | Updated |
|---|-------|-------|-------|--------|---------|
| 0 | Snyk scan | `dep-scan` | ⬜ not started — needs Snyk CLI or JSON export | [00-snyk-findings.md](00-snyk-findings.md) | 2026-10-15 |
| 1 | Inventory | `dep-inventory` | ⛔ needs decision (discrepancy) | [01-inventory.md](01-inventory.md) | 2026-10-15 |
| 2 | Impact analysis | `dep-analyze` | ⬜ not started | [02-impact.md](02-impact.md) | |
| 3 | Plan + approval | `dep-analyze` | ⬜ not started | [03-plan.md](03-plan.md) | |
| 4 | Apply | `dep-apply` | ⬜ not started | [04-apply-log.md](04-apply-log.md) | |
| 5 | Verify | `dep-verify` | ⬜ not started | [05-verification.md](05-verification.md) | |

States: ⬜ not started · 🟡 in progress · ✅ done · ⛔ blocked · ⏸ awaiting approval

## ▶ Next step

Run the full Snyk scan so the upgrade covers **every** flagged package, not just `spring-boot-starter-web`:
1. Install and log in to the Snyk CLI (`brew tap snyk/tap && brew install snyk-cli && snyk auth`), then run `/dep-scan 2026-10-15-spring-boot-4-1`.
   Or export `snyk test --all-projects --json` from wherever you scanned and run `/dep-scan <file>`.
2. Pick the scope (all / severity floor / rows). Then re-run `/dep-inventory`. That also settles
   where the reported 3.5.16 came from (the repo resolves 3.3.2).

## Repos

| Repo | Path | Baseline (before) | After upgrade |
|------|------|-------------------|---------------|
| learning-java/backend | `~/repos/learning-java/backend` | ✅ green — 39/0/0/0, boots (`main` @ `ce129d7`, [summary](verification/baseline/backend/summary.json)) | |

## Decisions and open questions

- 2026-10-15 — **Open:** reported current 3.5.16 ≠ resolved 3.3.2 (see 01 Discrepancies).
- 2026-10-15 — **Open:** Camel target 4.22.1 vs 4.23.0 (both built on Boot 4.1.1); prefer the LTS line — phase 2 to confirm.
