#!/usr/bin/env bash
set -euo pipefail

# T36 operational probes. Pass the short-lived operator token through T36_ACCESS_TOKEN.
# Probe output contains only status, never authentication or response bodies.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_DIR="${T36_REPORT_DIR:-${ROOT_DIR}/build/t36-evidence}"

die() {
  echo "T36 gate failed: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "missing command: $1"
}

curl_status() {
  python3 "${ROOT_DIR}/scripts/t36-http-probe.py" "$1" status || die "HTTP status probe failed"
}

curl_runtime_diagnostics() {
  python3 "${ROOT_DIR}/scripts/t36-http-probe.py" "$1" diagnostics || die "runtime diagnostics probe failed"
}

contract() {
  require_command node
  require_command python3
  [[ -x "${ROOT_DIR}/scripts/t36-upgrade-gate.sh" ]] || die "gate script is not executable"
  node "${ROOT_DIR}/summer-ui/scripts/verify-t36-upgrade.mjs"
  echo "T36 contract gate passed"
}

migrations() {
  require_command python3
  python3 - "${ROOT_DIR}/summer-sample/src/main/resources/db/migration/mysql" <<'PY'
import re
import sys
from collections import Counter
from pathlib import Path

root = Path(sys.argv[1])
versions = []
for file in root.glob('V*__*.sql'):
    match = re.fullmatch(r'V([0-9]+(?:[._][0-9]+)*)__.+\.sql', file.name)
    if not match:
        print('invalid Flyway filename: ' + file.name)
        raise SystemExit(1)
    if match:
        parts = [int(part) for part in re.split(r'[._]', match.group(1))]
        while len(parts) > 1 and parts[-1] == 0:
            parts.pop()
        versions.append('.'.join(map(str, parts)))
duplicates = sorted(version for version, count in Counter(versions).items() if count > 1)
if duplicates:
    print('duplicate Flyway versions: ' + ', '.join(duplicates))
    raise SystemExit(1)
if not versions:
    print('no Flyway migrations found')
    raise SystemExit(1)
print(f'Flyway migration filenames valid: {len(versions)} versions')
PY
}

preflight() {
  require_command python3
  : "${T36_BASE_URL:?set T36_BASE_URL, for example http://127.0.0.1:8080}"
  curl_status "${T36_BASE_URL%/}/actuator/health" >/dev/null
  if [[ -z "${T36_ACCESS_TOKEN:-}" ]]; then
    die "set T36_ACCESS_TOKEN to an authenticated release-operator access token"
  fi
  curl_status "${T36_BASE_URL%/}/internal/v1/release/readiness" >/dev/null
  curl_runtime_diagnostics "${T36_BASE_URL%/}/internal/v1/diagnostics/runtime" >/dev/null
  echo "T36 runtime preflight passed"
}

backup() {
  require_command mysqldump
  require_command openssl
  require_command python3
  python3 "${ROOT_DIR}/scripts/t36-mysql-archive.py" backup "${REPORT_DIR}" || die "encrypted backup failed"
}

restore() {
  require_command mysql
  require_command openssl
  require_command python3
  python3 "${ROOT_DIR}/scripts/t36-mysql-archive.py" restore "${REPORT_DIR}" || die "isolated restore failed"
}

rolling() {
  require_command python3
  : "${T36_NODE_A_URL:?set T36_NODE_A_URL}"
  : "${T36_NODE_B_URL:?set T36_NODE_B_URL}"
  [[ -n "${T36_ACCESS_TOKEN:-}" ]] || die "set T36_ACCESS_TOKEN for authenticated node probes"
  local a b
  a="$(curl_status "${T36_NODE_A_URL%/}/internal/v1/release/readiness")" || die "node A is not ready"
  b="$(curl_status "${T36_NODE_B_URL%/}/internal/v1/release/readiness")" || die "node B is not ready"
  [[ "${a}" == "${b}" && "${a}" == "READY" ]] || die "nodes disagree on readiness"
  curl_status "${T36_NODE_A_URL%/}/actuator/health" >/dev/null
  curl_status "${T36_NODE_B_URL%/}/actuator/health" >/dev/null
  curl_runtime_diagnostics "${T36_NODE_A_URL%/}/internal/v1/diagnostics/runtime" >/dev/null
  curl_runtime_diagnostics "${T36_NODE_B_URL%/}/internal/v1/diagnostics/runtime" >/dev/null
  echo "T36 two-node readiness probes passed; artifact compatibility requires separate rolling evidence"
}

usage() {
  cat >&2 <<'EOF'
Usage: scripts/t36-upgrade-gate.sh {contract|migrations|preflight|backup|restore|rolling}

contract  validate the checked-in T36 matrix, runbook, config and API contracts
migrations detect duplicate Flyway version numbers before a real validate/migrate run
preflight probe one running node (health plus authenticated release readiness)
backup    create an encrypted transaction-consistent MySQL dump (passphrase from environment)
restore   restore encrypted backup only into a new database ending in _t36_restore (requires explicit opt-in)
rolling   compare authenticated readiness and health of two nodes
EOF
  exit 2
}

case "${1:-}" in
  contract) contract ;;
  migrations) migrations ;;
  preflight) preflight ;;
  backup) backup ;;
  restore) restore ;;
  rolling) rolling ;;
  *) usage ;;
esac
