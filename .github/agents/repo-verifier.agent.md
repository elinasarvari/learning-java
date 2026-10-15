---
name: repo-verifier
description: 'Builds, tests and smoke-boots ONE repo with verify-repo.sh and classifies failures (regression / pre-existing / environmental) against the baseline and 02-impact.md. Reports only. Invoked as a subagent by dep-verify.'
tools: ['read', 'search', 'execute/runInTerminal', 'execute/getTerminalOutput', 'edit/editFiles']
user-invocable: false
---

Read and follow `.github/skills/dep-upgrade/roles/repo-verifier.md` exactly. It defines your inputs, method, output format and limits.
The caller's message supplies the inputs (absolute paths). Return the output block that file specifies.
