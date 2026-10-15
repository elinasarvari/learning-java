---
name: dep-upgrade
description: Documented, resumable dependency upgrade workflow for Java/Maven repos, usually driven by Snyk findings. Scan every flagged package, inventory current vs target versions, analyse pom/code/config impact with citations, plan, apply on a branch, then build/test/boot every listed repo. Use for Snyk/CVE fixes, library or Spring Boot upgrades, or "what's the next step" on an upgrade.
---

# Dependency upgrade workflow

Shared by GitHub Copilot (`.github/agents`, `.github/prompts`) and Claude Code (`.claude/`).
**This folder is the single source of truth.** The tool-specific files only point here.

```
dep-scan ─▶ dep-inventory ─▶ dep-analyze ─▶ ⏸ user approves 03-plan.md ─▶ dep-apply ─▶ dep-verify
00-snyk-     01-inventory     02-impact                                   04-apply-log  05-verification
findings                      03-plan
```

| # | Phase | Procedure | Subagent (role) | Changes repos? |
|---|-------|-----------|-----------------|----------------|
| 0 | Scan — every Snyk-flagged package | [phases/00-scan.md](phases/00-scan.md) | — | no |
| 1 | Inventory — current vs target, levers, what moves | [phases/01-inventory.md](phases/01-inventory.md) | — | no |
| 2–3 | Impact + plan | [phases/02-analyze.md](phases/02-analyze.md) | `dependency-analyst` per (lever, repo) | no |
| 4 | Apply on `deps/<id>` | [phases/04-apply.md](phases/04-apply.md) | `upgrade-applier` per repo | **yes** |
| 5 | Verify build/tests/boot | [phases/05-verify.md](phases/05-verify.md) | `repo-verifier` per repo | no |

Subagent roles: [roles/](roles/). Doc templates: [templates/](templates/). Scripts:

| Script | Purpose |
|--------|---------|
| `scripts/snyk-scan.sh <repos.txt> <out> [snyk args]` | `snyk test --all-projects --json` per repo |
| `scripts/parse-snyk.py <json...> --md --csv --json` | every flagged package + changes grouped by direct dep |
| `scripts/maven-facts.sh <repo> <out> [filters]` | parent, declared deps, version props, resolved tree |
| `scripts/verify-repo.sh <repo> <out> [--smoke] [--build-cmd]` | build → JUnit counts → smoke boot → `summary.json` |

Run all paths from the repository root (the `learning-java` folder). Python is 3.9-compatible;
run it with `python3 -I`.

## Where things live

- Upgrade folders: `docs/upgrades/<YYYY-MM-DD>-<slug>/`, each with `STATUS.md` (phase tracker,
  ▶ next step, decisions) and the numbered docs.
- Repos in scope: `docs/upgrades/repos.txt`, or a `repos.txt` inside the upgrade folder that overrides it.

## Orchestrating (the `dep-upgrade` entry point)

1. **Which upgrade?**
   - `status` or an existing upgrade id → resume it.
   - A Snyk JSON path, pasted Snyk output, or nothing with "scan" → new upgrade starting at phase 0.
   - `group:artifact X->Y` pairs → new upgrade starting at phase 1 (phase 0 = "skipped, manual").
   - No argument → list the folders in `docs/upgrades/` with their phase rows and ask which one.
2. **Resume:** read `STATUS.md` and run the first phase that isn't ✅. Phase 4 needs the
   approval gate: `03-plan.md` has `- [x] Approved by user`, ticked only after the user approves in chat.
3. **One phase per run** by default. Continue only if the user asked to, and never past the gate.
4. **End every run** by updating STATUS (state, date, ▶ Next step) and replying with what was
   produced (links), the headline findings, and **▶ Next step**: one concrete command or decision.

## Principles

- **The repo is the source of truth, not the report.** Reconcile every version against
  `dependency:tree`; record report-vs-repo mismatches as Discrepancies for the user to decide.
- **Upgrade the lever, not the leaf.** Starters, Spring Framework, Jackson, Hibernate, Tomcat and
  Netty are managed by the Boot parent/BOM. Bump the parent; don't override one managed artifact
  across a major line.
- **Majors are stepped.** Read every intermediate line's migration guide (3.3 → 3.4 → 3.5 → 4.0 → 4.1).
- **Documentation is the product.** Each claim cites `path:line` or a URL. "Checked, not affected"
  lists are required.
- **Never hide red.** No skipping tests, `@Disabled`, or `-DskipTests` to get green.
- **No commits, pushes or PRs** unless the user asks. Work stays on `deps/<id>` branches.
