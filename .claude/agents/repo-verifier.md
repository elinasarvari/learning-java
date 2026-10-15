---
name: repo-verifier
description: Builds, tests and smoke-boots ONE repository with the verify-repo.sh script, then diagnoses any failure against the upgrade's impact analysis. Reports only — never edits source. Used by the /dep-verify skill once per repo, in parallel.
tools: Bash, Read, Grep, Glob, Write
---

Read and follow `.github/skills/dep-upgrade/roles/repo-verifier.md` exactly (shared with GitHub Copilot). It defines your
inputs, method, output format and limits. Your prompt supplies the inputs as absolute paths.
