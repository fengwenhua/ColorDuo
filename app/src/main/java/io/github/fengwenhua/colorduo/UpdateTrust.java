/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.io.File;
import java.security.MessageDigest;

/** APK identity checks supplement the detached manifest signature and byte digest. */
public final class UpdateTrust {
    private UpdateTrust() {}
    public static void verifyApk(Context context, File apk, UpdateManifest manifest) throws Exception {
        manifest.verifyFile(apk);
        PackageManager pm = context.getPackageManager();
        PackageInfo downloaded = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo installed = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (downloaded == null || !context.getPackageName().equals(downloaded.packageName)
                || downloaded.getLongVersionCode() != manifest.versionCode
                || !manifest.version.equals(downloaded.versionName)
                || manifest.versionCode <= installed.getLongVersionCode()
                || downloaded.applicationInfo == null
                || (downloaded.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            throw new SecurityException("APK identity/version mismatch");
        if (!signer(downloaded).equals(manifest.certificateSha256))
            throw new SecurityException("APK certificate mismatch");
        if (!signer(installed).equals(manifest.certificateSha256))
            throw new SecurityException("New signing key: uninstall old ColorDuo first; settings will be cleared");
    }

    private static String signer(PackageInfo info) throws Exception {
        if (info.signingInfo == null || info.signingInfo.hasMultipleSigners()
                || info.signingInfo.getApkContentsSigners().length != 1)
            throw new SecurityException("Expected one current APK signer");
        return RemoteFontKey.hex(MessageDigest.getInstance("SHA-256")
                .digest(info.signingInfo.getApkContentsSigners()[0].toByteArray()));
    }
}
