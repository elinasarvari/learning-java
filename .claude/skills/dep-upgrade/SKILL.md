---
name: dep-upgrade
description: Orchestrates a documented dependency upgrade (typically from Snyk findings) for Java/Maven repos - inventory, impact analysis, plan, apply, verify across repos - and always ends by stating the next step. Use when the user wants to upgrade/bump library versions, fix Snyk/CVE findings, or asks "where are we / what's next" on an upgrade.
argument-hint: "[snyk.json | 'group:artifact current->target, ...' | upgrade-id | status]"
---

# Dependency upgrade workflow

A five-phase, resumable workflow. Every phase writes a document into one upgrade folder,
and every run ends by updating `STATUS.md` and telling the user the **next step**.

```
/dep-inventory ──▶ /dep-analyze ──▶ (user approves 03-plan.md) ──▶ /dep-apply ──▶ /dep-verify
  01-inventory      02-impact            ⏸ gate                    04-apply-log    05-verification
                    03-plan
```

| Phase | Skill | Subagent | Changes repos? |
|-------|-------|----------|----------------|
| 1 Inventory | `/dep-inventory` | — | no |
| 2+3 Impact + plan | `/dep-analyze` | `dependency-analyst` × (package, repo) | no |
| 4 Apply | `/dep-apply` | `upgrade-applier` × repo | **yes**, on branch `deps/<id>` |
| 5 Verify | `/dep-verify` | `repo-verifier` × repo | no (build output only) |

## Where things live (paths relative to the learning-java project root)

- Upgrade folders: `docs/upgrades/<YYYY-MM-DD>-<slug>/` — e.g. `2026-10-15-spring-boot-4-1`
- Repos in scope: `docs/upgrades/repos.txt` (default list) — an upgrade can override it with
  its own `repos.txt` in the upgrade folder
- Scripts: `.claude/skills/dep-upgrade/scripts/`
  - `parse-snyk.py <snyk.json> --md out.md --json out.json` — groups Snyk rows by the
    direct dependency you actually change
  - `maven-facts.sh <repo> <out-dir> [filters...]` — parent, declared deps, resolved tree
  - `verify-repo.sh <repo> <out-dir> [--smoke] [--build-cmd "..."]` — build/test/boot → `summary.json`
- Templates: `.claude/skills/dep-upgrade/templates/` — copy, never edit in place

## What to do when invoked

1. **Figure out which upgrade.** Parse `$ARGUMENTS`:
   - `status` or an existing upgrade id / folder → resume that upgrade (step 2).
   - a path to a Snyk JSON, pasted Snyk text, or `group:artifact X->Y` pairs → new upgrade.
     Create the folder, copy `templates/STATUS.md` into it, fill the header, and run
     `/dep-inventory` with the same input.
   - nothing → list folders in `docs/upgrades/` with their STATUS phase rows and ask which.
2. **Resume:** read `STATUS.md`, find the first phase not ✅, and run that phase's skill.
   Respect the gate: never start phase 4 unless `03-plan.md` has
   `- [x] Approved by user`. If it is unticked, show the plan summary and ask the user
   to approve; record their answer verbatim in STATUS "Decisions".
3. **Run one phase per invocation by default.** Continue into the next phase only if the
   user asked for it ("run through to verify") *and* the next phase is not behind the gate.
4. **Finish every run** by updating STATUS.md (phase state, date, ▶ Next step) and replying with:
   - what this phase produced (links to the docs),
   - the headline findings (risks, blockers, discrepancies),
   - **▶ Next step:** one concrete command or decision.

## Principles

- **The repo is the source of truth, not the report.** Snyk may be stale or scanned a
  different branch; always reconcile versions against `dependency:tree` and record
  discrepancies in 01-inventory.md for the user to decide.
- **Upgrade the lever, not the leaf.** Spring Boot starters, Spring Framework, Jackson,
  Hibernate, Tomcat, Netty… are managed by the Boot parent/BOM. Overriding a single managed
  artifact's version to jump a major line is almost always wrong; bump the parent instead
  and treat it as a platform upgrade.
- **Majors are stepped.** Analyse every intermediate line's migration guide (e.g.
  3.3 → 3.4 → 3.5 → 4.0 → 4.1). For big jumps, prefer a plan that lands on the latest
  minor of the current major first, with a green build, before crossing the major.
- **Documentation is the product.** Each claim cites a `path:line` or a URL. A list of
  checked-and-not-affected items is as important as the affected ones.
- **Never hide red.** No skipping tests, `@Disabled`, or `-DskipTests` to get green.
- **No commits/pushes/PRs** unless the user asks; work stays on `deps/<id>` branches.
