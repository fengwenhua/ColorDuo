#!/usr/bin/env python3
"""Run module-owned Android probes and collect an allowlisted, private result report.

No installation or SystemUI restart occurs unless explicitly requested. Never
uninstalls an app or uses adb downgrade; stop on signature/version mismatches.
"""
import argparse
import json
import os
import re
import subprocess
import time
from pathlib import Path


def run(adb, *args, timeout=120):
    result = subprocess.run([*adb, *args], text=True, capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError("adb command failed: " + (result.stderr or result.stdout).strip()[:300])
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", type=Path, help="Explicitly install this same-signature debug APK")
    parser.add_argument("--restart-systemui", action="store_true")
    parser.add_argument("--skip-probes", action="store_true", help="Read-only production diagnostics")
    parser.add_argument("--output", type=Path, default=Path(".local/device-verification.json"))
    args = parser.parse_args()
    adb = [args.adb, "-s", args.serial]
    if run(adb, "get-state") != "device":
        raise RuntimeError("Authorized device required")
    if args.apk:
        if not args.apk.is_file():
            raise RuntimeError("APK missing")
        installed = run(adb, "install", "-r", str(args.apk))
        if "Success" not in installed:
            raise RuntimeError("Installation did not report Success")
    report = {"package": "io.github.fengwenhua.colorduo", "androidProbes": "not-run"}
    if not args.skip_probes:
        probe = run(adb, "shell", "am", "instrument", "-w", "-r",
                    report["package"] + "/.CodecInstrumentation")
        match = re.search(r"checks passed: (\d+)", probe)
        if not match or "INSTRUMENTATION_CODE: -1" not in probe:
            raise RuntimeError("Android checks failed: " + probe[-500:])
        report["androidProbes"] = int(match.group(1))
    if args.restart_systemui:
        previous = run(adb, "shell", "pidof", "com.android.systemui")
        if not previous.isdigit():
            raise RuntimeError("Expected one SystemUI process")
        run(adb, "shell", "su", "-c", "kill -TERM " + previous)
        for _ in range(20):
            time.sleep(1)
            probe_pid = subprocess.run([*adb, "shell", "pidof", "com.android.systemui"],
                                       capture_output=True, text=True, timeout=10)
            if probe_pid.returncode not in (0, 1):
                raise RuntimeError("Cannot query SystemUI restart")
            current = probe_pid.stdout.strip()
            if current.isdigit() and current != previous:
                report["systemuiRestart"] = "restarted"
                break
        else:
            raise RuntimeError("SystemUI did not restart")
    dump = run(adb, "shell", "dumpsys activity service com.android.systemui/.SystemUIService")
    # Only module-owned counters/status; no notifications, network names or raw OEM dump.
    report["adapter"] = sorted(set(line.strip() for line in dump.splitlines()
        if re.match(r"\s*(ColorDuo ColorOS adapter:|adapter/install |adapter/[^ /]+/[^ /]+/(?:mount|drawn) )", line)))
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with os.fdopen(os.open(args.output, flags, 0o600), "w") as output:
        json.dump(report, output, ensure_ascii=False, indent=2)
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
