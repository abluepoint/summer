#!/usr/bin/env bash
set -euo pipefail

# T36 operational gate. It never prints credentials or response bodies. Use a
# curl config file with a protected Authorization header for authenticated
# endpoints; the file is supplied by the operator and is not created here.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_DIR="${T36_REPORT_DIR:-${ROOT_DIR}/build/t36-evidence}"
MYSQL_CONFIG=""

die() {
  echo "T36 gate failed: $*" >&2
  exit 1
}

cleanup() {
  if [[ -n "${MYSQL_CONFIG}" && -f "${MYSQL_CONFIG}" ]]; then
    python3 - "${MYSQL_CONFIG}" <<'PY'
import os
import sys
try:
    os.unlink(sys.argv[1])
except FileNotFoundError:
    pass
PY
  fi
}
trap cleanup EXIT

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "missing command: $1"
}

prepare_mysql_config() {
  : "${T36_DB_HOST:?set T36_DB_HOST}"
  : "${T36_DB_PORT:?set T36_DB_PORT}"
  : "${T36_DB_NAME:?set T36_DB_NAME}"
  : "${T36_DB_USER:?set T36_DB_USER}"
  : "${T36_DB_PASSWORD:?set T36_DB_PASSWORD}"
  MYSQL_CONFIG="$(mktemp "${TMPDIR:-/tmp}/summer-t36-mysql.XXXXXX")"
  chmod 600 "${MYSQL_CONFIG}"
  python3 - "${MYSQL_CONFIG}" <<'PY'
import os
import sys
from pathlib import Path

def ini(value):
    return value.replace('\\', '\\\\').replace('\n', '')

Path(sys.argv[1]).write_text(
    '[client]\n'
    f"host={ini(os.environ['T36_DB_HOST'])}\n"
    f"port={ini(os.environ['T36_DB_PORT'])}\n"
    f"user={ini(os.environ['T36_DB_USER'])}\n"
    f"password={ini(os.environ['T36_DB_PASSWORD'])}\n",
    encoding='utf-8',
)
PY
}

curl_status() {
  local url="$1"
  if [[ -n "${T36_CURL_CONFIG:-}" ]]; then
    curl --fail --silent --show-error --config "${T36_CURL_CONFIG}" "${url}"
  else
    curl --fail --silent --show-error "${url}"
  fi | python3 -c '
import json
import sys

try:
    payload = json.load(sys.stdin)
except (TypeError, json.JSONDecodeError):
    print('invalid-json')
    raise SystemExit(1)

status = payload.get('status')
if status is None:
    status = payload.get('data', {}).get('status')
print(status or 'UNKNOWN')
if status not in {'UP', 'READY'}:
    raise SystemExit(1)
' || die "HTTP probe failed: ${url}"
}

curl_runtime_diagnostics() {
  local url="$1"
  if [[ -n "${T36_CURL_CONFIG:-}" ]]; then
    curl --fail --silent --show-error --config "${T36_CURL_CONFIG}" "${url}"
  else
    curl --fail --silent --show-error "${url}"
  fi | python3 -c '
import json
import sys

try:
    payload = json.load(sys.stdin)
    data = payload.get("data", {})
    components = data.get("components", [])
    metrics = data.get("metrics", {})
except (TypeError, json.JSONDecodeError):
    raise SystemExit(1)

if payload.get("success") is not True or not isinstance(components, list):
    raise SystemExit(1)
if not isinstance(metrics.get("registeredMeters"), int):
    raise SystemExit(1)
for item in components:
    if not isinstance(item, dict) or not item.get("name") or not item.get("status"):
        raise SystemExit(1)
print("runtime-components=" + ",".join(sorted(item["name"] for item in components)))
' || die "runtime diagnostics probe failed: ${url}"
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
    match = re.match(r'V([^_]+)__', file.name)
    if match:
        versions.append(match.group(1))
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
  require_command curl
  require_command python3
  : "${T36_BASE_URL:?set T36_BASE_URL, for example http://127.0.0.1:8080}"
  curl_status "${T36_BASE_URL%/}/actuator/health" >/dev/null
  if [[ -z "${T36_CURL_CONFIG:-}" ]]; then
    die "set T36_CURL_CONFIG to a protected curl config containing the release-operator Authorization header"
  fi
  curl_status "${T36_BASE_URL%/}/internal/v1/release/readiness" >/dev/null
  curl_runtime_diagnostics "${T36_BASE_URL%/}/internal/v1/diagnostics/runtime" >/dev/null
  echo "T36 runtime preflight passed"
}

backup() {
  require_command mysqldump
  require_command python3
  prepare_mysql_config
  mkdir -p "${REPORT_DIR}"
  local target="${T36_BACKUP_FILE:-${REPORT_DIR}/summer-$(date -u +%Y%m%dT%H%M%SZ).sql}"
  mysqldump --defaults-extra-file="${MYSQL_CONFIG}" \
    --single-transaction --routines --events --triggers \
    --no-tablespaces "${T36_DB_NAME}" >"${target}"
  [[ -s "${target}" ]] || die "backup is empty"
  printf 'backup=%s\n' "${target}"
}

restore() {
  require_command mysql
  require_command python3
  [[ -n "${T36_BACKUP_FILE:-}" && -s "${T36_BACKUP_FILE}" ]] || die "set T36_BACKUP_FILE to a non-empty dump"
  [[ "${T36_ALLOW_RESTORE:-}" == "1" ]] || die "set T36_ALLOW_RESTORE=1 for an explicit restore rehearsal"
  : "${T36_RESTORE_DATABASE:?set T36_RESTORE_DATABASE}"
  [[ "${T36_RESTORE_DATABASE}" =~ ^[a-zA-Z0-9_]+_t36_restore$ ]] || die "restore database must end with _t36_restore"
  prepare_mysql_config
  mysql --defaults-extra-file="${MYSQL_CONFIG}" \
    --batch --skip-column-names \
    -e "CREATE DATABASE IF NOT EXISTS \`${T36_RESTORE_DATABASE}\`"
  mysql --defaults-extra-file="${MYSQL_CONFIG}" "${T36_RESTORE_DATABASE}" <"${T36_BACKUP_FILE}" >/dev/null
  local table_count
  table_count="$(mysql --defaults-extra-file="${MYSQL_CONFIG}" "${T36_RESTORE_DATABASE}" --batch --skip-column-names \
    -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")"
  [[ "${table_count}" =~ ^[0-9]+$ && "${table_count}" -gt 0 ]] || die "restored database has no tables"
  printf 'restored_database=%s tables=%s\n' "${T36_RESTORE_DATABASE}" "${table_count}"
}

rolling() {
  require_command curl
  require_command python3
  : "${T36_NODE_A_URL:?set T36_NODE_A_URL}"
  : "${T36_NODE_B_URL:?set T36_NODE_B_URL}"
  [[ -n "${T36_CURL_CONFIG:-}" ]] || die "set T36_CURL_CONFIG for authenticated node probes"
  local a b
  a="$(curl_status "${T36_NODE_A_URL%/}/internal/v1/release/readiness")" || die "node A is not ready"
  b="$(curl_status "${T36_NODE_B_URL%/}/internal/v1/release/readiness")" || die "node B is not ready"
  [[ "${a}" == "${b}" && "${a}" == "READY" ]] || die "nodes disagree on readiness"
  curl_status "${T36_NODE_A_URL%/}/actuator/health" >/dev/null
  curl_status "${T36_NODE_B_URL%/}/actuator/health" >/dev/null
  curl_runtime_diagnostics "${T36_NODE_A_URL%/}/internal/v1/diagnostics/runtime" >/dev/null
  curl_runtime_diagnostics "${T36_NODE_B_URL%/}/internal/v1/diagnostics/runtime" >/dev/null
  echo "T36 two-node rolling compatibility gate passed"
}

usage() {
  cat >&2 <<'EOF'
Usage: scripts/t36-upgrade-gate.sh {contract|migrations|preflight|backup|restore|rolling}

contract  validate the checked-in T36 matrix, runbook, config and API contracts
migrations detect duplicate Flyway version numbers before a real validate/migrate run
preflight probe one running node (health plus authenticated release readiness)
backup    create a transaction-consistent MySQL dump with routines/events/triggers
restore   restore only into a database ending in _t36_restore (requires explicit opt-in)
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
