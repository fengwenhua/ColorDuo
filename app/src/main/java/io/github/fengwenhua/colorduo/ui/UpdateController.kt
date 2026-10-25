/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.github.fengwenhua.colorduo.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import io.github.fengwenhua.colorduo.BuildConfig
import io.github.fengwenhua.colorduo.UpdateManifest
import io.github.fengwenhua.colorduo.UpdateTrust
import io.github.fengwenhua.colorduo.VersionPolicy
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val REPO = "fengwenhua/ColorDuo"
private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
private const val MIRROR = "https://gh-proxy.com/"
internal const val RELEASES_PAGE = "https://github.com/$REPO/releases"
internal const val REPO_HOME = "https://github.com/$REPO"

internal data class UpdateInfo(
    val tag: String,
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    val assetId: Long,
    val manifest: UpdateManifest,
)

internal sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val current: String) : UpdateState
    data object NoRelease : UpdateState
    data class Available(val info: UpdateInfo, val current: String) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val detail: String?) : UpdateState
}

internal enum class InstallResult { Started, NeedsPermission, NoInstaller, InvalidPackage }

/** Metadata may travel through a proxy; only the embedded public key authorizes APK bytes. */
internal class UpdateController(private val context: Context) {
    var state: UpdateState by mutableStateOf(UpdateState.Idle)
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    fun dispose() = scope.cancel()
    fun currentVersion(): String = installedVersion(context)

    fun check() {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        state = UpdateState.Checking
        scope.launch {
            val current = installedVersion(context)
            val result = withContext(Dispatchers.IO) { runCatching { retry { fetchLatest(context) } } }
            state = result.fold(onSuccess = { info ->
                when {
                    info == null -> UpdateState.NoRelease
                    info.manifest.versionCode > context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode ->
                        UpdateState.Available(info, current)
                    else -> UpdateState.UpToDate(current)
                }
            }, onFailure = { UpdateState.Failed(it.message ?: it.javaClass.simpleName) })
        }
    }

    fun download(info: UpdateInfo) {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        state = UpdateState.Downloading(info, 0f)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val directory = File(context.cacheDir, "updates").apply { check(mkdirs() || isDirectory) }
                    val target = File(directory, info.apkName)
                    var lastPercent = -2
                    downloadFrom(context, info, target) { progress ->
                        if (!scope.isActive) throw CancellationException()
                        val percent = (progress * 100).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            scope.launch { state = UpdateState.Downloading(info, progress) }
                        }
                    }
                    target
                }
            }
            state = result.fold(onSuccess = { UpdateState.Ready(info, it) },
                onFailure = { UpdateState.Failed(it.message ?: it.javaClass.simpleName) })
        }
    }

    fun install(file: File): InstallResult {
        val ready = state as? UpdateState.Ready ?: return InstallResult.InvalidPackage
        if (ready.file != file || runCatching { UpdateTrust.verifyApk(context, file, ready.info.manifest) }.isFailure)
            return InstallResult.InvalidPackage
        if (!context.packageManager.canRequestPackageInstalls()) return InstallResult.NeedsPermission
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            InstallResult.Started
        } catch (_: Exception) { InstallResult.NoInstaller }
    }

    fun openInstallPermissionScreen() {
        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    fun openInBrowser(url: String) {
        // Callers only open our repository/release URLs, not proxy-provided html_url values.
        if (!url.startsWith(REPO_HOME + "/") && url != REPO_HOME) return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

internal fun isNewer(a: String, b: String): Boolean = runCatching { VersionPolicy.compare(a, b) > 0 }.getOrDefault(false)

private fun installedVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull()?.takeIf { it.isNotBlank() } ?: "0"

private fun fetchLatest(context: Context): UpdateInfo? {
    var failure: Exception? = null
    for (url in listOf(API_LATEST, MIRROR + API_LATEST)) {
        try {
            val bytes = requestBytes(url, 256 * 1024, allow404 = url == API_LATEST) ?: return null
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            val tag = json.getString("tag_name")
            if (!tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.]+)?")))
                throw SecurityException("Invalid release tag")
            val base = "https://github.com/$REPO/releases/download/$tag/"
            val publicKey = context.assets.open("update-signing-public.pem").bufferedReader().use { it.readText() }
            var manifest: UpdateManifest? = null
            for (prefix in listOf("", MIRROR)) {
                try {
                    manifest = UpdateManifest.verify(
                        requestBytes(prefix + base + "release-manifest.json", UpdateManifest.MAX_BYTES)!!,
                        requestBytes(prefix + base + "release-manifest.sig", 80)!!,
                        publicKey, BuildConfig.RELEASE_CERT_SHA256)
                    break
                } catch (error: Exception) { failure = error }
            }
            val verified = manifest ?: throw failure ?: SecurityException("Signed release unavailable")
            if (tag != "v" + verified.version) throw SecurityException("Release/manifest version mismatch")
            val assets = json.optJSONArray("assets")
            var assetId = 0L
            if (assets != null) for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name") == verified.apkName) { assetId = asset.optLong("id"); break }
            }
            return UpdateInfo(tag, verified.version, json.optString("body").trim(),
                "$RELEASES_PAGE/tag/$tag", base + verified.apkName, verified.apkName, verified.apkSize, assetId, verified)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { failure = error }
    }
    throw failure ?: IllegalStateException("No update endpoint")
}

private fun connection(url: String, accept: String) = (URL(url).openConnection() as HttpURLConnection).apply {
    require(url.startsWith("https://"))
    connectTimeout = 15_000
    readTimeout = 20_000
    instanceFollowRedirects = true
    setRequestProperty("User-Agent", "ColorDuo-Updater")
    setRequestProperty("Accept", accept)
}

private fun requestBytes(url: String, limit: Int, allow404: Boolean = false): ByteArray? {
    val request = connection(url, "application/vnd.github+json")
    try {
        val code = request.responseCode
        if (code == 404 && allow404) return null
        check(code == 200) { "HTTP $code" }
        return request.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                check(output.size() + read <= limit) { "Update response exceeds limit" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    } finally { request.disconnect() }
}

private fun downloadFrom(context: Context, info: UpdateInfo, target: File, progress: (Float) -> Unit) {
    val official = buildList {
        if (info.assetId > 0) add("https://api.github.com/repos/$REPO/releases/assets/${info.assetId}")
        add(info.apkUrl)
    }
    var failure: Exception? = null
    for (url in official + official.map { MIRROR + it }) {
        try { retry { downloadToFile(context, url, info, target, progress) }; return }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { failure = error }
    }
    throw failure ?: IllegalStateException("No download endpoint")
}

private fun downloadToFile(context: Context, url: String, info: UpdateInfo, target: File, progress: (Float) -> Unit) {
    val request = connection(url, "application/octet-stream")
    val part = File(target.parentFile, "${target.name}.part")
    try {
        check(request.responseCode == 200) { "HTTP ${request.responseCode}" }
        var copied = 0L
        request.inputStream.use { input ->
            part.outputStream().use { output ->
                val buffer = ByteArray(65_536)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read
                    check(copied <= info.apkSize) { "APK exceeds signed size" }
                    output.write(buffer, 0, read)
                    progress(copied.toFloat() / info.apkSize)
                }
            }
        }
        UpdateTrust.verifyApk(context, part, info.manifest)
        java.nio.file.Files.move(part.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        progress(1f)
    } finally { request.disconnect(); part.delete() }
}

private inline fun <T> retry(block: () -> T): T {
    var failure: Exception? = null
    repeat(3) { attempt ->
        try { return block() }
        catch (error: CancellationException) { throw error }
        catch (error: SecurityException) { throw error }
        catch (error: Exception) {
            failure = error
            if (attempt < 2) Thread.sleep(400L * (attempt + 1))
        }
    }
    throw failure ?: IllegalStateException("Retry failed")
}

internal fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) String.format(Locale.US, "%.1f MB", mb)
        else String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}
