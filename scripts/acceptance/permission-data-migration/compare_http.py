#!/usr/bin/env python3
"""Generate a source-bound receipt from actual authenticated old/new HTTP requests.

Call generate() with transports holding tokens in memory. Saved observations omit credentials,
headers, response bodies and query strings. Bootstrap/backup/native UI remain runner concerns.
"""
import hashlib
import json
from pathlib import Path


class ComparisonFailure(RuntimeError):
    pass


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def unwrap(body):
    if isinstance(body, dict) and "success" in body and body["success"] is not True:
        raise ComparisonFailure("Failed or malformed business response envelope")
    return body.get("data", body) if isinstance(body, dict) else body


def at(value, path):
    for part in path.split(".") if path else []:
        value = value[part]
    return value


def outcome(status):
    if 200 <= status <= 299:
        return True
    if status in (403, 404):
        return False
    raise ComparisonFailure("Inconclusive HTTP status " + str(status))


def observe(transport, scenario, observations, side):
    allowed = []
    statuses = []
    per_probe = []
    for probe in scenario["probes"]:
        status, body, _ = transport(probe["path"], probe.get("method", "GET"),
                                    probe.get("body"), probe.get("headers", {}))
        entry = dict(side=side, subjectKind=scenario["subjectKind"], subject=scenario["subject"],
                     resource=scenario["resource"], action=scenario["action"],
                     operation=scenario["operation"], method=probe.get("method", "GET"),
                     path=probe["path"].split("?", 1)[0], status=status)
        observations.append(entry)  # Preserve failure status before validation raises.
        success = outcome(status)
        statuses.append(status)
        ids = []
        if success:
            data = unwrap(body)
            if "itemsPath" in probe:
                rows = at(data, probe["itemsPath"])
                if not isinstance(rows, list):
                    raise ComparisonFailure("Actual list response required")
                ids = [str(at(row, probe["idPath"])) for row in rows]
                if "totalPath" in probe:
                    total = at(data, probe["totalPath"])
                    if not isinstance(total, int) or isinstance(total, bool) or total < 0 or total != len(ids):
                        raise ComparisonFailure("Actual total differs from complete allowed ID set")
                    entry["reportedTotal"] = total
                else:
                    entry["fullListCount"] = len(ids)
            else:
                # Logical identities are the same business key in the restored copy;
                # create/update success must also return that exact key in its payload.
                actual_id = str(at(data, probe["idPath"]))
                if actual_id != probe["identity"]:
                    raise ComparisonFailure("Actual business response identity differs from probe")
                ids = [actual_id]
        count_result = None
        if "countProbe" in probe:
            count_probe = probe["countProbe"]
            count_status, count_body, _ = transport(count_probe["path"], "GET", None, {})
            entry["countStatus"] = count_status
            count_success = outcome(count_status)
            count = at(unwrap(count_body), count_probe["countPath"]) if count_success else None
            entry["reportedSeparateTotal"] = count
            if count_success and (not isinstance(count, int) or isinstance(count, bool) or count < 0):
                raise ComparisonFailure("Actual count must be a nonnegative integer")
            if count_success != success:
                raise ComparisonFailure("Actual count/list probe outcomes differ; complete total cannot be proved")
            if count_success and count != len(ids):
                raise ComparisonFailure("Actual separate count differs from complete allowed ID set")
            count_result = (count_success, count)
        entry["allowedIds"] = sorted(ids)
        allowed.extend(ids)
        per_probe.append((success, sorted(ids), count_result))
    if not statuses:
        raise ComparisonFailure("At least one actual probe required for every matrix cell")
    successful = [status for status in statuses if 200 <= status <= 299]
    # Summary is an observed status. All per-probe statuses remain in observations.
    return sorted(allowed), (successful or statuses)[0], per_probe


def validate_scenarios(scenarios):
    import urllib.parse
    allowed_headers = {"if-match", "idempotency-key", "accept", "content-type"}
    credential_fields = {"authorization", "cookie", "set-cookie", "password", "secret", "client_secret",
                         "access_token", "id_token", "refresh_token", "token", "code_verifier", "assertion"}
    def fields(value):
        if isinstance(value, dict):
            for key, nested in value.items():
                if key.lower().replace("-", "_") in {name.replace("-", "_") for name in credential_fields}:
                    raise ComparisonFailure("Scenario credential field is forbidden")
                fields(nested)
        elif isinstance(value, list):
            for nested in value:
                fields(nested)
    def probe(value):
        path = value.get("path", "")
        parts = urllib.parse.urlsplit(path)
        if not path.startswith("/api/") or parts.scheme or parts.netloc:
            raise ComparisonFailure("Only relative business API probes are allowed")
        if any(key.lower() in credential_fields | {"code", "state"} for key in urllib.parse.parse_qs(parts.query)):
            raise ComparisonFailure("Scenario credential query is forbidden")
        if any(key.lower() not in allowed_headers for key in value.get("headers", {})):
            raise ComparisonFailure("Scenario header is not on the noncredential allowlist")
        fields(value.get("body"))
        if "countProbe" in value:
            probe(value["countProbe"])
    for scenario in scenarios:
        for value in scenario["probes"]:
            probe(value)


def generate(plan_report, scenarios, transports, runtime_inputs, output_directory):
    """transports[(side, subject)] performs real HTTP and returns status/body/headers."""
    validate_scenarios(scenarios)
    output_directory = Path(output_directory)
    output_directory.mkdir(parents=True, exist_ok=True)
    plan = plan_report["plan"]
    required = set()
    for item in plan["items"]:
        if item["status"] == "PROVED":
            for operation in (["list", "total", "detail"] if item["action"] == "read" else ["write"]):
                required.add((item["subjectKind"], item["subject"], item["resource"], item["action"], operation))
    actual = [(s["subjectKind"], s["subject"], s["resource"], s["action"], s["operation"]) for s in scenarios]
    if len(actual) != len(set(actual)) or set(actual) != required:
        raise ComparisonFailure("Scenario matrix must exactly cover all proved cells")
    observations, comparisons = [], []
    try:
        for scenario in scenarios:
            before, before_status, old_probes = observe(transports[("old", scenario["subject"])], scenario, observations, "old")
            after, after_status, new_probes = observe(transports[("new", scenario["subject"])], scenario, observations, "new")
            if old_probes != new_probes:
                raise ComparisonFailure("Actual old/new probe outcome or allowed ID set changed")
            if before != after or outcome(before_status) != outcome(after_status):
                raise ComparisonFailure("Actual old/new allowed set or HTTP outcome changed for " + canonical(actual[len(comparisons)]))
            comparisons.append(dict(subjectKind=scenario["subjectKind"], subject=scenario["subject"],
                                    resource=scenario["resource"], action=scenario["action"], operation=scenario["operation"],
                                    oldAllowedIds=before, newAllowedIds=after,
                                    oldStatus=before_status, newStatus=after_status))
    finally:
        (output_directory / "http-observations.json").write_text(json.dumps(observations, ensure_ascii=False, indent=2))
    inputs = dict(runtime=runtime_inputs, observations=observations, scenarios=scenarios)
    # Scenarios carry no tokens. Actual source/class hashes and real responses bind the receipt.
    encoded = canonical(inputs)
    (output_directory / "http-runtime-inputs.json").write_text(json.dumps(inputs, ensure_ascii=False, indent=2))
    receipt = dict(format="summer-data-http-comparison/1", sourceFingerprint=plan_report["sourceFingerprint"],
                   planHash=plan_report["planHash"], runtimeInputsFingerprint=hashlib.sha256(encoded.encode()).hexdigest(),
                   comparisons=comparisons)
    (output_directory / "http-comparison-receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2))
    return receipt
