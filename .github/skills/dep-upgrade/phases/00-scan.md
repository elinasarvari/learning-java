# Phase 0 — Snyk scan: every flagged package

Goal: a complete list of **everything** Snyk flags across the repos in scope, not just the
package someone noticed, so nothing gets upgraded in isolation. Read-only for every repo.

## Input

One of:
- nothing → run the Snyk CLI over every repo in `repos.txt` (step 2);
- one or more Snyk JSON files (`snyk test --all-projects --json`, e.g. from CI) → skip to step 3;
- pasted Snyk output (CLI text, IDE plugin, or a web-UI issue list) → step 3, by hand.

## Steps

1. **Upgrade folder.** Use the given upgrade id, or create
   `docs/upgrades/<YYYY-MM-DD>-<slug>/` (slug from the main package or `snyk-sweep`) and copy
   `templates/STATUS.md` into it. STATUS phase 0 → 🟡.
2. **Scan** (only when no report was supplied):
   ```bash
   bash .github/skills/dep-upgrade/scripts/snyk-scan.sh docs/upgrades/repos.txt <upgrade>/snyk [--org=<org>]
   ```
   Exit 127/126 means the CLI is missing or not authenticated — stop and give the user the
   install/auth commands the script printed. Don't try to install or log in for them.
   Per-repo meanings are in `<upgrade>/snyk/scan-index.tsv` (0 clean, 1 findings, 2 failed,
   3 no supported project). A failed repo is reported, never silently dropped.
3. **Summarise all findings:**
   ```bash
   python3 -I .github/skills/dep-upgrade/scripts/parse-snyk.py <upgrade>/snyk/*.json \
     --md <upgrade>/snyk/findings.md --json <upgrade>/snyk/findings.json --csv <upgrade>/snyk/findings.csv
   ```
   For pasted output, build the same two tables by hand (columns from `templates/00-snyk-findings.md`).
4. **Write `00-snyk-findings.md`** from `templates/00-snyk-findings.md`: the scan summary, table 1
   (every flagged package, one row per `package@version`) and table 2 (changes grouped by the
   direct dependency to edit). Then add the triage column, using these buckets:
   - **platform** — the package is managed by a parent/BOM already in the pom (Spring Boot,
     Spring Cloud, Camel BOM, Jackson BOM...). Many rows collapse into **one** lever bump.
     Example: `spring-boot-starter-web` and `-websocket` at 3.5.16 → one parent bump.
   - **direct** — a version you declare yourself; bump it in the pom.
   - **transitive-pin** — no upgrade path through its parent; needs a
     `<dependencyManagement>` override (risky; call it out).
   - **no-fix / license** — nothing to upgrade to; needs a decision (accept, replace, ignore with reason).
5. **Close out.** Phase 0 → ✅. ▶ Next step: "pick the scope — `all`, a severity floor
   (e.g. `high+`), or specific rows — then run the inventory phase". Reply with the severity
   totals, the triaged table-2 rows, and the next step.
