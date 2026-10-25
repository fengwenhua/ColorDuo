#!/usr/bin/env python3
"""Exercise the release verifier with the public debug fixture, never production keys."""
import argparse
import hashlib
import importlib.util
import re
import tempfile
import zipfile
from pathlib import Path

spec = importlib.util.spec_from_file_location("sign_release", Path(__file__).with_name("sign-release.py"))
sign = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sign)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--build-tools", type=Path, required=True)
    args = parser.parse_args()
    tool = args.build_tools / "apksigner"
    original = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    with tempfile.TemporaryDirectory(prefix="colorduo-public-signing-test-") as raw:
        directory = Path(raw)
        for v2, v3 in [(True, True), (True, False), (False, True)]:
            apk = directory / f"v2-{v2}-v3-{v3}.apk"
            sign.checked([str(tool), "sign", "--ks", ".tools/debug.keystore", "--ks-key-alias", "hyperduo",
                          "--ks-pass", "pass:android", "--key-pass", "pass:android",
                          "--v1-signing-enabled", "false", "--v2-signing-enabled", str(v2).lower(),
                          "--v3-signing-enabled", str(v3).lower(), "--v4-signing-enabled", "false",
                          "--out", str(apk), str(args.apk)])
            report = sign.checked([str(tool), "verify", "--verbose", "--print-certs", str(apk)])
            cert = re.search(r"Signer #1 certificate SHA-256 digest: ([a-f0-9]{64})", report).group(1)
            try:
                sign.verify_apk(tool, apk, cert)
            except RuntimeError:
                if v2 and v3: raise
            else:
                if not (v2 and v3): raise AssertionError("Missing signature scheme accepted")
            if v2 and v3:
                try:
                    sign.verify_apk(tool, apk, "0" * 64)
                except RuntimeError:
                    pass
                else:
                    raise AssertionError("Unpinned certificate accepted")
                # Changing a signed ZIP entry must fail cryptographic verification.
                with zipfile.ZipFile(apk, "a") as archive:
                    archive.writestr("colorduo-tamper-test", b"changed")
                try:
                    sign.verify_apk(tool, apk, cert)
                except RuntimeError:
                    pass
                else:
                    raise AssertionError("Modified signed APK accepted")
    assert hashlib.sha256(args.apk.read_bytes()).hexdigest() == original
    print("Release signing integration passed: v2+v3 accepted; missing schemes, wrong certificate and tampering rejected")


if __name__ == "__main__": main()
