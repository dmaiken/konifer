#!/usr/bin/env bash

set -euo pipefail

if (( $# != 1 )); then
    echo "Usage: $0 json-report" >&2
    exit 2
fi
if ! command -v jq >/dev/null 2>&1; then
    echo "jq is required to check misconfiguration findings." >&2
    exit 127
fi

# Check only failed misconfigurations; licenses remain advisory at every severity.
blocking_findings="$(jq -er '
    if type != "object" then
        error("Expected a Trivy JSON report object")
    elif (.Results | type) != "array" then
        error("Expected a Trivy Results array")
    else
        [.Results[] | .Misconfigurations[]?
            | select(.Status == "FAIL")
            | select(.Severity | IN("MEDIUM", "HIGH", "CRITICAL"))
            | "\(.ID) (\(.Severity)): \(.Message)"]
        | join("\n")
    end
' "$1")"

if [[ -n "$blocking_findings" ]]; then
    echo "Blocking misconfigurations (MEDIUM, HIGH, CRITICAL):" >&2
    printf '%s\n' "$blocking_findings" >&2
    exit 1
fi

echo "No blocking misconfigurations (MEDIUM, HIGH, CRITICAL)."
