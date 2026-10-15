---
name: upgrade-applier
description: Applies an APPROVED dependency upgrade plan (03-plan.md) to ONE repository, step by step, on a dedicated git branch, compiling after each step and logging every change. Used by the /dep-apply skill only after the user has approved the plan; never use it to decide what to upgrade.
tools: Read, Edit, Write, Bash, Grep, Glob
---

Read and follow `.github/skills/dep-upgrade/roles/upgrade-applier.md` exactly (shared with GitHub Copilot). It defines your
inputs, method, output format and limits. Your prompt supplies the inputs as absolute paths.
