#!/usr/bin/env python3
"""Authenticated offline SQL backup. Plaintext travels only through stdin/stdout pipes.

Set SUMMER_BACKUP_PASSWORD to an independently stored, random password (32+ characters).
Use a trusted OpenSSL 3 executable, selected with SUMMER_OPENSSL if necessary.
The public manifest hash must be approved/stored separately before restore.
"""

import argparse
import datetime
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys

FORMAT = "summer-encrypted-sql-backup/2"
ITERATIONS = 600000


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode()


def digest_file(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def mac(password, metadata, ciphertext):
    key = hashlib.pbkdf2_hmac(
        "sha256", password, b"summer-backup-authentication/1\0" + bytes.fromhex(metadata["macSalt"]),
        ITERATIONS, 32)
    result = hmac.new(key, canonical(metadata), hashlib.sha256)
    with ciphertext.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def openssl(password, source, target, decrypt=False):
    binary = os.environ.get("SUMMER_OPENSSL") or shutil.which("openssl")
    if not binary:
        raise ValueError("Trusted OpenSSL 3 executable required")
    version = subprocess.run([binary, "version"], capture_output=True, check=True).stdout
    if not version.startswith(b"OpenSSL 3."):
        raise ValueError("Trusted OpenSSL 3 executable required")
    read_fd, write_fd = os.pipe()
    try:
        os.write(write_fd, password + b"\n")
        os.close(write_fd)
        write_fd = None
        arguments = [binary, "enc", "-aes-256-cbc", "-pbkdf2", "-iter", str(ITERATIONS),
                     "-md", "sha256", "-pass", "fd:" + str(read_fd)]
        match = re.match(rb"OpenSSL 3\.(\d+)\.", version)
        if not match:
            raise ValueError("Recognized OpenSSL 3 version required")
        if int(match.group(1)) >= 2:
            arguments += ["-saltlen", "8"]
        arguments.append("-d" if decrypt else "-salt")
        environment = dict(os.environ)
        environment.pop("SUMMER_BACKUP_PASSWORD", None)
        if decrypt:
            result = subprocess.run(arguments, stdin=source, stdout=target, stderr=subprocess.DEVNULL,
                                    pass_fds=(read_fd,), env=environment)
            if result.returncode:
                raise ValueError("Backup cryptographic operation failed")
        else:
            process = subprocess.Popen(arguments, stdin=subprocess.PIPE, stdout=target,
                                       stderr=subprocess.DEVNULL, pass_fds=(read_fd,), env=environment)
            plaintext = hashlib.sha256()
            size = 0
            try:
                for block in iter(lambda: source.read(1024 * 1024), b""):
                    plaintext.update(block)
                    size += len(block)
                    process.stdin.write(block)
                process.stdin.close()
                if process.wait() or size == 0:
                    raise ValueError("Nonempty successful SQL dump required")
                return size, plaintext.hexdigest()
            finally:
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
    finally:
        os.close(read_fd)
        if write_fd is not None:
            os.close(write_fd)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=["create", "restore"])
    parser.add_argument("directory", type=Path)
    parser.add_argument("--domain-key", required=True)
    parser.add_argument("--bridge-sha256", required=True)
    parser.add_argument("--schema-fingerprint", required=True)
    parser.add_argument("--source-fingerprint", required=True)
    parser.add_argument("--expected-manifest-sha256")
    args = parser.parse_args()
    if not re.fullmatch(r"(?:PLATFORM:platform|ENTERPRISE:[A-Za-z0-9_-]{1,80})", args.domain_key):
        raise ValueError("Explicit full Domain required")
    if any(not re.fullmatch(r"[0-9a-f]{64}", value)
           for value in (args.bridge_sha256, args.schema_fingerprint, args.source_fingerprint)):
        raise ValueError("Exact trusted bridge and schema fingerprints required")
    password = os.environ.get("SUMMER_BACKUP_PASSWORD", "").encode()
    if len(password) < 32 or len(password) > 1024 or b"\n" in password or b"\r" in password:
        raise ValueError("Random backup password of 32–1024 bytes required")
    ciphertext = args.directory / "backup.enc"
    manifest = args.directory / "manifest.json"
    expected = {"format": FORMAT, "domainKey": args.domain_key, "bridgeSha256": args.bridge_sha256,
                "schemaFingerprint": args.schema_fingerprint, "sourceFingerprint": args.source_fingerprint,
                "encryptionSaltBytes": 8}
    if args.operation == "create":
        if sys.stdin.isatty():
            raise ValueError("Pipe SQL dump into stdin")
        args.directory.mkdir(mode=0o700, parents=False, exist_ok=False)
        metadata = dict(expected, encryption="AES-256-CBC/PBKDF2-HMAC-SHA256",
                        authentication="HMAC-SHA256", iterations=ITERATIONS,
                        macSalt=secrets.token_hex(32),
                        createdAt=datetime.datetime.now(datetime.timezone.utc).isoformat())
        # Only ciphertext is ever written. CREATE_EXCL prevents accidental overwrite/reuse.
        with ciphertext.open("xb") as output:
            ciphertext.chmod(0o600)
            size, plaintext_hash = openssl(password, sys.stdin.buffer, output)
        metadata["plaintextBytes"] = size
        metadata["plaintextSha256"] = plaintext_hash
        metadata["ciphertextSha256"] = digest_file(ciphertext)
        metadata["ciphertextBytes"] = ciphertext.stat().st_size
        metadata["hmacSha256"] = mac(password, metadata, ciphertext)
        with manifest.open("xb") as output:
            manifest.chmod(0o600)
            output.write(canonical(metadata) + b"\n")
        print(json.dumps({"status": "ENCRYPTED", "manifestSha256": digest_file(manifest)}))
    else:
        if sys.stdout.isatty():
            raise ValueError("Pipe restore stdout into an isolated SQL client")
        if not args.expected_manifest_sha256 or not hmac.compare_digest(
                digest_file(manifest), args.expected_manifest_sha256):
            raise ValueError("Approved backup manifest fingerprint mismatch")
        metadata = json.loads(manifest.read_bytes())
        signature = metadata.pop("hmacSha256", "")
        if (any(metadata.get(key) != value for key, value in expected.items())
                or metadata.get("iterations") != ITERATIONS
                or metadata.get("encryption") != "AES-256-CBC/PBKDF2-HMAC-SHA256"
                or metadata.get("authentication") != "HMAC-SHA256"
                or not re.fullmatch(r"[0-9a-f]{64}", metadata.get("macSalt", ""))
                or metadata.get("ciphertextBytes") != ciphertext.stat().st_size
                or metadata.get("ciphertextSha256") != digest_file(ciphertext)
                or not isinstance(metadata.get("plaintextBytes"), int) or metadata["plaintextBytes"] <= 0
                or not re.fullmatch(r"[0-9a-f]{64}", metadata.get("plaintextSha256", ""))
                or not hmac.compare_digest(mac(password, metadata, ciphertext), signature)):
            raise ValueError("Backup authentication or source binding failed")
        # Authentication completes before a single plaintext byte is emitted.
        with ciphertext.open("rb") as source:
            openssl(password, source, sys.stdout.buffer, decrypt=True)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError, json.JSONDecodeError):
        print("Backup operation rejected; no credentials are included in diagnostics", file=sys.stderr)
        sys.exit(1)
