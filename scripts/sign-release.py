#!/usr/bin/env python3
"""Sign an unsigned APK in the protected Actions job, then sign its exact manifest bytes."""
import argparse
import base64
import hashlib
import json
import os
import re
import subprocess
import tempfile
from pathlib import Path


def stamp():
    return dict(line.split("=", 1) for line in Path("version.properties").read_text().splitlines() if "=" in line)


def checked(command, **kwargs):
    result = subprocess.run(command, capture_output=True, check=False, **kwargs)
    if result.returncode:
        raise RuntimeError("Signing/verification failed: " + Path(command[0]).name)
    return result.stdout.decode()


def verify_apk(tool, target, expected_cert):
    """Check the real device range, then require both v2 and v3 to verify."""
    for options, schemes in [((), (3,)), (("--min-sdk-version", "24"), (2, 3))]:
        # minSdk 29 skips v2 when v3 exists. Extending verification down to API 24
        # exercises both signatures; this does not change the APK's minSdk.
        signed = checked([str(tool), "verify", "--verbose", "--print-certs", *options, str(target)])
        for scheme in schemes:
            if f"Verified using v{scheme} scheme (APK Signature Scheme v{scheme}): true" not in signed.splitlines():
                raise RuntimeError(f"Release APK did not verify with signature scheme v{scheme}")
        certificates = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$", signed, re.MULTILINE)
        if "Number of signers: 1" not in signed.splitlines() or certificates != [expected_cert]:
            raise RuntimeError("Release APK signer does not match the pinned production certificate")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--out", type=Path, default=Path("dist"))
    args = parser.parse_args()
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("GITHUB_REPOSITORY") != "fengwenhua/ColorDuo":
        raise SystemExit("Formal signing is restricted to this repository's protected Actions job")
    version = stamp()
    tag = "v" + version["versionName"]
    commit = checked(["git", "rev-parse", "HEAD"]).strip()
    if checked(["git", "rev-parse", tag + "^{}"]).strip() != commit:
        raise SystemExit("Signing checkout must equal the release tag")
    expected_cert = Path("release-trust.properties").read_text().strip().split("=", 1)[1]
    name = "ColorDuo-" + version["versionName"] + ".apk"
    args.out.mkdir(parents=True, exist_ok=True)
    target = args.out / name
    tool = args.build_tools / "apksigner"
    aapt = args.build_tools / "aapt2"
    with tempfile.TemporaryDirectory(prefix="colorduo-sign-", dir=os.environ["RUNNER_TEMP"]) as raw:
        private = Path(raw)
        key = private / "release.p12"
        key.write_bytes(base64.b64decode(os.environ["RELEASE_KEYSTORE_BASE64"], validate=True))
        key.chmod(0o600)
        manifest_key = private / "manifest.pem"
        manifest_key.write_text(os.environ["UPDATE_SIGNING_KEY_PEM"])
        manifest_key.chmod(0o600)
        checked([str(tool), "sign", "--ks", str(key), "--ks-key-alias", "colorduo",
            "--ks-pass", "env:RELEASE_STORE_PASSWORD", "--key-pass", "env:RELEASE_STORE_PASSWORD",
            "--v2-signing-enabled", "true", "--v3-signing-enabled", "true", "--v4-signing-enabled", "false",
            "--out", str(target), str(args.apk)])
        verify_apk(tool, target, expected_cert)
        badging = checked([str(aapt), "dump", "badging", str(target)])
        for value in ["name='io.github.fengwenhua.colorduo'", "versionCode='" + version["versionCode"] + "'",
                      "versionName='" + version["versionName"] + "'"]:
            if value not in badging: raise RuntimeError("APK package/version mismatch")
        if "application-debuggable" in badging: raise RuntimeError("Release must not be debuggable")
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        (args.out / (name + ".sha256")).write_text(digest + "  " + name + "\n")
        manifest = args.out / "release-manifest.json"
        manifest.write_text(json.dumps({"schema": 1, "packageName": "io.github.fengwenhua.colorduo",
            "versionName": version["versionName"], "versionCode": int(version["versionCode"]), "apkName": name,
            "apkSize": target.stat().st_size, "sha256": digest, "certificateSha256": expected_cert,
            "sourceCommit": commit}, separators=(",", ":"), sort_keys=True) + "\n")
        signature = args.out / "release-manifest.sig"
        checked(["openssl", "dgst", "-sha256", "-sign", str(manifest_key), "-out", str(signature), str(manifest)])
        checked(["openssl", "dgst", "-sha256", "-verify", "app/src/main/assets/update-signing-public.pem",
            "-signature", str(signature), str(manifest)])
    print(json.dumps({"apk": name, "sha256": digest, "certificate": expected_cert, "sourceCommit": commit}))


if __name__ == "__main__":
    main()
