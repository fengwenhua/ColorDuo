#!/usr/bin/env python3
"""One-time GitHub-hosted generation. Private material never becomes an artifact."""
import base64
import hashlib
import json
import os
import secrets
import subprocess
import tempfile
from pathlib import Path


def gh(*args, data=None):
    result = subprocess.run(["gh", *args], input=data, capture_output=True, check=False)
    if result.returncode:
        # Do not echo command input, response bodies or credential-bearing errors.
        raise RuntimeError("GitHub operation failed: " + args[0])
    return result.stdout


def main():
    if (os.environ.get("GITHUB_ACTIONS") != "true" or
            os.environ.get("GITHUB_REPOSITORY") != "fengwenhua/ColorDuo" or
            os.environ.get("GITHUB_REF") != "refs/heads/main"):
        raise SystemExit("Run only in the reviewed main-branch bootstrap workflow")
    names = ["RELEASE_STORE_PASSWORD", "RELEASE_KEYSTORE_BASE64", "UPDATE_SIGNING_KEY_PEM"]
    existing = {entry["name"] for entry in json.loads(gh("secret", "list", "--env", "release", "--json", "name"))}
    if set(names) & existing:
        raise SystemExit("Signing secrets already exist; refusing replacement")
    public = Path(os.environ["RUNNER_TEMP"]) / "colorduo-signing-public"
    public.mkdir(mode=0o700)
    created = []
    try:
        with tempfile.TemporaryDirectory(prefix="colorduo-signing-", dir=os.environ["RUNNER_TEMP"]) as raw:
            private = Path(raw)
            password = secrets.token_urlsafe(48)
            env = {**os.environ, "KEYSTORE_PASSWORD": password}
            key = private / "release.p12"
            subprocess.run(["keytool", "-genkeypair", "-keystore", str(key), "-storetype", "PKCS12",
                "-alias", "colorduo", "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000",
                "-dname", "CN=ColorDuo, O=fengwenhua", "-storepass:env", "KEYSTORE_PASSWORD",
                "-keypass:env", "KEYSTORE_PASSWORD"], env=env, check=True, capture_output=True)
            cert = public / "release-certificate.pem"
            subprocess.run(["keytool", "-exportcert", "-rfc", "-keystore", str(key), "-alias", "colorduo",
                "-storepass:env", "KEYSTORE_PASSWORD", "-file", str(cert)], env=env, check=True, capture_output=True)
            manifest_key = private / "manifest.pem"
            subprocess.run(["openssl", "genpkey", "-algorithm", "ED25519", "-out", str(manifest_key)], check=True)
            subprocess.run(["openssl", "pkey", "-in", str(manifest_key), "-pubout", "-out",
                str(public / "update-signing-public.pem")], check=True)
            der = subprocess.check_output(["openssl", "x509", "-in", str(cert), "-outform", "DER"])
            digest = hashlib.sha256(der).hexdigest()
            (public / "release-trust.properties").write_text("certificateSha256=" + digest + "\n", encoding="utf-8")
            values = [password.encode(), base64.b64encode(key.read_bytes()), manifest_key.read_bytes()]
            for name, value in zip(names, values):
                gh("secret", "set", name, "--env", "release", data=value)
                created.append(name)
            print("Cloud signing initialization complete; certificate SHA-256: " + digest)
    except BaseException:
        # A partial initialization must not leave a keystore without its password.
        for name in reversed(created):
            gh("secret", "delete", name, "--env", "release")
        raise


if __name__ == "__main__":
    main()
