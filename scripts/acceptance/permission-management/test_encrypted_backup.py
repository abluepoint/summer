"""Real OpenSSL round trip and fail-closed authentication checks; no database required."""

import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import tempfile
import unittest


class EncryptedBackupTest(unittest.TestCase):
    def test_restore_authenticates_before_emitting_sql(self):
        with tempfile.TemporaryDirectory(prefix="summer-backup-test-") as temporary:
            directory = Path(temporary) / "encrypted"
            script = Path(__file__).with_name("encrypted_backup.py")
            env = dict(os.environ, SUMMER_BACKUP_PASSWORD=secrets.token_urlsafe(48))
            common = [str(directory), "--domain-key", "ENTERPRISE:backup-test",
                      "--bridge-sha256", "a" * 64, "--schema-fingerprint", "b" * 64,
                      "--source-fingerprint", "c" * 64]
            sql = b"CREATE TABLE example (id INT);\nINSERT INTO example VALUES (7);\n"
            encrypted = subprocess.run([sys.executable, str(script), "create", *common],
                                       input=sql, capture_output=True, env=env)
            self.assertEqual(0, encrypted.returncode, encrypted.stderr)
            manifest_hash = json.loads(encrypted.stdout)["manifestSha256"]
            restore = [sys.executable, str(script), "restore", *common,
                       "--expected-manifest-sha256", manifest_hash]
            restored = subprocess.run(restore, capture_output=True, env=env)
            self.assertEqual(0, restored.returncode, restored.stderr)
            self.assertEqual(sql, restored.stdout)
            wrong_source = list(restore)
            wrong_source[wrong_source.index("--source-fingerprint") + 1] = "d" * 64
            rejected_source = subprocess.run(wrong_source, capture_output=True, env=env)
            self.assertNotEqual(0, rejected_source.returncode)
            self.assertEqual(b"", rejected_source.stdout)
            wrong_password = subprocess.run(restore, capture_output=True,
                                           env=dict(env, SUMMER_BACKUP_PASSWORD=secrets.token_urlsafe(48)))
            self.assertNotEqual(0, wrong_password.returncode)
            self.assertEqual(b"", wrong_password.stdout)
            ciphertext = directory / "backup.enc"
            corrupted = bytearray(ciphertext.read_bytes())
            corrupted[-1] ^= 1
            ciphertext.write_bytes(corrupted)
            tampered = subprocess.run(restore, capture_output=True, env=env)
            self.assertNotEqual(0, tampered.returncode)
            self.assertEqual(b"", tampered.stdout)
            self.assertNotIn(sql, ciphertext.read_bytes())
            self.assertEqual(manifest_hash, hashlib.sha256((directory / "manifest.json").read_bytes()).hexdigest())

    def test_empty_dump_never_produces_a_successful_backup_manifest(self):
        with tempfile.TemporaryDirectory(prefix="summer-empty-backup-test-") as temporary:
            directory = Path(temporary) / "encrypted"
            result = subprocess.run([sys.executable, str(Path(__file__).with_name("encrypted_backup.py")),
                "create", str(directory), "--domain-key", "ENTERPRISE:empty-backup-test",
                "--bridge-sha256", "a" * 64, "--schema-fingerprint", "b" * 64,
                "--source-fingerprint", "c" * 64], input=b"", capture_output=True,
                env=dict(os.environ, SUMMER_BACKUP_PASSWORD=secrets.token_urlsafe(48)))
            self.assertNotEqual(0, result.returncode)
            self.assertFalse((directory / "manifest.json").exists())


if __name__ == "__main__":
    unittest.main()
