# Dependency upgrade workflow (Snyk / CVE fixes)

A set of slash commands, agents and scripts that take a Snyk scan to a verified upgrade across
repos. Every step is documented as it goes. The same commands work in **GitHub Copilot**
(VS Code, agent mode) and **Claude Code**.

```
/dep-scan ─▶ /dep-inventory ─▶ /dep-analyze ─▶ ⏸ you approve the plan ─▶ /dep-apply ─▶ /dep-verify
 every        current vs        code + pom        03-plan.md                changes on    build, tests,
 flagged      target, what      impact, plan                                deps/<id>     boot - every repo
 package      moves with it                                                 branch
```

Every command ends with **▶ Next step**. Copilot also shows the next phase as a button under its reply.

---

## 1. Setup (once)

| Need | How |
|------|-----|
| Java 21 + Maven wrapper | already in `backend/` (`./mvnw`) |
| Python 3.9+ | `python3 --version` (macOS ships with it) |
| Snyk CLI, only to scan from here | `brew tap snyk/tap && brew install snyk-cli` (or `npm install -g snyk`), then `snyk auth`, or `export SNYK_TOKEN=...` |
| Repos to scan, build and test | edit [`repos.txt`](repos.txt): one path per line, e.g. `~/repos/orders-service --smoke` |

No Snyk CLI? Export `snyk test --all-projects --json` from CI (or wherever you scan) and pass
the file to `/dep-scan`.

## 2. How to use it

Open the **`learning-java` folder as the workspace root**. The commands look for `.github/` and
`.claude/` there.

### In GitHub Copilot (VS Code)

1. Open Copilot Chat and switch to **Agent** mode.
2. Type `/dep-` and pick a command from the list. These come from `.github/prompts/`.
   You can also pick an agent such as **dep-upgrade** from the agent dropdown and chat to it normally.
3. When a phase finishes, click the handoff button under the reply, e.g. *Next: analyse impact and
   plan*. The button fills in the next prompt; you review it and press send.

### In Claude Code

Run `claude` in the `learning-java` folder, then type the same commands (`/dep-scan`, …).

### The commands

| # | Command | Example | What happens | Writes | Changes code? |
|---|---------|---------|--------------|--------|---------------|
| 0 | `/dep-scan` | `/dep-scan` or `/dep-scan ~/Downloads/snyk.json` | Runs Snyk on every repo in `repos.txt`, or reads your JSON. Lists **every** flagged package with current version, severity, CVEs, fixed-in, how it gets in, and the fix | `00-snyk-findings.md`, `snyk/findings.csv` | no |
| 1 | `/dep-inventory` | `/dep-inventory 2026-10-15-spring-boot-4-1` or `/dep-inventory org.springframework.boot:spring-boot-starter-web 3.5.16->4.1.1` | Checks each package against the real poms: version resolved today, target, what sets the version (parent/BOM/property), everything that moves with it, coupled libraries (e.g. Camel). Runs a **baseline** build | `01-inventory.md` | no |
| 2–3 | `/dep-analyze` | `/dep-analyze 2026-10-15-spring-boot-4-1` | Subagents read the official migration guides and search the code, config and tests. Each affected spot is listed with `file:line`. Then writes an ordered plan | `02-impact.md`, `03-plan.md` | no |
| ⏸ | *approve* | reply "approve" (or click *I approve the plan – apply it*) | Your approval is recorded, with your words and the date | `STATUS.md` | — |
| 4 | `/dep-apply` | `/dep-apply 2026-10-15-spring-boot-4-1` | Applies the plan step by step on branch `deps/<id>` and compiles after each step. **Refuses** repos with uncommitted changes. Never commits | `04-apply-log.md` | **yes** (uncommitted, on a branch) |
| 5 | `/dep-verify` | `/dep-verify 2026-10-15-spring-boot-4-1` | Every repo in `repos.txt`: full build, all tests, app boot. Compares to the baseline and marks each failure as regression, pre-existing or environment | `05-verification.md` | no |
| — | `/dep-upgrade` | `/dep-upgrade status` | Shows where each upgrade is, runs the next phase, and tells you the next step | `STATUS.md` | no |
| — | `/dep-verify baseline` | `/dep-verify baseline` | Health check of every repo on its current branch, outside any upgrade | `_health/<date>/` | no |

**Typical run:** `/dep-scan` → choose the scope ("all", "high and critical only", or rows 1–3)
→ `/dep-inventory` → `/dep-analyze` → read `03-plan.md`, reply *approve* → `/dep-apply` →
`/dep-verify` → review `git diff` on `deps/<id>`, then ask for a commit or PR.

---

## 3. Files that were added to this project

### Shared by Copilot and Claude: `.github/skills/dep-upgrade/`

The single source of truth. **To change how the workflow behaves, edit these files.** Both tools pick up the change.

| File | What it is |
|------|------------|
| `SKILL.md` | Overview: phases, folder layout, principles, how to resume an upgrade |
| `phases/00-scan.md` | Step-by-step procedure for `/dep-scan` |
| `phases/01-inventory.md` | Procedure for `/dep-inventory` |
| `phases/02-analyze.md` | Procedure for `/dep-analyze` (impact + plan) |
| `phases/04-apply.md` | Procedure for `/dep-apply`, including the approval and clean-repo gate |
| `phases/05-verify.md` | Procedure for `/dep-verify` |
| `roles/dependency-analyst.md` | Subagent: read-only impact analysis of one package in one repo |
| `roles/upgrade-applier.md` | Subagent: applies the approved plan in one repo, compiling per step |
| `roles/repo-verifier.md` | Subagent: builds, tests and boots one repo, and classifies failures |
| `scripts/snyk-scan.sh` | `snyk test --all-projects --json` for every repo in `repos.txt` |
| `scripts/parse-snyk.py` | Turns Snyk JSON into the findings tables + CSV |
| `scripts/maven-facts.sh` | Parent version, declared deps, version properties, resolved dependency tree |
| `scripts/verify-repo.sh` | Build → test counts → optional boot check → `summary.json` |
| `templates/STATUS.md`, `templates/00-…05-*.md` | Blank document templates, copied into each upgrade folder |

The scripts also work on their own, from the `learning-java` root:

```bash
S=.github/skills/dep-upgrade/scripts
bash $S/snyk-scan.sh docs/upgrades/repos.txt /tmp/snyk                     # scan all repos
python3 -I $S/parse-snyk.py /tmp/snyk/*.json --md findings.md --csv findings.csv   # every flagged package
bash $S/maven-facts.sh backend /tmp/facts spring-boot camel               # what resolves today
bash $S/verify-repo.sh backend /tmp/verify --smoke                        # build + tests + boot
```

### Copilot only: `.github/`

| File | What it is |
|------|------------|
| `prompts/dep-scan.prompt.md` … `dep-verify.prompt.md`, `dep-upgrade.prompt.md` | The `/dep-*` slash commands. Each routes to the agent of the same name |
| `agents/dep-scan.agent.md` … `dep-verify.agent.md`, `dep-upgrade.agent.md` | Phase agents. Tool permissions, which subagents they may call, next-step handoff buttons |
| `agents/dependency-analyst.agent.md`, `upgrade-applier.agent.md`, `repo-verifier.agent.md` | Subagents (`user-invocable: false`, so they don't show in the picker) |
| `instructions/maven-dependencies.instructions.md` | Rules Copilot applies whenever it edits any `pom.xml` |

### Claude Code only: `.claude/`

| File | What it is |
|------|------------|
| `skills/dep-*/SKILL.md` | The `/dep-*` commands. Short pointers to the shared `phases/` files |
| `agents/dependency-analyst.md`, `upgrade-applier.md`, `repo-verifier.md` | Subagents. Pointers to the shared `roles/` files |

### This folder: `docs/upgrades/`

| File | What it is |
|------|------------|
| `README.md` | This guide |
| `repos.txt` | Repos that are scanned, built and tested. **Edit to add your repos** |
| `.gitignore` | Keeps raw build logs and `_health/` runs out of git |
| `<date>-<slug>/` | One folder per upgrade (below) |

---

## 4. What each upgrade generates

Everything for one upgrade sits in one folder, e.g. `docs/upgrades/2026-10-15-spring-boot-4-1/`:

| File / folder | Created by | Contents |
|---------------|------------|----------|
| `STATUS.md` | every command | **Start here.** Phase tracker, ▶ next step, repos with baseline/after results, decisions and approvals |
| `00-snyk-findings.md` | `/dep-scan` | Severity totals; every flagged package; changes grouped by the dependency you edit |
| `snyk/<repo>.json`, `scan-index.tsv`, `findings.{md,json,csv}` | `/dep-scan` | Raw Snyk output per repo; `findings.csv` opens in Excel |
| `01-inventory.md` | `/dep-inventory` | Requested vs actual versions, discrepancies, what moves with the upgrade, coupled libraries |
| `facts/<repo>/` | `/dep-inventory` | `dependency-tree.txt`, `declared.txt`, `version-props.txt`, `facts.env` |
| `02-impact.md` | `/dep-analyze` | Risk summary + per-package breaking changes with `file:line` and source links, plus "checked, not affected" |
| `analysis/<repo>--<artifact>.md` | `/dep-analyze` | Full report from each analyst subagent |
| `03-plan.md` | `/dep-analyze` | Ordered steps with exact diffs, the approval box, rollback |
| `04-apply-log.md` | `/dep-apply` | What changed per step, compile results, deviations from the plan |
| `verification/baseline/<repo>/` | `/dep-inventory` or `/dep-apply` | `summary.json` from **before** the upgrade |
| `verification/after/<repo>/` | `/dep-verify` | `summary.json` + `diagnosis.md` from **after** |
| `05-verification.md` | `/dep-verify` | Result per repo, failures with root cause, go/no-go verdict |

---

## 5. Guard rails

- Read-only phases (scan, inventory, analyze, verify) never edit source code or poms.
- The plan is only approved when **you** approve it in chat. The agents never tick the box themselves.
- Apply runs only when you ask (Claude: `/dep-apply` can't be auto-triggered; Copilot: you must
  send the handoff). It works on `deps/<id>`, refuses dirty repos, and never commits, pushes or opens PRs.
- Tests are never skipped or disabled to get a green build. A red result is reported as it is.

## 6. Troubleshooting

| Symptom | Fix |
|---------|-----|
| `/dep-*` commands don't show up | Open `learning-java` itself as the workspace root, not a parent folder. In Copilot, use Agent mode |
| `snyk-scan.sh` exits 127 / 126 | CLI not installed / not logged in. See Setup, or pass a JSON export to `/dep-scan` |
| Snyk says one version, inventory another | Expected when the scan ran on another branch or repo. Listed under *Discrepancies* in `01-inventory.md` for you to decide |
| `/dep-apply` stops at "dirty repo" | Commit or stash your own changes first. The tool won't touch them |
| Build red only when repos run in parallel | Shared `~/.m2` download clash. Re-run that repo alone: `/dep-verify <id> <repo-name>` |
