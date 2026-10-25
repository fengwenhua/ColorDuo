"""Exercise publication integrity and deletion boundaries without network writes."""
import importlib.util
import unittest
from types import SimpleNamespace
from pathlib import Path
from unittest.mock import patch
from urllib.request import Request

spec = importlib.util.spec_from_file_location("release_pipeline", Path(__file__).resolve().parents[1] / "scripts/github-release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)
STAMP = {"versionName": "1.1.0", "versionCode": "10100"}
NOTES = "先卸载旧版本；卸载会清除配置，重新启用 LSPosed。"
FILES = [SimpleNamespace(name=name, read_bytes=lambda data=name: data.encode())
         for name in ["ColorDuo-1.1.0.apk", "ColorDuo-1.1.0.apk.sha256", "release-manifest.json", "release-manifest.sig"]]


class API:
    def __init__(self, draft=True, corrupt=False):
        self.current = {"id": 10, "tag_name": "v1.1.0", "name": "1.1.0", "body": NOTES,
            "draft": draft, "prerelease": False, "html_url": "https://github.com/fengwenhua/ColorDuo/releases/tag/v1.1.0",
            "upload_url": "https://uploads.github.com/repos/fengwenhua/ColorDuo/releases/10/assets{?name}", "assets": []}
        self.calls = []
        self.corrupt = corrupt

    def optional(self, path): return self.current

    def request(self, path, method="GET", value=None, blob=None, **kwargs):
        self.calls.append((path, method))
        if method == "POST":
            name = path.split("name=", 1)[1]
            asset = {"name": name, "id": len(self.current["assets"]) + 1}
            self.current["assets"].append(asset)
            return asset
        if method == "PATCH":
            self.current["draft"] = False
            return self.current
        if method == "DELETE": return None
        if "/releases/assets/" in path:
            index = int(path.rsplit("/", 1)[1]) - 1
            return b"modified" if self.corrupt else FILES[index].read_bytes()
        if "/releases/tags/" in path: return self.current
        if "/releases?" in path:
            return [self.current, {"id": 1, "tag_name": "v1.0.0", "draft": False},
                    {"id": 2, "tag_name": "future-draft", "draft": True}]
        raise AssertionError(path)


class ReleasePipelineTest(unittest.TestCase):
    def setUp(self):
        self.patches = [patch.object(release, "version", return_value=STAMP),
            patch.object(release, "artifacts", return_value=FILES), patch.object(Path, "read_text", return_value=NOTES)]
        for p in self.patches: p.start()
        self.addCleanup(lambda: [p.stop() for p in reversed(self.patches)])

    def test_publish_after_all_four_assets_are_read_back(self):
        api = API()
        release.publish(api, "fengwenhua/ColorDuo", "v1.1.0", Path("dist"))
        self.assertEqual(4, len(api.current["assets"]))
        self.assertFalse(api.current["draft"])
        self.assertEqual("PATCH", api.calls[-1][1])

    def test_corrupt_upload_keeps_draft_and_never_publishes(self):
        api = API(corrupt=True)
        with self.assertRaises(RuntimeError): release.publish(api, "fengwenhua/ColorDuo", "v1.1.0", Path("dist"))
        self.assertTrue(api.current["draft"])
        self.assertFalse(any(method == "PATCH" for _, method in api.calls))

    def test_existing_public_release_is_immutable(self):
        api = API(draft=False)
        with self.assertRaises(RuntimeError): release.publish(api, "fengwenhua/ColorDuo", "v1.1.0", Path("dist"))
        self.assertFalse(any(method != "GET" for _, method in api.calls))

    def test_prune_requires_explicit_execute_and_preserves_keep_and_drafts(self):
        api = API(draft=False)
        api.current["assets"] = [{"name": FILES[0].name, "id": 1}]
        release.prune(api, "fengwenhua/ColorDuo", "v1.1.0", Path("dist"), False)
        self.assertFalse(any(method == "DELETE" for _, method in api.calls))
        release.prune(api, "fengwenhua/ColorDuo", "v1.1.0", Path("dist"), True)
        deleted = [path for path, method in api.calls if method == "DELETE"]
        self.assertEqual(["repos/fengwenhua/ColorDuo/releases/1"], deleted)

    def test_corrupt_kept_apk_prevents_any_pruning(self):
        api = API(draft=False, corrupt=True)
        api.current["assets"] = [{"name": FILES[0].name, "id": 1}]
        with self.assertRaises(RuntimeError): release.prune(api, "fengwenhua/ColorDuo", "v1.1.0", Path("dist"), True)
        self.assertFalse(any(method == "DELETE" for _, method in api.calls))

    def test_redirect_never_sends_api_credential_to_cdn(self):
        original = Request("https://api.github.com/repos/fengwenhua/ColorDuo/releases/assets/1",
                           headers={"Authorization": "Bearer test-only"})
        redirected = release.SafeRedirect().redirect_request(original, None, 302, "Found", {}, "https://release-assets.githubusercontent.com/blob")
        self.assertIsNone(redirected.get_header("Authorization"))
        with self.assertRaises(RuntimeError):
            release.SafeRedirect().redirect_request(original, None, 302, "Found", {}, "http://example.com/blob")


if __name__ == "__main__": unittest.main()
