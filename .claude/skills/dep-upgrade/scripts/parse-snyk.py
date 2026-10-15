#!/usr/bin/env python3
"""Normalise `snyk test --json` output into an upgrade inventory.

Snyk reports one row per (vulnerability x dependency path). What you actually change
in a pom is the *direct* dependency at the top of that path, so rows are grouped by
the direct dependency and its recommended upgrade (upgradePath[1]).

Usage:
    python3 -I parse-snyk.py <snyk.json> [--json out.json] [--md out.md]

Accepts both shapes Snyk emits: a single project object, or a list of them
(`--all-projects`). Written for Python 3.9 - no match statements or `X | None`.
"""
import argparse
import json
import sys
from collections import OrderedDict

SEVERITY_ORDER = {"low": 1, "medium": 2, "high": 3, "critical": 4}


def split_coord(coord):
    """'group:artifact@1.2.3' -> ('group:artifact', '1.2.3'). Non-strings -> (None, None)."""
    if not isinstance(coord, str) or "@" not in coord:
        return None, None
    name, _, version = coord.rpartition("@")
    return name, version


def load_projects(path):
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    projects = data if isinstance(data, list) else [data]
    for project in projects:
        if not project.get("ok", True) and "error" in project:
            print("WARN: Snyk reported an error for %s: %s"
                  % (project.get("path", "?"), project["error"]), file=sys.stderr)
    return projects


def collect(projects):
    groups = OrderedDict()
    for project in projects:
        project_name = project.get("projectName") or project.get("path") or "unknown"
        target_file = project.get("displayTargetFile") or ""
        for vuln in project.get("vulnerabilities", []):
            path = vuln.get("from") or []
            upgrade = vuln.get("upgradePath") or []
            direct_name, direct_version = split_coord(path[1] if len(path) > 1 else None)
            if direct_name is None:
                # The project itself is vulnerable (no intermediate dependency).
                direct_name, direct_version = vuln.get("packageName"), vuln.get("version")
            # upgradePath[1] is false/absent when Snyk has no upgrade route.
            _, target_version = split_coord(upgrade[1] if len(upgrade) > 1 else None)

            key = (direct_name, direct_version, target_version)
            group = groups.setdefault(key, {
                "direct_dependency": direct_name,
                "current_version": direct_version,
                "target_version": target_version,
                "is_upgradable": bool(vuln.get("isUpgradable")),
                "is_patchable": bool(vuln.get("isPatchable")),
                "max_severity": "low",
                "projects": [],
                "vulnerable_packages": OrderedDict(),
            })
            location = "%s (%s)" % (project_name, target_file) if target_file else project_name
            if location not in group["projects"]:
                group["projects"].append(location)
            severity = (vuln.get("severity") or "low").lower()
            if SEVERITY_ORDER.get(severity, 0) > SEVERITY_ORDER.get(group["max_severity"], 0):
                group["max_severity"] = severity

            pkg_key = "%s@%s" % (vuln.get("packageName"), vuln.get("version"))
            pkg = group["vulnerable_packages"].setdefault(pkg_key, {
                "package": vuln.get("packageName"),
                "version": vuln.get("version"),
                "fixed_in": [],
                "issues": [],
            })
            for fixed in vuln.get("fixedIn") or []:
                if fixed not in pkg["fixed_in"]:
                    pkg["fixed_in"].append(fixed)
            cves = (vuln.get("identifiers") or {}).get("CVE") or []
            issue = "%s %s (%s)" % (vuln.get("id"), ",".join(cves), severity)
            if issue not in pkg["issues"]:
                pkg["issues"].append(issue)

    result = []
    for group in groups.values():
        group["vulnerable_packages"] = list(group["vulnerable_packages"].values())
        result.append(group)
    result.sort(key=lambda g: -SEVERITY_ORDER.get(g["max_severity"], 0))
    return result


def to_markdown(groups):
    lines = [
        "| # | Direct dependency | Current | Target (Snyk) | Max severity | Vulnerable transitive packages | Projects |",
        "|---|-------------------|---------|---------------|--------------|--------------------------------|----------|",
    ]
    for i, g in enumerate(groups, 1):
        pkgs = "<br>".join(
            "`%s@%s` -> fixed in %s" % (p["package"], p["version"], ", ".join(p["fixed_in"]) or "none")
            for p in g["vulnerable_packages"])
        target = g["target_version"] or ("_no upgrade path_" if not g["is_upgradable"] else "?")
        lines.append("| %d | `%s` | %s | %s | %s | %s | %s |" % (
            i, g["direct_dependency"], g["current_version"], target, g["max_severity"],
            pkgs, "<br>".join(g["projects"])))
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("report")
    parser.add_argument("--json", dest="json_out")
    parser.add_argument("--md", dest="md_out")
    args = parser.parse_args()

    groups = collect(load_projects(args.report))
    markdown = to_markdown(groups)
    if args.json_out:
        with open(args.json_out, "w", encoding="utf-8") as fh:
            json.dump(groups, fh, indent=2)
    if args.md_out:
        with open(args.md_out, "w", encoding="utf-8") as fh:
            fh.write(markdown)
    sys.stdout.write(markdown)
    print("\n%d direct dependencies to change across %d vulnerable packages."
          % (len(groups), sum(len(g["vulnerable_packages"]) for g in groups)), file=sys.stderr)


if __name__ == "__main__":
    main()
