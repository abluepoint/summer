#!/usr/bin/env python3
"""Exercise current permission management through real HTTP, without provisioning services."""

import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import threading
import urllib.error
import urllib.parse
import urllib.request
import uuid


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        return None


class Regression:
    def __init__(self, origin, secondary, domain, token):
        self.origin = origin
        self.secondary = secondary
        self.domain = domain
        self.token = token
        self.prefix = "acceptance-" + uuid.uuid4().hex
        self.permissions = set()
        self.groups = set()
        self.checks = []
        self.cleanup_failures = 0
        self.resource_lock = threading.Lock()

    def request(self, path, method="GET", body=None, version=None, key=None,
                origin=None, authenticate=True):
        headers = {"Accept": "application/json"}
        if authenticate and self.token:
            headers["Authorization"] = "Bearer " + self.token
        if version is not None:
            headers["If-Match"] = '"' + str(version) + '"'
        if method != "GET":
            headers["Idempotency-Key"] = key or uuid.uuid4().hex
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode()
        request = urllib.request.Request(
            (origin or self.origin) + self.domain + path,
            data=data, headers=headers, method=method)
        # Do not expose authorization headers, URLs, or raw server failures in reports.
        try:
            response = urllib.request.build_opener(NoRedirect()).open(request, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read()
            try:
                payload = json.loads(raw) if raw else None
            except (json.JSONDecodeError, UnicodeDecodeError):
                payload = None
            return response.status, payload.get("data", payload) if isinstance(payload, dict) else payload

    def check(self, name, condition):
        self.checks.append({"name": name, "passed": bool(condition)})
        if not condition:
            raise AssertionError(name)

    def expect(self, name, path, expected=200, **kwargs):
        status, body = self.request(path, **kwargs)
        self.remember(path, kwargs.get("method", "GET"), status, body)
        self.check(name, status == expected)
        return body

    def remember(self, path, method, status, body):
        # Retain successful POST identities even when a status regression fails its assertion.
        if method == "POST" and 200 <= status < 300 and isinstance(body, dict):
            with self.resource_lock:
                if path == "/permissions" and isinstance(body.get("permissionId"), str):
                    self.permissions.add(body["permissionId"])
                if path == "/permission-groups" and isinstance(body.get("groupId"), str):
                    self.groups.add(body["groupId"])

    def concurrent_create(self, body, key):
        status, item = self.request("/permissions", "POST", body, key=key)
        self.remember("/permissions", "POST", status, item)
        return status, item

    def group(self, name, **fields):
        return self.expect("create_group_" + name, "/permission-groups", 201,
                           method="POST", body={"name": self.prefix + name, **fields})

    def permission(self, name, group, **fields):
        return self.expect("create_permission_" + name, "/permissions", 201,
                           method="POST", body={"name": self.prefix + name,
                           "code": self.prefix + "." + name, "type": "MENU",
                           "groupId": group["groupId"], **fields})

    def run(self):
        self.expect("unauthenticated_request_is_401", "/permissions", 401, authenticate=False)
        root = self.group("root")
        child = self.group("child", parentGroupId=root["groupId"], sortOrder=10)
        leaf = self.group("leaf", parentGroupId=child["groupId"], sortOrder=20)
        current = self.expect("read_group", "/permission-groups/" + leaf["groupId"])
        self.check("tree_parent_and_sort", current["parentGroupId"] == child["groupId"]
                   and current["sortOrder"] == 20)
        self.expect("cycle_is_409", "/permission-groups/" + root["groupId"], 409,
                    method="PATCH", body={"parentGroupId": leaf["groupId"]}, version=1)
        self.expect("nonempty_group_is_409", "/permission-groups/" + root["groupId"], 409,
                    method="DELETE", version=1)

        permission = self.permission("interface", child, type="INTERFACE", description="original")
        path = "/permissions/" + permission["permissionId"]
        self.check("default_authorized_and_stable_identity", permission["accessMode"] == "AUTHORIZED"
                   and permission["version"] == 1 and bool(permission["permissionId"]))
        self.expect("missing_condition_is_428", path, 428, method="PATCH", body={"name": "ignored"})
        self.expect("stale_condition_is_412", path, 412, method="PATCH", body={"name": "ignored"}, version=0)
        self.expect("immutable_type_is_400", path, 400, method="PATCH", body={"type": "MENU"}, version=1)
        self.expect("immutable_code_is_400", path, 400, method="PATCH", body={"code": "changed"}, version=1)
        self.expect("unknown_field_is_400", path, 400, method="PATCH", body={"unknown": True}, version=1)
        moved = self.expect("move_and_clear_description", path, method="PATCH",
                            body={"groupId": leaf["groupId"], "description": None}, version=1)
        self.check("null_clears_and_omitted_name_stays", moved["description"] is None
                   and moved["name"] == permission["name"] and moved["version"] == 2)
        self.expect("disable_with_condition", path, method="PATCH", body={"status": "DISABLED"}, version=2)
        self.expect("incomplete_active_data_is_400", "/permissions", 400, method="POST",
                    body={"name": "invalid", "code": self.prefix + ".invalid", "type": "DATA",
                          "groupId": root["groupId"]})
        data = self.permission("data", root, type="DATA", status="DISABLED")
        self.expect("incomplete_data_activation_is_400", "/permissions/" + data["permissionId"], 400,
                    method="PATCH", body={"status": "ACTIVE"}, version=1)
        self.expect("invalid_page_is_400", "/permissions?page=0", 400)
        self.expect("unbounded_page_is_400", "/permissions?size=201", 400)
        query = "/permissions?" + urllib.parse.urlencode({"type": "INTERFACE", "groupId": root["groupId"],
                "includeDescendants": "true", "name": self.prefix})
        first = self.expect("descendant_page", query)
        second = self.expect("stable_page", query)
        self.check("descendant_filter_and_stable_page", first == second and first["total"] == 1
                   and first["items"][0]["permissionId"] == permission["permissionId"])

        key = uuid.uuid4().hex
        body = {"name": self.prefix + "receipt", "code": self.prefix + ".receipt", "type": "MENU",
                "groupId": root["groupId"], "accessMode": "PUBLIC"}
        original = self.expect("create_with_receipt", "/permissions", 201, method="POST", body=body, key=key)
        replay = self.expect("canonical_receipt_replay", "/permissions", 201, method="POST",
                             body=dict(reversed(list(body.items()))), key=key)
        self.check("same_request_returns_original", replay == original)
        self.expect("different_request_same_key_is_409", "/permissions", 409, method="POST",
                    body={**body, "name": "different"}, key=key)
        self.expect("change_current_after_receipt", "/permissions/" + original["permissionId"],
                    method="PATCH", body={"name": "later"}, version=1)
        replay = self.expect("second_node_receipt_replay", "/permissions", 201, method="POST",
                             body=body, key=key, origin=self.secondary)
        self.check("second_node_returns_original_not_current", replay == original and replay["version"] == 1)

        key = uuid.uuid4().hex
        body = {"name": self.prefix + "concurrent", "code": self.prefix + ".concurrent", "type": "MENU",
                "groupId": root["groupId"], "accessMode": "AUTHENTICATED"}
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            futures = [pool.submit(self.concurrent_create, body, key) for _ in range(2)]
            responses = [future.result() for future in futures]
        self.check("concurrent_same_key_single_identity", all(status == 201 for status, _ in responses)
                   and responses[0][1] == responses[1][1]
                   and responses[0][1]["accessMode"] == "AUTHENTICATED")

        left, right = self.group("left"), self.group("right")
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            futures = [pool.submit(self.request, "/permission-groups/" + a["groupId"], "PATCH",
                                   {"parentGroupId": b["groupId"]}, version=1)
                       for a, b in [(left, right), (right, left)]]
            statuses = sorted(future.result()[0] for future in futures)
        self.check("concurrent_cycle_has_one_winner", statuses == [200, 409])
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            futures = [pool.submit(self.request, path, "PATCH", {"groupId": group["groupId"]}, version=3)
                       for group in [left, right]]
            statuses = sorted(future.result()[0] for future in futures)
        self.check("concurrent_move_same_version_has_one_winner", statuses == [200, 412])

        disposable = self.permission("reserved", root)
        disposable_path = "/permissions/" + disposable["permissionId"]
        self.expect("delete_missing_condition_is_428", disposable_path, 428, method="DELETE")
        self.expect("delete_stale_condition_is_412", disposable_path, 412, method="DELETE", version=0)
        self.expect("delete_permission", disposable_path, method="DELETE", version=1)
        self.permissions.discard(disposable["permissionId"])
        self.expect("deleted_code_cannot_be_reused", "/permissions", 409, method="POST",
                    body={"name": "new identity", "code": disposable["code"], "type": "MENU",
                          "groupId": root["groupId"]})

    def cleanup(self):
        # Only IDs returned by this run are touched. Never enumerate/delete a whole domain.
        for permission_id in list(self.permissions):
            try:
                path = "/permissions/" + permission_id
                status, item = self.request(path)
                if status == 404 or (status == 200 and self.request(path, "DELETE", version=item["version"])[0] == 200):
                    self.permissions.discard(permission_id)
            except Exception:
                pass
        # Parent order may have changed in the graph race. Retry only the owned IDs.
        for _ in range(len(self.groups) + 1):
            for group_id in list(self.groups):
                try:
                    path = "/permission-groups/" + group_id
                    status, item = self.request(path)
                    if status == 404 or (status == 200 and self.request(path, "DELETE", version=item["version"])[0] == 200):
                        self.groups.discard(group_id)
                except Exception:
                    pass
            if not self.groups:
                break
        self.cleanup_failures = len(self.permissions) + len(self.groups)


def origin(value):
    parsed = urllib.parse.urlsplit(value)
    if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password \
            or parsed.path not in ("", "/") or parsed.query or parsed.fragment:
        raise argparse.ArgumentTypeError("Use an HTTP(S) origin without credentials, path, or query")
    return value.rstrip("/")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--origin", required=True, type=origin)
    parser.add_argument("--secondary-origin", required=True, type=origin)
    parser.add_argument("--owned-domain", required=True,
                        help="Explicit disposable domain API prefix; the runner does not create it")
    parser.add_argument("--report", required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"/api/v1/(platform|enterprises/[A-Za-z0-9_-]{1,64})", args.owned_domain):
        parser.error("Invalid owned domain API prefix")
    if args.origin == args.secondary_origin:
        parser.error("Two distinct application origins sharing the same owned database are required")
    token = os.environ.get("SUMMER_PERMISSION_ACCEPTANCE_TOKEN")
    if not token:
        parser.error("SUMMER_PERMISSION_ACCEPTANCE_TOKEN must be supplied in the process environment")
    run = Regression(args.origin, args.secondary_origin, args.owned_domain, token)
    runner_sha = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    failure_type = None
    try:
        run.run()
    except Exception as error:
        failure_type = type(error).__name__
    finally:
        run.cleanup()
    failed = sum(not check["passed"] for check in run.checks)
    errors = int(failure_type is not None and failed == 0)
    report = {"tests": len(run.checks), "passed": len(run.checks) - failed,
              "failures": failed, "errors": errors, "skipped": 0, "checks": run.checks,
              "failureType": failure_type, "runnerSha256": runner_sha,
              "realHttp": True, "networkMocking": False,
              "twoDistinctNodes": True, "cleanup": {"ownedCurrentRecordsRemaining": run.cleanup_failures,
              "permanentIdentityReceiptsAudit": "retained; dispose the explicitly owned test database externally"}}
    report["status"] = "PASSED" if not (failed or errors or run.cleanup_failures) else "FAILED"
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({key: report[key] for key in ("status", "tests", "passed", "failures", "errors", "skipped")}))
    return 0 if report["status"] == "PASSED" else 1


if __name__ == "__main__":
    sys.exit(main())
