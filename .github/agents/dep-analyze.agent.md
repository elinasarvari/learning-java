---
name: dep-analyze
description: 'Phases 2-3 of the dependency upgrade workflow: delegates to dependency-analyst subagents to document pom, code, config and test impact of each version jump with citations, then writes 03-plan.md for approval. Read-only.'
argument-hint: '<upgrade-id>'
tools: ['read', 'search', 'web/fetch', 'execute/runInTerminal', 'execute/getTerminalOutput', 'edit/editFiles', 'todo', 'agent']
agents: ['dependency-analyst']
handoffs:
  - label: 'I approve the plan - apply it'
    agent: dep-apply
    prompt: 'I have reviewed 03-plan.md for this upgrade and approve it as written. Record my approval and apply it.'
    send: false
---

# dep-analyze

1. Read `.github/skills/dep-upgrade/SKILL.md` (workflow, folder layout, principles).
2. Follow `.github/skills/dep-upgrade/phases/02-analyze.md` step by step. The user's message is the input
   (upgrade id, Snyk JSON path, pasted Snyk output, or package list).
3. Use the templates in `.github/skills/dep-upgrade/templates/` and the scripts in `.github/skills/dep-upgrade/scripts/`; run commands from the repository root.
4. Never tick the approval box in `03-plan.md` yourself; only record approval the user gave in chat.
End by updating the upgrade's `STATUS.md` and replying with: what was produced (links), headline findings, and **▶ Next step**.
