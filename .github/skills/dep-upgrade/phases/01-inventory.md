# Phase 1 — Inventory

Turn the requested upgrades into a verified list: package, current version, target version,
how each version is set in the pom, and what else moves with it. Read-only.

## Input

- an upgrade id whose `00-snyk-findings.md` has a **Scope decision** → use the in-scope rows; or
- a manual list `group:artifact current->target, ...` → create the upgrade folder as in phase 0
  step 1 (phase 0 is then marked "skipped — manual request").

## Steps

1. Copy `templates/01-inventory.md` → `<upgrade>/01-inventory.md`. STATUS phase 1 → 🟡.
2. **Requested changes.** One row per in-scope change, with the Snyk issue ids/CVEs it fixes.
   Confirm every target **exists** on Maven Central
   (`https://repo1.maven.org/maven2/<group/as/path>/<artifact>/maven-metadata.xml`).
   A missing target → look up the latest patch of the fixed line, mark it `proposed`. A
   version that doesn't exist at all is a blocker.
3. **Repos in scope.** `<upgrade>/repos.txt` if present, else `docs/upgrades/repos.txt`. Add every
   Snyk project path that exists locally. List Snyk projects that don't exist locally under
   Discrepancies. Fill the STATUS "Repos" table.
4. **Facts per repo** (in parallel for several repos):
   ```bash
   bash .github/skills/dep-upgrade/scripts/maven-facts.sh <repo> <upgrade>/facts/<repo-name> <artifact substrings...>
   ```
   Gradle: `./gradlew dependencies --configuration runtimeClasspath > <upgrade>/facts/<repo-name>/dependency-tree.txt`.
5. **Reconcile** with `declared.txt`, `version-props.txt` and `dependency-tree.txt`: record the
   version that actually resolves now, and the **lever** that sets it (parent, BOM, property,
   explicit, or the direct dependency that pulls a transitive in). Any mismatch with the report,
   e.g. Snyk says 3.5.16 but the pom resolves 3.3.2 → **Discrepancies**, with the likely reason.
6. **What moves with it.** When the lever is a platform parent/BOM, fetch the target's managed
   versions, e.g. `spring-boot-dependencies-<target>.pom` on Maven Central. Then list every
   dependency in the tree whose version changes (now → after) and its jump type.
7. **Coupled dependencies.** List every third-party BOM/starter that the lever does *not* manage
   (Camel, springdoc, Testcontainers, Lombok, MapStruct, cloud SDKs...). For each, give its version
   now, whether it supports the target, and the minimum version that does, with evidence. Camel
   tip: `<spring-boot-version>` in `org.apache.camel:camel-parent:<v>` on Maven Central says
   which Boot that Camel release is built against.
8. **Baseline.** If STATUS has no baseline for a repo, run
   `bash .github/skills/dep-upgrade/scripts/verify-repo.sh <repo> <upgrade>/verification/baseline/<repo-name> --smoke`
   and record the result in the STATUS "Repos" table.
9. **Close out.** Phase 1 → ✅, or ⛔ if a discrepancy/blocker needs a user decision.
   ▶ Next step: "resolve discrepancy X" or "run the analyze phase".
