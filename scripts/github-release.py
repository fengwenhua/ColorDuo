#!/usr/bin/env python3
"""Idempotent two-repository release publishing. Pruning is explicit and preserves Git tags."""
import argparse
import hashlib
import json
import os
import re
import subprocess
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

REPOSITORIES = {"fengwenhua/ColorDuo", "Xposed-Modules-Repo/io.github.fengwenhua.colorduo"}


class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not newurl.startswith("https://"): raise RuntimeError("Refusing insecure redirect")
        result = super().redirect_request(req, fp, code, msg, headers, newurl)
        if urllib.parse.urlsplit(newurl).netloc != urllib.parse.urlsplit(req.full_url).netloc:
            result.remove_header("Authorization")
        return result


class GitHub:
    def __init__(self, token):
        self.token = token
        self.opener = urllib.request.build_opener(SafeRedirect)

    def request(self, path, method="GET", value=None, blob=None, accept="application/vnd.github+json", binary=False):
        url = path if path.startswith("https://") else "https://api.github.com/" + path
        host = urllib.parse.urlsplit(url).netloc
        if host not in {"api.github.com", "uploads.github.com"}: raise RuntimeError("Unexpected API host")
        headers = {"Authorization": "Bearer " + self.token, "User-Agent": "ColorDuo-release", "Accept": accept}
        body = blob
        if value is not None:
            body = json.dumps(value).encode()
            headers["Content-Type"] = "application/json"
        elif blob is not None: headers["Content-Type"] = "application/octet-stream"
        request = urllib.request.Request(url, data=body, headers=headers, method=method)
        with self.opener.open(request, timeout=90) as response:
            raw = response.read(70 * 1024 * 1024)
        return raw if binary else json.loads(raw) if raw else None

    def optional(self, path):
        try: return self.request(path)
        except urllib.error.HTTPError as error:
            if error.code == 404: return None
            raise


def version():
    return dict(line.split("=", 1) for line in Path("version.properties").read_text().splitlines() if "=" in line)


def tag_for(repo, release):
    return ("v" if repo == "fengwenhua/ColorDuo" else release["versionCode"] + "-") + release["versionName"]


def artifacts(directory):
    release = version()
    manifest = json.loads((directory / "release-manifest.json").read_text())
    apk = directory / ("ColorDuo-" + release["versionName"] + ".apk")
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    expected = Path("release-trust.properties").read_text().strip().split("=", 1)[1]
    if (manifest.get("schema") != 1 or manifest.get("packageName") != "io.github.fengwenhua.colorduo"
            or manifest.get("versionName") != release["versionName"] or manifest.get("versionCode") != int(release["versionCode"])
            or manifest.get("apkName") != apk.name or manifest.get("apkSize") != apk.stat().st_size
            or manifest.get("sha256") != digest or manifest.get("certificateSha256") != expected):
        raise RuntimeError("Release artifacts do not match source identity")
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    if manifest.get("sourceCommit") != head: raise RuntimeError("Artifact/source commit mismatch")
    subprocess.run(["openssl", "dgst", "-sha256", "-verify", "app/src/main/assets/update-signing-public.pem",
        "-signature", str(directory / "release-manifest.sig"), str(directory / "release-manifest.json")], check=True, capture_output=True)
    sha = directory / (apk.name + ".sha256")
    if sha.read_text() != digest + "  " + apk.name + "\n": raise RuntimeError("Checksum asset mismatch")
    return [apk, sha, directory / "release-manifest.json", directory / "release-manifest.sig"]


def find_release(api, base, tag):
    # GitHub's tag endpoint finds published releases. Authenticated release lists
    # also include drafts, including one whose upload response was lost.
    current = api.optional(base + "/releases/tags/" + urllib.parse.quote(tag, safe=""))
    if current is not None:
        return current
    matches = {}
    page = 1
    while True:
        items = api.request(base + f"/releases?per_page=100&page={page}")
        for item in items:
            if item["tag_name"] == tag:
                matches[item["id"]] = item
        if len(items) < 100:
            break
        page += 1
    if len(matches) > 1:
        raise RuntimeError("Multiple releases share this tag; inspect them before retrying")
    return next(iter(matches.values()), None)


def publish(api, repo, tag, directory):
    stamp = version()
    if tag != tag_for(repo, stamp): raise RuntimeError("Unexpected release tag")
    files = artifacts(directory)
    notes = Path("docs/release-notes/release-notes-" + stamp["versionName"] + ".md").read_text()
    if stamp["versionName"] == "1.1.0" and not all(text in notes for text in ["先卸载旧版本", "清除", "LSPosed"]):
        raise RuntimeError("Signing migration notice is required")
    base = "repos/" + repo
    current = find_release(api, base, tag)
    if current is None:
        current = api.request(base + "/releases", "POST", {"tag_name": tag, "name": stamp["versionName"],
            "body": notes, "draft": True, "prerelease": False,
            "target_commitish": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
                if repo == "fengwenhua/ColorDuo" else "main"})
    if (current["tag_name"] != tag or current["name"] != stamp["versionName"]
            or current["body"] != notes or current["prerelease"]):
        raise RuntimeError("Existing release differs; refusing replacement")
    existing = {a["name"]: a for a in current["assets"]}
    if len(existing) != len(current["assets"]) or set(existing) - {file.name for file in files}:
        raise RuntimeError("Unexpected or duplicate release assets; inspect them before retrying")
    if any(asset.get("state") != "uploaded" for asset in existing.values()):
        # A starter can also be an in-flight upload. Do not delete/restart it just
        # because a response timed out; the previous attempt must finish first.
        raise RuntimeError("Release asset upload has not completed; inspect the previous attempt before retrying")
    for file in files:
        asset = existing.get(file.name)
        if asset is None:
            if not current["draft"]: raise RuntimeError("Published release is incomplete; refusing mutation")
            asset = api.request(current["upload_url"].split("{", 1)[0] + "?name=" + urllib.parse.quote(file.name),
                "POST", blob=file.read_bytes())
        if asset.get("state") != "uploaded":
            raise RuntimeError("Release asset upload has not completed; inspect the previous attempt before retrying")
        data = api.request(base + "/releases/assets/" + str(asset["id"]), accept="application/octet-stream", binary=True)
        if hashlib.sha256(data).digest() != hashlib.sha256(file.read_bytes()).digest():
            raise RuntimeError("Remote asset verification failed: " + file.name)
    if current["draft"]:
        current = api.request(base + "/releases/" + str(current["id"]), "PATCH", {"draft": False, "make_latest": "true"})
    print(json.dumps({"release": current["html_url"], "id": current["id"], "assetsVerified": len(files)}))


def prune(api, repo, tag, directory, execute):
    # Validate the kept release's signed APK before removing any prior public download.
    files = artifacts(directory)
    base = "repos/" + repo
    kept = api.request(base + "/releases/tags/" + tag)
    if kept["draft"] or kept["prerelease"]: raise RuntimeError("Kept release is not public and stable")
    apk = files[0]
    match = next(a for a in kept["assets"] if a["name"] == apk.name)
    downloaded = api.request(base + "/releases/assets/" + str(match["id"]), accept="application/octet-stream", binary=True)
    if hashlib.sha256(downloaded).digest() != hashlib.sha256(apk.read_bytes()).digest():
        raise RuntimeError("Kept release APK mismatch")
    old = []
    page = 1
    while True:
        items = api.request(base + f"/releases?per_page=100&page={page}")
        old.extend(r for r in items if r["id"] != kept["id"] and not r["draft"])
        if len(items) < 100: break
        page += 1
    print(json.dumps({"repository": repo, "keep": tag, "remove": [(r["id"], r["tag_name"]) for r in old], "execute": execute}))
    if execute:
        for release in old: api.request(base + "/releases/" + str(release["id"]), "DELETE")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["preflight", "publish", "prune"])
    parser.add_argument("--repo", choices=sorted(REPOSITORIES), default="fengwenhua/ColorDuo")
    parser.add_argument("--tag", required=True)
    parser.add_argument("--dist", type=Path, default=Path("dist"))
    parser.add_argument("--execute", action="store_true", help="Actually delete the explicitly listed old Releases")
    args = parser.parse_args()
    if args.tag != tag_for(args.repo, version()): raise SystemExit("Tag/version mismatch")
    if args.action == "preflight":
        if args.repo != "fengwenhua/ColorDuo" or not re.fullmatch(r"v\d+\.\d+\.\d+", args.tag):
            raise SystemExit("Expected a formal source tag")
        head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        if subprocess.check_output(["git", "rev-parse", args.tag + "^{}"], text=True).strip() != head:
            raise SystemExit("Tag/checkout mismatch")
        if subprocess.check_output(["git", "status", "--porcelain"], text=True).strip(): raise SystemExit("Dirty release checkout")
        subprocess.run(["git", "merge-base", "--is-ancestor", head, "origin/main"], check=True)
        notes = Path("docs/release-notes/release-notes-" + version()["versionName"] + ".md")
        if not notes.is_file(): raise SystemExit("Committed release notes required")
        for old_tag in subprocess.check_output(["git", "tag", "-l", "v*"], text=True).splitlines():
            if old_tag == args.tag or not re.fullmatch(r"v\d+\.\d+\.\d+", old_tag): continue
            old = subprocess.run(["git", "show", old_tag + ":version.properties"], capture_output=True, text=True)
            if old.returncode == 0:
                values = dict(line.split("=", 1) for line in old.stdout.splitlines() if "=" in line)
                if int(values["versionCode"]) >= int(version()["versionCode"]):
                    raise SystemExit("Release versionCode must exceed earlier formal tags")
        print("Clean committed release checkout verified: " + head)
    else:
        token = os.environ.get("GITHUB_TOKEN")
        if not token: raise SystemExit("A scoped GITHUB_TOKEN is required")
        api = GitHub(token)
        if args.action == "publish": publish(api, args.repo, args.tag, args.dist)
        else: prune(api, args.repo, args.tag, args.dist, args.execute)


if __name__ == "__main__":
    main()
