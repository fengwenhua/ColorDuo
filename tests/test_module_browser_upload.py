"""Optional authenticated metadata never authenticates public asset downloads."""
import importlib.util
import io
import json
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
from urllib.request import Request

spec = importlib.util.spec_from_file_location("module_upload", Path(__file__).resolve().parents[1] / "scripts/module-browser-upload.py")
upload = importlib.util.module_from_spec(spec)
spec.loader.exec_module(upload)


class ModuleDownloadTest(unittest.TestCase):
    def test_token_only_used_for_main_repository_metadata(self):
        payload = b"public signed APK"
        file = SimpleNamespace(name="ColorDuo-1.1.1.apk", stat=lambda: SimpleNamespace(st_size=len(payload)), read_bytes=lambda: payload)
        release = {"draft": False, "prerelease": False, "assets": [{"name": file.name, "size": len(payload),
                   "browser_download_url": "https://github.com/fengwenhua/ColorDuo/releases/download/v1.1.1/" + file.name}]}
        requests = []

        def opened(request, **kwargs):
            requests.append(request)
            return io.BytesIO(json.dumps(release).encode() if len(requests) == 1 else payload)

        with patch.dict(upload.os.environ, {"GITHUB_TOKEN": "test-only"}), patch.object(upload.urllib.request, "build_opener") as opener:
            opener.return_value.open.side_effect = opened
            upload.public_main([file], "v1.1.1")
        self.assertEqual("Bearer test-only", requests[0].get_header("Authorization"))
        self.assertIsNone(requests[1].get_header("Authorization"))

    def test_redirect_strips_credential_and_rejects_plain_http(self):
        request = Request("https://api.github.com/repos/fengwenhua/ColorDuo/releases/tags/v1.1.1",
                          headers={"Authorization": "Bearer test-only"})
        result = upload.HttpsRedirect().redirect_request(request, None, 302, "Found", {}, "https://release-assets.githubusercontent.com/blob")
        self.assertIsNone(result.get_header("Authorization"))
        with self.assertRaises(RuntimeError):
            upload.HttpsRedirect().redirect_request(request, None, 302, "Found", {}, "http://example.com/blob")


if __name__ == "__main__": unittest.main()
