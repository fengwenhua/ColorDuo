"""Verify fail-closed parsing and the API-29 v2-skipping behavior."""
import importlib.util
import unittest
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("sign_release", Path(__file__).resolve().parents[1] / "scripts/sign-release.py")
sign = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sign)
CERT = "a" * 64


def output(v2=True, v3=True, cert=CERT, signers=1):
    return (f"Verifies\nVerified using v2 scheme (APK Signature Scheme v2): {str(v2).lower()}\n"
            f"Verified using v3 scheme (APK Signature Scheme v3): {str(v3).lower()}\n"
            f"Number of signers: {signers}\nSigner #1 certificate SHA-256 digest: {cert}\n")


class SigningVerificationTest(unittest.TestCase):
    def verify(self, first, second):
        with patch.object(sign, "checked", side_effect=[first, second]) as checked:
            sign.verify_apk(Path("apksigner"), Path("release.apk"), CERT)
            self.assertEqual(2, checked.call_count)
            self.assertNotIn("--min-sdk-version", checked.call_args_list[0].args[0])
            self.assertEqual(["--min-sdk-version", "24"], checked.call_args_list[1].args[0][-3:-1])

    def test_v2_skipped_by_default_is_verified_explicitly(self):
        self.verify(output(v2=False), output())

    def test_both_verified_by_default_are_also_accepted(self):
        self.verify(output(), output())

    def test_missing_scheme_is_rejected(self):
        for first, second in [(output(), output(v2=False)), (output(), output(v3=False)),
                              (output(v3=False), output())]:
            with self.subTest(first=first, second=second), self.assertRaisesRegex(RuntimeError, "signature scheme"):
                self.verify(first, second)

    def test_wrong_missing_or_multiple_signers_are_rejected(self):
        for invalid in [output(cert="b" * 64), output(cert=""), output(signers=2),
                        output() + "Signer #2 certificate SHA-256 digest: " + CERT + "\n"]:
            for first, second in [(invalid, output()), (output(), invalid)]:
                with self.subTest(invalid=invalid), self.assertRaisesRegex(RuntimeError, "pinned production certificate"):
                    self.verify(first, second)


if __name__ == "__main__": unittest.main()
