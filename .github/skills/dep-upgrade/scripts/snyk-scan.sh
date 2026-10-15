#!/usr/bin/env bash
# Run a Snyk open-source scan on every repo in a repos.txt and keep the raw JSON per repo.
# Read-only for the repos (Snyk resolves the Maven/Gradle tree; it does not edit files).
#
# Usage: snyk-scan.sh <repos.txt> <out-dir> [extra snyk args...]
#   e.g. snyk-scan.sh docs/upgrades/repos.txt docs/upgrades/2026-10-15-x/snyk --org=my-org
#
# Needs the Snyk CLI, authenticated:
#   brew tap snyk/tap && brew install snyk-cli     (or: npm install -g snyk)
#   snyk auth                                       (or export SNYK_TOKEN=...)
#
# Outputs in <out-dir>:
#   <repo-name>.json   raw `snyk test --all-projects --json` per repo
#   scan-index.tsv     repo-name <TAB> path <TAB> exit-code <TAB> meaning
# Then summarise everything with:
#   python3 -I parse-snyk.py <out-dir>/*.json --md findings.md --csv findings.csv
set -uo pipefail

repos_file=${1:?repos.txt required}
out=${2:?out dir required}
shift 2

if ! command -v snyk >/dev/null 2>&1; then
  cat >&2 <<'EOF'
Snyk CLI not found. Install and authenticate, then re-run:
  brew tap snyk/tap && brew install snyk-cli   # or: npm install -g snyk
  snyk auth                                    # or: export SNYK_TOKEN=<token from app.snyk.io/account>
Alternatively export the JSON from your CI / Snyk run and pass it to parse-snyk.py directly.
EOF
  exit 127
fi
if [[ -z "${SNYK_TOKEN:-}" ]] && ! snyk config get api >/dev/null 2>&1; then
  echo "Snyk is not authenticated - run 'snyk auth' or export SNYK_TOKEN." >&2
  exit 126
fi

mkdir -p "$out"
out=$(cd "$out" && pwd)
printf 'repo\tpath\texit\tmeaning\n' > "$out/scan-index.tsv"

while IFS= read -r line; do
  line=${line%%#*}
  read -r path _ <<< "$line"
  [[ -z "${path:-}" ]] && continue
  path=${path/#\~/$HOME}
  # Name the output after the repo root + subdir, e.g. learning-java-backend.json
  name=$(echo "$path" | sed -E "s|^$HOME/repos/||; s|^/||; s|/|-|g")
  if [[ ! -d "$path" ]]; then
    printf '%s\t%s\t-\tmissing\n' "$name" "$path" >> "$out/scan-index.tsv"
    echo "SKIP $name: $path does not exist" >&2
    continue
  fi
  echo "SCAN $name ($path)" >&2
  # --all-projects picks up every pom/gradle file under the path (multi-module included).
  (cd "$path" && snyk test --all-projects --json "$@") > "$out/$name.json" 2> "$out/$name.err"
  code=$?
  case $code in
    0) meaning="no vulnerabilities" ;;
    1) meaning="vulnerabilities found" ;;
    2) meaning="scan failed - see $name.err" ;;
    3) meaning="no supported project found" ;;
    *) meaning="unexpected exit" ;;
  esac
  printf '%s\t%s\t%s\t%s\n' "$name" "$path" "$code" "$meaning" >> "$out/scan-index.tsv"
done < "$repos_file"

column -t -s $'\t' "$out/scan-index.tsv" >&2
