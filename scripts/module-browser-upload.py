#!/usr/bin/env python3
"""Official GitHub web fallback for organization OAuth restrictions (macOS Chrome).

Uses an already authenticated browser. Upload/publish require the exact signed
source-tag artifacts and a byte-identical public main Release. No credentials
or CSRF values are read. Upload creates a draft; publishing is a separate action.
"""
import argparse
import base64
import hashlib
import importlib.util
import json
import os
import subprocess
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PREFIX = "https://github.com/Xposed-Modules-Repo/io.github.fengwenhua.colorduo"


def browser(javascript):
    script = '''tell application "Google Chrome"
repeat with w in windows
repeat with t in tabs of w
if URL of t starts with REPOSITORY then
return execute t javascript EXPRESSION
end if
end repeat
end repeat
return "MODULE_TAB_MISSING"
end tell'''.replace("REPOSITORY", json.dumps(PREFIX)).replace("EXPRESSION", json.dumps(javascript))
    result = subprocess.run(["osascript", "-"], input=script, capture_output=True, text=True, timeout=30)
    if result.returncode:
        raise RuntimeError("Chrome automation unavailable: " + result.stderr[:300])
    value = result.stdout.strip()
    if value == "MODULE_TAB_MISSING":
        raise RuntimeError("Open this repository's release editor in Chrome first")
    return value


def state():
    return json.loads(browser('''JSON.stringify({
      url:location.href,
      tag:document.getElementById('release_tag_name')?.value,
      title:document.getElementById('release_name')?.value,
      body:document.getElementById('release_body')?.value,
      assets:Array.from(document.querySelectorAll('input')).filter(n=>
        n.name==='release[release_assets_attributes][][name]'&&n.value).map(n=>n.value),
      errors:Array.from(document.querySelectorAll('.flash-error,.js-upload-error')).filter(n=>n.getClientRects().length&&n.textContent.trim())
        .map(n=>n.textContent.trim().slice(0,300))
    })'''))


class HttpsRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        if not newurl.startswith("https://"):
            raise RuntimeError("Refusing insecure release download redirect")
        return super().redirect_request(request, fp, code, message, headers, newurl)


def public_main(files, tag):
    opener = urllib.request.build_opener(HttpsRedirect)
    url = "https://api.github.com/repos/fengwenhua/ColorDuo/releases/tags/" + tag
    with opener.open(urllib.request.Request(url, headers={"User-Agent": "ColorDuo-release"}), timeout=30) as response:
        release = json.load(response)
    if release["draft"] or release["prerelease"]:
        raise RuntimeError("Main Release must already be public and stable")
    for file in files:
        asset = next((a for a in release["assets"] if a["name"] == file.name), None)
        if not asset or asset["size"] != file.stat().st_size:
            raise RuntimeError("Main asset missing or size mismatch: " + file.name)
        request = urllib.request.Request(asset["browser_download_url"], headers={"User-Agent": "ColorDuo-release"})
        with opener.open(request, timeout=90) as response:
            data = response.read(70 * 1024 * 1024)
        if hashlib.sha256(data).digest() != hashlib.sha256(file.read_bytes()).digest():
            raise RuntimeError("Main asset digest mismatch: " + file.name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("inspect", "upload", "publish"))
    parser.add_argument("--dist", type=Path, default=Path("dist"))
    args = parser.parse_args()
    os.chdir(ROOT)
    if args.action == "inspect":
        current = state()
        current["bodyLength"] = len(current.pop("body", "") or "")
        print(json.dumps(current, ensure_ascii=False))
        return
    spec = importlib.util.spec_from_file_location("release_pipeline", ROOT / "scripts/github-release.py")
    pipeline = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(pipeline)
    stamp = pipeline.version()
    tag = stamp["versionCode"] + "-" + stamp["versionName"]
    files = pipeline.artifacts(args.dist.resolve())
    public_main(files, "v" + stamp["versionName"])
    notes = (ROOT / "docs/release-notes" / ("release-notes-" + stamp["versionName"] + ".md")).read_text()
    current = state()
    if (current.get("tag") != tag or not current["url"].startswith(PREFIX + "/releases/")
            or not ("/new" in current["url"] or "/edit/" in current["url"])):
        raise RuntimeError("Unexpected module release editor or tag")
    if args.action == "upload":
        if current["assets"]:
            raise RuntimeError("Assets already attached; inspect/resume rather than duplicate them")
        browser('''var title=document.getElementById('release_name');
Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(title,VERSION);
title.dispatchEvent(new Event('input',{bubbles:true}));
var body=document.getElementById('release_body');
Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(body,NOTES);
body.dispatchEvent(new Event('input',{bubbles:true}));body.dispatchEvent(new Event('change',{bubbles:true}));
window.colorDuoReleaseFiles=[];'ready';'''.replace("VERSION", json.dumps(stamp["versionName"])).replace("NOTES", json.dumps(notes)))
        for file in files:
            encoded = base64.b64encode(file.read_bytes()).decode()
            browser("window.colorDuoReleaseChunks=[];'ready'")
            for start in range(0, len(encoded), 400_000):
                browser("window.colorDuoReleaseChunks.push(" + json.dumps(encoded[start:start + 400_000]) + ");'ok'")
            browser("window.colorDuoReleaseFiles.push(new File([Uint8Array.from(atob(window.colorDuoReleaseChunks.join('')),c=>c.charCodeAt(0))],"
                    + json.dumps(file.name) + ",{type:'application/octet-stream'}));'added'")
            print(json.dumps({"prepared": file.name, "bytes": file.stat().st_size}), flush=True)
        browser('''var input=document.getElementById('releases-upload');if(!input)throw Error('File input missing');
var transfer=new DataTransfer();window.colorDuoReleaseFiles.forEach(f=>transfer.items.add(f));
input.files=transfer.files;input.dispatchEvent(new Event('change',{bubbles:true}));'upload started' ''')
        expected = {p.name for p in files}
        for _ in range(50):
            time.sleep(1)
            uploaded = state()
            if expected.issubset(set(uploaded["assets"])):
                print(json.dumps({"draft": tag, "assets": sorted(expected)}, ensure_ascii=False))
                return
            if uploaded["errors"]:
                raise RuntimeError("GitHub upload error: " + str(uploaded["errors"]))
        raise RuntimeError("Upload still pending; inspect the draft, do not upload duplicates")
    if current["title"] != stamp["versionName"] or current["body"] != notes:
        raise RuntimeError("Release title/body differ from committed source")
    expected = {p.name for p in files}
    if set(current["assets"]) != expected:
        raise RuntimeError("Expected the four verified assets exactly once")
    if len(current["assets"]) != len(expected):
        raise RuntimeError("Duplicate release asset links; inspect before publishing")
    browser('''var button=Array.from(document.querySelectorAll('button')).find(n=>n.textContent.trim()==='Publish release');
if(!button||button.disabled)throw Error('Publish button unavailable');button.click();'publish requested' ''')
    print(json.dumps({"publishing": tag, "verifyPublicDownloadsNext": True}))


if __name__ == "__main__":
    main()
