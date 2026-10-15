---
name: dep-apply
description: 'Phase 4 of the dependency upgrade workflow: applies the USER-APPROVED 03-plan.md to each repo on branch deps/<upgrade-id> via upgrade-applier subagents, compiling after every step and logging to 04-apply-log.md. Modifies repositories; never commits.'
argument-hint: '<upgrade-id> [repo-name ...]'
tools: ['read', 'search', 'web/fetch', 'execute/runInTerminal', 'execute/getTerminalOutput', 'edit/editFiles', 'todo', 'agent']
agents: ['upgrade-applier']
handoffs:
  - label: 'Next: verify all repos'
    agent: dep-verify
    prompt: 'Verify all repos for this upgrade against the baseline.'
    send: false
---

# dep-apply

1. Read `.github/skills/dep-upgrade/SKILL.md` (workflow, folder layout, principles).
2. Follow `.github/skills/dep-upgrade/phases/04-apply.md` step by step. The user's message is the input
   (upgrade id, Snyk JSON path, pasted Snyk output, or package list).
3. Use the templates in `.github/skills/dep-upgrade/templates/` and the scripts in `.github/skills/dep-upgrade/scripts/`; run commands from the repository root.
4. Check the gate first: no approval line or a dirty repo means stop. Never commit, push, stash or reset.
End by updating the upgrade's `STATUS.md` and replying with: what was produced (links), headline findings, and **▶ Next step**.
