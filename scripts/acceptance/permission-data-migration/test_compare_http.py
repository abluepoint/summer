import tempfile
import unittest
from pathlib import Path
from compare_http import generate, ComparisonFailure

ROOT = Path(__file__).resolve().parents[3]
ARTIFACTS = ROOT / ".scratch/permission-management/execution/13/python-test-artifacts"
ARTIFACTS.mkdir(parents=True, exist_ok=True)

class CompareHttpTest(unittest.TestCase):
    def report(self):
        return dict(sourceFingerprint="f" * 64, planHash="a" * 64,
                    plan=dict(items=[dict(subjectKind="USER", subject="alice", resource="dictionaries",
                                          action="read", status="PROVED")]))

    def scenarios(self):
        return [dict(subjectKind="USER", subject="alice", resource="dictionaries", action="read",
                     operation=operation, probes=[dict(path="/api/first", itemsPath="", idPath="id"),
                                                   dict(path="/api/second", itemsPath="", idPath="id")])
                for operation in ("list", "total", "detail")]

    def test_individual_outcome_change_cannot_hide_in_successful_empty_summary(self):
        old = lambda path, *args: (403 if path == "/api/first" else 200, [], {})
        new = lambda path, *args: (200, [], {})
        with tempfile.TemporaryDirectory(dir=ARTIFACTS) as output:
            with self.assertRaisesRegex(ComparisonFailure, "probe"):
                generate(self.report(), self.scenarios(), {("old", "alice"): old, ("new", "alice"): new}, {}, output)

    def test_credentials_are_rejected_before_transport_or_artifact_serialization(self):
        for header in ("Authorization", "Cookie", "Set-Cookie", "Proxy-Authorization", "X-Api-Key"):
            scenarios = self.scenarios()
            scenarios[0]["probes"][0]["headers"] = {header: "fixture-placeholder"}
            calls = []
            transport = lambda *args: (calls.append(args) or (200, [], {}))
            with tempfile.TemporaryDirectory(dir=ARTIFACTS) as output:
                with self.assertRaisesRegex(ComparisonFailure, "credential|header"):
                    generate(self.report(), scenarios, {("old", "alice"): transport, ("new", "alice"): transport}, {}, output)
                self.assertEqual(calls, [])

    def test_count_endpoint_is_still_observed_when_list_is_denied(self):
        scenarios = self.scenarios()
        for scenario in scenarios:
            for probe in scenario["probes"]:
                probe["countProbe"] = dict(path="/api/count", countPath="totalElements")
        calls = []
        def old(path, *args):
            calls.append(("old", path))
            return (403, None, {})
        def new(path, *args):
            calls.append(("new", path))
            return (200, {"totalElements": 7}, {}) if path == "/api/count" else (403, None, {})
        with tempfile.TemporaryDirectory(dir=ARTIFACTS) as output:
            with self.assertRaisesRegex(ComparisonFailure, "count"):
                generate(self.report(), scenarios, {("old", "alice"): old, ("new", "alice"): new}, {}, output)
        self.assertIn(("old", "/api/count"), calls)
        self.assertIn(("new", "/api/count"), calls)

    def test_failed_business_envelope_is_inconclusive_even_with_200_and_empty_ids(self):
        failed = lambda *args: (200, {"success": False, "data": [], "error": {"code": "SUMMER_FORBIDDEN"}}, {})
        with tempfile.TemporaryDirectory(dir=ARTIFACTS) as output:
            with self.assertRaisesRegex(ComparisonFailure, "envelope"):
                generate(self.report(), self.scenarios(), {("old", "alice"): failed, ("new", "alice"): failed}, {}, output)

    def test_list_total_boolean_cannot_stand_in_for_a_numeric_count(self):
        scenarios = self.scenarios()
        for scenario in scenarios:
            scenario["probes"] = [dict(path="/api/first", itemsPath="items", idPath="id", totalPath="total")]
        malformed = lambda *args: (200, {"items": [{"id": "row"}], "total": True}, {})
        with tempfile.TemporaryDirectory(dir=ARTIFACTS) as output:
            with self.assertRaisesRegex(ComparisonFailure, "total"):
                generate(self.report(), scenarios, {("old", "alice"): malformed, ("new", "alice"): malformed}, {}, output)

    def test_controlled_403_to_404_remains_valid_per_probe(self):
        old = lambda *args: (403, None, {})
        new = lambda *args: (404, None, {})
        with tempfile.TemporaryDirectory(dir=ARTIFACTS) as output:
            receipt = generate(self.report(), self.scenarios(), {("old", "alice"): old, ("new", "alice"): new}, {}, output)
            self.assertEqual(len(receipt["comparisons"]), 3)

if __name__ == "__main__":
    unittest.main()
