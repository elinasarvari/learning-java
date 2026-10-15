---
name: dep-inventory
description: Phase 1 of the dependency upgrade workflow. Turns a Snyk report (JSON or pasted) or a manual list of packages into a verified inventory - package, current version, target version, how each version is set in the pom, what else moves with it - written to 01-inventory.md. Read-only. Use when starting a dependency/Snyk upgrade.
argument-hint: "<snyk.json | 'group:artifact current->target, ...'> [upgrade-id]"
---

# Phase 1 — Inventory

Read `.claude/skills/dep-upgrade/SKILL.md` first for folder layout and principles.
This phase changes nothing in any repo.

## Steps

1. **Upgrade folder.** Use the given upgrade id, or create
   `docs/upgrades/<YYYY-MM-DD>-<slug>/` and copy `templates/STATUS.md` into it.
   Copy `templates/01-inventory.md` → `01-inventory.md`. Set STATUS phase 1 to 🟡.

2. **Requested changes** — fill the first table from the input:
   - Snyk JSON (`snyk test --json` / `--all-projects`): run
     `python3 -I .claude/skills/dep-upgrade/scripts/parse-snyk.py <file> --md <dir>/snyk-summary.md --json <dir>/snyk-summary.json`
     and use its rows. Keep the raw report in the folder as `snyk-report.json`.
   - Pasted Snyk text or a manual list: extract `group:artifact`, current, target, and the
     issue ids/CVEs if present. If a target is missing, look up the latest patch of the
     fixed line on Maven Central
     (`https://repo1.maven.org/maven2/<group/as/path>/<artifact>/maven-metadata.xml`)
     and mark it `proposed`.
   - Confirm every requested target **exists** on Maven Central. A nonexistent version
     is a blocker.

3. **Repos in scope.** Read the upgrade folder's `repos.txt`, else `docs/upgrades/repos.txt`.
   For Snyk input, also add any project path from the report that exists locally, and list the
   ones that don't exist locally under Discrepancies. Fill the STATUS "Repos" table.

4. **Facts per repo** (run in parallel for several repos):
   `bash .claude/skills/dep-upgrade/scripts/maven-facts.sh <repo> <dir>/facts/<repo-name> <artifact substrings...>`
   Gradle repos: run `./gradlew dependencies --configuration runtimeClasspath` into the same
   folder and read it the same way.

5. **Reconcile** — fill "Reconciled against the repos" from `declared.txt`,
   `version-props.txt` and `dependency-tree.txt`:
   - What version is *actually* resolved now, and the **lever** that sets it (parent, BOM,
     property, explicit, or the direct dependency that pulls a transitive in).
   - Any mismatch with the report (e.g. Snyk says 3.5.16, the pom resolves 3.3.2) →
     **Discrepancies**, with the likely reason (other branch, stale scan, other repo).

6. **What moves with it.** If the lever is a platform parent/BOM (Spring Boot), fetch the
   target's managed versions — the `spring-boot-dependencies-<target>.pom` on Maven Central —
   and list each dependency in this repo's tree whose version changes (now → after).

7. **Coupled dependencies.** Every third-party BOM/starter *not* managed by the lever (Camel,
   springdoc, Testcontainers BOM, Lombok, MapStruct, cloud SDK BOMs…): record now-version and
   whether it supports the target, linking the compatibility statement (for Camel:
   https://camel.apache.org/camel-spring-boot/latest/spring-boot.html and the Camel release
   notes). Unknown → `check` and leave the details to phase 2.

8. **Close out.** Phase 1 → ✅ (or ⛔ if a blocker/discrepancy needs a user decision).
   ▶ Next step: either "resolve discrepancy X" or "run `/dep-analyze <upgrade-id>`".
   Reply with the requested-vs-reconciled table, discrepancies, and the next step.
