#!/usr/bin/env python3
"""Attest a local old JVM from its actual PID, executable JAR and listening socket.

Capture/source fingerprints are explicitly supplied by the trusted local operator; this
tool does not claim to discover JDBC identity or prove authorization equivalence.
Credentials/configuration must be in the JVM environment, never command-line arguments.
"""

import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import zipfile


def sha256(path):
    result = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def process(pid, field):
    value = subprocess.run(["ps", "-p", str(pid), "-o", field + "="],
                           capture_output=True, text=True, check=True).stdout.strip()
    if not value:
        raise ValueError("Actual process unavailable")
    return value


def attest(pid, port, expected_bridge, source_fingerprint, schema_fingerprint, allowlist):
    started = process(pid, "lstart")
    command = process(pid, "command")
    arguments = shlex.split(command)
    if "-jar" not in arguments:
        raise ValueError("Pinned executable JAR invocation required")
    index = arguments.index("-jar")
    if index < 1 or len(arguments) != index + 2:
        raise ValueError("JVM configuration must use the environment")
    for option in arguments[1:index]:
        if not re.fullmatch(r"(?:-Xm[xs]\d+[mMgG]|-XX:MaxMetaspaceSize=\d+[mMgG]|-Dfile.encoding=UTF-8)", option):
            raise ValueError("Unsupported JVM argument in attested bridge")
    executable = Path(arguments[0]).resolve(strict=True)
    artifact = Path(arguments[index + 1]).resolve(strict=True)
    if not artifact.is_file() or not executable.is_file():
        raise ValueError("Actual artifact/executable unavailable")
    actual = sha256(artifact)
    trusted = json.loads(allowlist.read_bytes())
    entry = next((item for item in trusted.get("bridges", [])
                  if item.get("artifactSha256") == actual), None)
    if (trusted.get("format") != "summer-trusted-upgrade-bridges/1"
            or entry is None or actual != expected_bridge):
        raise ValueError("Actual running artifact is outside the trusted allowlist")
    listeners = subprocess.run(["lsof", "-nP", "-a", "-p", str(pid), "-t",
                                "-iTCP:" + str(port), "-sTCP:LISTEN"],
                               capture_output=True, text=True, check=True)
    if set(listeners.stdout.split()) != {str(pid)}:
        raise ValueError("Actual process does not own the expected listening socket")
    classes = {}
    with zipfile.ZipFile(artifact) as jar:
        for name in sorted(jar.namelist()):
            if name.startswith("BOOT-INF/classes/") and name.endswith(".class"):
                classes[name.removeprefix("BOOT-INF/classes/")] = hashlib.sha256(jar.read(name)).hexdigest()
        for name, expected in entry["migrations"].items():
            if hashlib.sha256(jar.read("BOOT-INF/classes/db/migration/mysql/" + name)).hexdigest() != expected:
                raise ValueError("Actual bridge schema resource changed")
    if (process(pid, "lstart") != started or process(pid, "command") != command
            or sha256(artifact) != actual):
        raise ValueError("Actual process/artifact changed during attestation")
    return {"format": "summer-actual-bridge-execution/1", "pid": pid,
            "processStarted": started, "listeningPort": port, "actualArtifact": str(artifact),
            "artifactSha256": actual, "javaExecutableSha256": sha256(executable),
            "build": entry["build"], "sourceRecipe": entry["recipe"],
            "allowlistSha256": sha256(allowlist), "installedClassHashes": classes,
            "operatorSourceFingerprint": source_fingerprint,
            "operatorSchemaFingerprint": schema_fingerprint,
            "sourceBinding": "TRUSTED_LOCAL_OPERATOR_CAPTURE_BINDING",
            "createdAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "cutoverAuthorized": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pid", type=int, required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--expected-bridge-sha256", required=True)
    parser.add_argument("--source-fingerprint", required=True)
    parser.add_argument("--schema-fingerprint", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--allowlist", type=Path, default=Path(__file__).resolve().parents[3] /
                        "summer-sample/src/main/resources/authorization/trusted-upgrade-bridges.json")
    args = parser.parse_args()
    if args.pid <= 0 or not 1 <= args.port <= 65535 or any(
            not re.fullmatch("[0-9a-f]{64}", value)
            for value in (args.expected_bridge_sha256, args.source_fingerprint, args.schema_fingerprint)):
        raise ValueError("Exact process, socket and source fingerprints required")
    result = attest(args.pid, args.port, args.expected_bridge_sha256,
                    args.source_fingerprint, args.schema_fingerprint, args.allowlist)
    descriptor = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as output:
        json.dump(result, output, sort_keys=True, indent=2)
        output.write("\n")
    print(json.dumps({"status": "ATTESTED", "artifactSha256": result["artifactSha256"],
                      "receiptSha256": sha256(args.output), "cutoverAuthorized": False}))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError, KeyError, zipfile.BadZipFile):
        print("Actual bridge attestation rejected; process arguments and credentials are suppressed", file=sys.stderr)
        sys.exit(1)
