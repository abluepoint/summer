#!/usr/bin/env python3
"""Read-only authenticated export before restarting the old process (bundles are in memory).

Token is read from SUMMER_MIGRATION_EXPORT_TOKEN and never persisted or printed.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def export(origin, enterprise, token):
    base = "/api/v1/enterprises/" + urllib.parse.quote(enterprise, safe="")

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args):
            return None

    opener = urllib.request.build_opener(NoRedirect())

    def read(path):
        request = urllib.request.Request(origin.rstrip("/") + path,
                                         headers={"Authorization": "Bearer " + token})
        try:
            with opener.open(request, timeout=20) as response:
                body = json.load(response)
        except urllib.error.HTTPError as error:
            raise RuntimeError("Legacy export HTTP " + str(error.code)) from None
        return body.get("data", body)

    catalogs = [read(base + "/capabilities/catalog"),
                read("/api/v1/platform/capabilities/catalog")]
    capabilities = {}
    for catalog in catalogs:
        for item in catalog["capabilities"]:
            capabilities[item["capabilityId"]] = dict(
                id=item["capabilityId"], code=item["code"], resource=item["resource"],
                active=item["status"] == "ACTIVE")
    current = read(base + "/capability-bundles")
    histories = {}
    bundles = []
    for bundle in current:
        bundle_id = bundle["bundleId"]
        history = read(base + "/capability-bundles/" + urllib.parse.quote(bundle_id, safe="") + "/versions")
        history_json = canonical(history)
        histories[bundle_id] = history_json
        fingerprint = hashlib.sha256(history_json.encode()).hexdigest()
        for version in history:
            if version["bundleId"] != bundle_id:
                raise RuntimeError("Bundle history identity conflict")
            bundles.append(dict(id=bundle_id, effectiveVersion=version["version"],
                                capabilityIds=version["capabilityIds"], historyFingerprint=fingerprint))
    evidence = dict(enterpriseId=enterprise, catalogs=catalogs, bundles=current,
                    bundleHistories=histories, source="authenticated-old-http")
    return dict(capabilities=list(capabilities.values()), bundles=bundles,
                evidenceJson=canonical(evidence))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--origin", required=True)
    parser.add_argument("--enterprise", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    parts = urllib.parse.urlsplit(args.origin)
    if parts.scheme != "https" and not (parts.scheme == "http" and parts.hostname in {"127.0.0.1", "localhost"}):
        parser.error("HTTPS or local loopback origin required")
    result = export(args.origin, args.enterprise, os.environ["SUMMER_MIGRATION_EXPORT_TOKEN"])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    descriptor = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        json.dump(result, output, ensure_ascii=False, indent=2)
    print("Legacy export saved; token omitted")
