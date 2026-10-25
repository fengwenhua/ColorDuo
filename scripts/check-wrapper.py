#!/usr/bin/env python3
"""Verify the reviewed Wrapper bootstrap before executing it in CI."""
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# Official Gradle 9.8.0 checksums, retrieved from services.gradle.org/downloads.gradle.org.
JAR_SHA256 = "238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5"
ZIP_SHA256 = "bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c"

def main():
    wrapper = ROOT / "gradle/wrapper"
    if hashlib.sha256((wrapper / "gradle-wrapper.jar").read_bytes()).hexdigest() != JAR_SHA256:
        raise SystemExit("Unexpected Gradle Wrapper JAR")
    properties = dict(line.split("=", 1) for line in (wrapper / "gradle-wrapper.properties").read_text().splitlines() if "=" in line)
    if properties.get("distributionSha256Sum") != ZIP_SHA256:
        raise SystemExit("Unexpected Gradle distribution checksum")
    if properties.get("distributionUrl") != r"https\://services.gradle.org/distributions/gradle-9.8.0-bin.zip":
        raise SystemExit("Unexpected Gradle distribution URL")
    print("Gradle Wrapper JAR and distribution pins verified")

if __name__ == "__main__":
    main()
