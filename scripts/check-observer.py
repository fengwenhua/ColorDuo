#!/usr/bin/env python3
"""Validate sampling-hook presence in real APK DEX files, without device writes."""
import argparse
from zipfile import ZipFile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("apk")
parser.add_argument("--expect", choices=["present", "absent"], required=True)
args = parser.parse_args()
with ZipFile(args.apk) as apk:
    dexes = [apk.read(name) for name in apk.namelist() if name.startswith("classes") and name.endswith(".dex")]
assert dexes, "APK contains no DEX"
present = any(b"colorduo-coloros-observe-" in dex for dex in dexes)
if present != (args.expect == "present"):
    raise SystemExit(f"Observer hook mismatch: expected {args.expect}, present={present}")
print(f"Observer hooks {args.expect}: verified {args.apk}")
