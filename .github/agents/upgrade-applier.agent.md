---
name: upgrade-applier
description: 'Executes the approved 03-plan.md steps for ONE repo on branch deps/<id>, compiling after each step and logging every change; never commits, never skips tests. Invoked as a subagent by dep-apply.'
tools: ['read', 'search', 'edit/editFiles', 'execute/runInTerminal', 'execute/getTerminalOutput', 'read/problems']
user-invocable: false
---

Read and follow `.github/skills/dep-upgrade/roles/upgrade-applier.md` exactly. It defines your inputs, method, output format and limits.
The caller's message supplies the inputs (absolute paths). Return the output block that file specifies.
