# Upgrade: {{TITLE}}

| | |
|--|--|
| **Upgrade id** | `{{UPGRADE_ID}}` |
| **Source** | {{SOURCE — e.g. Snyk report path, or "manual request"}} |
| **Started** | {{DATE}} |
| **Repos in scope** | see [repos](#repos) |

## Phase tracker

| # | Phase | Skill | State | Output | Updated |
|---|-------|-------|-------|--------|---------|
| 1 | Inventory | `/dep-inventory` | ⬜ not started | [01-inventory.md](01-inventory.md) | |
| 2 | Impact analysis | `/dep-analyze` | ⬜ not started | [02-impact.md](02-impact.md) | |
| 3 | Plan + approval | `/dep-analyze` | ⬜ not started | [03-plan.md](03-plan.md) | |
| 4 | Apply | `/dep-apply` | ⬜ not started | [04-apply-log.md](04-apply-log.md) | |
| 5 | Verify | `/dep-verify` | ⬜ not started | [05-verification.md](05-verification.md) | |

States: ⬜ not started · 🟡 in progress · ✅ done · ⛔ blocked · ⏸ awaiting approval

## ▶ Next step

{{ONE concrete action, e.g. "Review 03-plan.md, then run `/dep-apply {{UPGRADE_ID}}`"}}

## Repos

| Repo | Path | Baseline (before) | After upgrade |
|------|------|-------------------|---------------|

## Decisions and open questions

<!-- Append-only. Date each entry. Record every user approval here verbatim. -->
