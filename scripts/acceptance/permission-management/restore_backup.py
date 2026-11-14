#!/usr/bin/env python3
"""Authenticate a backup, restore into an empty isolated schema, and compare frozen ledgers.

The callable accepts an operator-owned SQL transport which must keep credentials in memory.
The CLI uses the native mysql client with MYSQL_PWD inherited from the operator environment.
No plaintext SQL file is created. A failed/partial target is never marked verified or retried
over existing tables: prepare a new empty isolated target before retrying.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys


def sha(value):
    return hashlib.sha256(value).hexdigest()


def restore(directory, manifest_hash, domain, bridge_hash, schema_hash, source_hash,
            source_schema, target_schema, query, output, import_query, purpose="LEGACY_UPGRADE"):
    output = Path(output)
    if output.exists() or output.is_symlink():
        raise ValueError("New restore report required")
    if (source_schema == target_schema or any(not re.fullmatch(r"[A-Za-z0-9_]{1,64}", item)
                                            for item in (source_schema, target_schema))):
        raise ValueError("Distinct explicit source and isolated target required")
    recovery = purpose in ("MANAGED_RECOVERY", "MANAGED_CONTRACT")
    if purpose not in ("LEGACY_UPGRADE", "MANAGED_RECOVERY", "MANAGED_CONTRACT"):
        raise ValueError("Explicit supported restore purpose required")
    if not re.fullmatch(r"ENTERPRISE:[A-Za-z0-9_-]{1,80}", domain) and not (recovery and domain == "PLATFORM:platform"):
        raise ValueError("Explicit supported full Domain required")
    if any(not re.fullmatch(r"[0-9a-f]{64}", item) for item in
           (manifest_hash, bridge_hash, schema_hash, source_hash)):
        raise ValueError("Exact approved backup provenance required")
    def scalar(sql, schema):
        return query(sql, schema).strip()
    for schema in (source_schema, target_schema):
        if scalar("SELECT DATABASE();", schema) != schema:
            raise ValueError("SQL transport schema mismatch")
    if scalar("SELECT count(*) FROM information_schema.tables WHERE table_schema=DATABASE();", target_schema) != "0":
        raise ValueError("Restore target must already exist and contain no tables")
    # The SQL stream never reaches the source/read transport. Import must use a dedicated
    # effective MySQL principal whose complete direct grants cover only this target schema.
    if import_query("SELECT DATABASE();", target_schema).strip() != target_schema:
        raise ValueError("Dedicated import connection target mismatch")
    principal = import_query("SELECT CURRENT_USER();", target_schema).strip()
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,32}@[A-Za-z0-9_.%:-]{1,255}", principal):
        raise ValueError("Explicit dedicated import principal required")
    grants = import_query("SHOW GRANTS;", target_schema).strip().splitlines()
    if not grants:
        raise ValueError("Actual import principal grants required")
    target_privileges = set()
    for grant in grants:
        match = re.fullmatch(r"GRANT (.+) ON (.+) TO .+", grant)
        if not match or "WITH GRANT OPTION" in grant:
            raise ValueError("Import principal cannot inherit roles/proxy or delegate grants")
        privileges, object_name = match.groups()
        if object_name == "*.*" and privileges == "USAGE":
            continue
        if object_name != "`" + target_schema.replace("_", "\\_") + "`.*":
            raise ValueError("Import principal must have no grants outside the isolated target")
        target_privileges.update(privilege.strip() for privilege in privileges.split(","))
    if "ALL PRIVILEGES" not in target_privileges and not {
            "SELECT", "INSERT", "CREATE", "DROP", "ALTER", "INDEX", "REFERENCES", "LOCK TABLES"
    }.issubset(target_privileges):
        raise ValueError("Dedicated target import grants do not support the single-schema dump recipe")
    source_mode = snapshot = None
    if not recovery:
        source_mode = scalar("SELECT concat(engine_mode,':',revision) FROM summer_authorization_domain WHERE domain_key='" + domain + "';", source_schema)
        if not re.fullmatch(r"LEGACY:[0-9]+", source_mode):
            raise ValueError("An explicit unchanged LEGACY source is required")
        snapshot = scalar("SELECT snapshot_id FROM summer_authorization_migration_snapshot WHERE domain_key='" + domain + "' AND source_fingerprint='" + source_hash + "';", source_schema)
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,96}", snapshot):
            raise ValueError("Unique captured source required")
        if scalar("SELECT count(*) FROM summer_authorization_writer_fence WHERE snapshot_id='" + snapshot + "' AND phase='FENCED';", source_schema) != "1":
            raise ValueError("Completed writer fence required before restore")
    arguments = [sys.executable, str(Path(__file__).with_name("encrypted_backup.py")), "restore", str(directory),
                 "--domain-key", domain, "--bridge-sha256", bridge_hash, "--schema-fingerprint", schema_hash,
                 "--source-fingerprint", source_hash, "--expected-manifest-sha256", manifest_hash]
    decrypted = subprocess.run(arguments, capture_output=True, timeout=120)
    if decrypted.returncode:
        raise ValueError("Backup authentication/decryption rejected")
    metadata = json.loads((Path(directory) / "manifest.json").read_bytes())
    if len(decrypted.stdout) != metadata["plaintextBytes"] or sha(decrypted.stdout) != metadata["plaintextSha256"]:
        raise ValueError("Decrypted SQL differs from the authenticated dump")
    # Supported recipe: mysqldump <one-schema>, without --databases/--all-databases. Reject
    # database selectors and client-side commands before sending any dump statement.
    if re.search(rb"(?im)^\s*(?:/\*!\d+\s*)?(?:USE\b|(?:CREATE|ALTER|DROP)\s+DATABASE\b|SOURCE\s|SYSTEM\s|\\[!.])", decrypted.stdout):
        raise ValueError("Unsupported database-selecting or client-command dump recipe")
    # MySQL dump streams may contain binary OAuth/credential BLOB literals. Preserve every
    # authenticated byte instead of assuming that an SQL dump is a UTF-8 text document.
    import_query(decrypted.stdout, target_schema)
    ledgers = {}
    for column in (() if recovery else ("snapshot_json", "evidence_json", "plan_json")):
        statement = "SELECT SHA2(" + column + ",256) FROM summer_authorization_migration_snapshot WHERE snapshot_id='" + snapshot + "';"
        before, after = (scalar(statement, schema) for schema in (source_schema, target_schema))
        if not re.fullmatch(r"[0-9a-f]{64}", before) or before != after:
            raise ValueError("Restored frozen ledger differs from actual source")
        ledgers[column] = before
    restored_mode = scalar("SELECT concat(engine_mode,':',revision) FROM summer_authorization_domain WHERE domain_key='" + domain + "';", target_schema)
    if not recovery and restored_mode != source_mode:
        raise ValueError("Restored mode/revision differs from actual source")
    if recovery:
        if not re.fullmatch(r"MANAGED:[0-9]+", restored_mode):
            raise ValueError("Recovery backup must contain an explicit valid MANAGED domain")
        if scalar("SELECT count(*) FROM summer_permission_bootstrap WHERE domain_key='" + domain + "';", target_schema) != "1":
            raise ValueError("Recovery backup must retain the original bootstrap marker")
        if scalar("SELECT (SELECT count(*) FROM summer_authorization_installation WHERE domain_key='" + domain + "')+(SELECT count(*) FROM summer_authorization_cutover WHERE domain_key='" + domain + "' AND purpose='PRODUCTION_CUTOVER');", target_schema) != "1":
            raise ValueError("Recovery backup must contain complete managed provenance")
    for schema in (() if recovery else (source_schema, target_schema)):
        if scalar("SELECT count(*) FROM summer_authorization_bridge_enrollment WHERE domain_key='" + domain + "' AND bridge_sha256='" + bridge_hash + "' AND schema_fingerprint='" + schema_hash + "';", schema) != "1":
            raise ValueError("Restored bridge provenance mismatch")
    receipt_format = ("summer-verified-managed-contract-restore/1" if purpose == "MANAGED_CONTRACT" else
                      "summer-verified-managed-recovery-restore/1" if recovery else "summer-verified-isolated-restore/1")
    receipt = dict(format=receipt_format, verified=True, domainKey=domain,
                   sourceFingerprint=source_hash, sourceSchema=source_schema, targetSchema=target_schema,
                   backupManifestSha256=manifest_hash, bridgeSha256=bridge_hash, schemaFingerprint=schema_hash,
                   snapshotId=snapshot, frozenLedgerHashes=ledgers, restoredModeRevision=restored_mode,
                   importPrincipal=principal, importGrants=grants,
                   importRecipe="single-schema/no-database-selectors/target-only-principal/1",
                   plaintextPersisted=False, cutoverAuthorized=False)
    if recovery:
        # This verifies transport/authentication/provenance only. The Java recovery command
        # independently reads both DBs and binds complete grant/mutation facts before any repair.
        receipt.update(recoveryAuthorized=False, currentFactsComparisonPending=True)
    fd = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as stream:
        json.dump(receipt, stream, sort_keys=True, indent=2)
        stream.write("\n")
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    for name in ("manifest-sha256", "domain-key", "bridge-sha256", "schema-fingerprint",
                 "source-fingerprint", "source-schema", "target-schema", "output"):
        parser.add_argument("--" + name, required=True)
    parser.add_argument("--purpose", choices=("LEGACY_UPGRADE", "MANAGED_RECOVERY", "MANAGED_CONTRACT"), default="LEGACY_UPGRADE")
    args = parser.parse_args()
    def query(statement, schema):
        command = [os.environ.get("SUMMER_MYSQL_CLIENT", "mysql"), "--batch", "--skip-column-names", "--database=" + schema]
        response = subprocess.run(command, input=statement if isinstance(statement, bytes) else statement.encode(), capture_output=True, timeout=120)
        if response.returncode:
            raise ValueError("SQL operation failed; prepare a new isolated target before retry")
        return response.stdout.decode("utf-8", errors="strict")
    def import_query(statement, schema):
        user = os.environ.get("SUMMER_RESTORE_MYSQL_USER", "")
        password = os.environ.get("SUMMER_RESTORE_MYSQL_PASSWORD", "")
        if not user or not password:
            raise ValueError("Separate dedicated target-only restore credentials required")
        command = [os.environ.get("SUMMER_MYSQL_CLIENT", "mysql"), "--batch", "--raw", "--binary-mode",
                   "--skip-column-names", "--user=" + user, "--database=" + schema]
        environment = dict(os.environ, MYSQL_PWD=password)
        response = subprocess.run(command, input=statement if isinstance(statement, bytes) else statement.encode(),
                                  capture_output=True, env=environment, timeout=120)
        if response.returncode:
            raise ValueError("Dedicated target-only SQL import failed")
        return response.stdout.decode("utf-8", errors="strict")
    receipt = restore(args.directory, args.manifest_sha256, args.domain_key, args.bridge_sha256,
                      args.schema_fingerprint, args.source_fingerprint, args.source_schema,
                      args.target_schema, query, args.output, import_query, args.purpose)
    print(json.dumps({"verified": True, "receiptSha256": sha(Path(args.output).read_bytes()),
                      "cutoverAuthorized": receipt["cutoverAuthorized"]}))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError, json.JSONDecodeError):
        print("Restore rejected; no credentials or SQL included in diagnostics", file=sys.stderr)
        sys.exit(1)
