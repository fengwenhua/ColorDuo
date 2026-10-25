#!/usr/bin/env python3
"""Opt-in phone UI capture at three font scales; always restores the original setting.

Run once per physical panel. Requires an unlocked, connected device. Checks
navigation bounds and captures evidence; does not claim pixel/TalkBack equivalence.
"""
import argparse
import json
import os
import re
import struct
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "io.github.fengwenhua.colorduo"
LABELS = {"home": ("首页", "Home"), "about": ("关于", "About")}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--panel", choices=("outer", "inner", "split"), required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, mode=0o700, exist_ok=False)
    adb = [args.adb, "-s", args.serial]

    def run(*parts):
        return subprocess.check_output([*adb, *parts], timeout=30).decode().strip()

    def hierarchy():
        run("shell", "uiautomator", "dump", "/data/local/tmp/colorduo-ui-verification.xml")
        return ET.fromstring(run("exec-out", "cat", "/data/local/tmp/colorduo-ui-verification.xml"))

    def button(tree, page):
        candidates = []
        for node in tree.iter("node"):
            if node.get("package") != PACKAGE or node.get("text") not in LABELS[page]:
                continue
            bounds = tuple(map(int, re.findall(r"\d+", node.get("bounds", ""))))
            if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]:
                candidates.append(bounds)
        if not candidates:
            raise RuntimeError("Module navigation unavailable; unlock the device and open ColorDuo")
        return max(candidates, key=lambda bounds: bounds[3])

    def tap(bounds):
        left, top, right, bottom = bounds
        run("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
        time.sleep(0.5)

    def capture(name, tree):
        data = subprocess.check_output([*adb, "exec-out", "screencap", "-p"], timeout=30)
        # Foldables print a display-selection warning ahead of the actual PNG bytes.
        data = data[data.index(b"\x89PNG\r\n\x1a\n"):]
        width, height = struct.unpack(">II", data[16:24])
        for page in LABELS:
            left, top, right, bottom = button(tree, page)
            if not (0 <= left < right <= width and 0 <= top < bottom <= height):
                raise RuntimeError("Navigation is outside the active window")
        target = args.output / (name + ".png")
        with os.fdopen(os.open(target, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), "wb") as output:
            output.write(data)
        return {"file": target.name, "pixels": [width, height], "navigationBoundsPass": True}

    previous = run("shell", "settings", "get", "system", "font_scale")
    report = {"panel": args.panel, "originalFontScale": previous, "captures": []}
    try:
        for scale in ("1.0", "1.3", "1.5"):
            run("shell", "settings", "put", "system", "font_scale", scale)
            run("shell", "am", "start", "-n", PACKAGE + "/.ui.MainActivity")
            time.sleep(1)
            tap(button(hierarchy(), "home"))
            report["captures"].append(capture("font-" + scale + "-home", hierarchy()))
            tap(button(hierarchy(), "about"))
            report["captures"].append(capture("font-" + scale + "-about", hierarchy()))
            print(json.dumps({"panel": args.panel, "fontScale": scale, "captures": "saved"}), flush=True)
        tap(button(hierarchy(), "home"))
    finally:
        if previous == "null":
            run("shell", "settings", "delete", "system", "font_scale")
        else:
            run("shell", "settings", "put", "system", "font_scale", previous)
        report["restoredFontScale"] = run("shell", "settings", "get", "system", "font_scale")
        if report["restoredFontScale"] != previous:
            raise RuntimeError("Original font scale was not restored")
        target = args.output / "report.json"
        with os.fdopen(os.open(target, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), "w") as output:
            json.dump(report, output, ensure_ascii=False, indent=2)
        run("shell", "rm", "/data/local/tmp/colorduo-ui-verification.xml")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
