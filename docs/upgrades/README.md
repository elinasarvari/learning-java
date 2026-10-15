# Dependency upgrades

Every dependency upgrade (usually triggered by Snyk findings) is documented in its own folder
here, produced by the Claude Code skills in [`.claude/skills/`](../../.claude/skills/).

## Quick start

Open Claude Code in the `learning-java` root, then:

```text
/dep-upgrade path/to/snyk.json                       # from a Snyk export (snyk test --json)
/dep-upgrade org.springframework.boot:spring-boot-starter-web 3.5.16->4.1.1   # or by hand
/dep-upgrade status                                   # where is each upgrade, what's next?
```

`/dep-upgrade` runs one phase at a time and always ends with **▶ Next step**. You can also
call each phase directly:

| # | Command | Writes | Touches repos? |
|---|---------|--------|----------------|
| 1 | `/dep-inventory <input>` | `01-inventory.md` — package, current, target, how the version is set, what moves with it | no |
| 2–3 | `/dep-analyze <id>` | `02-impact.md` — pom + code + config impact with citations; `03-plan.md` — ordered steps | no |
| ⏸ | *you approve `03-plan.md`* | approval recorded in `STATUS.md` | — |
| 4 | `/dep-apply <id>` | `04-apply-log.md` — changes on branch `deps/<id>`, uncommitted | **yes** |
| 5 | `/dep-verify <id>` | `05-verification.md` — build + tests + boot per repo, vs. baseline | no |

`/dep-verify baseline` health-checks every repo in [`repos.txt`](repos.txt) on its current
branch, outside of any upgrade.

## Pieces

| Kind | Name | Role |
|------|------|------|
| Skill | `dep-upgrade` | Orchestrator, folder layout, principles, scripts, templates |
| Skill | `dep-inventory` / `dep-analyze` / `dep-apply` / `dep-verify` | One phase each |
| Subagent | `dependency-analyst` | Read-only impact analysis of one package in one repo |
| Subagent | `upgrade-applier` | Executes the approved plan in one repo, compiling per step |
| Subagent | `repo-verifier` | Builds/tests/boots one repo and classifies failures |
| Script | `parse-snyk.py` | Groups Snyk rows by the direct dependency you actually change |
| Script | `maven-facts.sh` | Parent, declared deps, version properties, resolved tree |
| Script | `verify-repo.sh` | Build → JUnit counts → optional smoke boot → `summary.json` |

## Folder layout per upgrade

```text
docs/upgrades/2026-10-15-spring-boot-4-1/
├── STATUS.md               phase tracker, ▶ next step, decisions/approvals
├── 01-inventory.md
├── 02-impact.md
├── 03-plan.md
├── 04-apply-log.md
├── 05-verification.md
├── snyk-report.json        (if started from Snyk)
├── facts/<repo>/           maven-facts.sh output
├── analysis/<repo>--<artifact>.md   one per dependency-analyst
└── verification/{baseline,after}/<repo>/summary.json, diagnosis.md
```

## Guard rails

- The approval box in `03-plan.md` is ticked only when you approve in chat.
- `/dep-apply` only runs when you invoke it (`disable-model-invocation`), refuses dirty repos,
  and works on `deps/<id>`. Nothing is committed or pushed unless you ask.
- Tests are never skipped or disabled to get a green build.
