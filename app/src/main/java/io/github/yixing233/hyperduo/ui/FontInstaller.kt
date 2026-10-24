package io.github.yixing233.hyperduo.ui

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream

/**
 * Copies a font the user picked into a place the hooked SystemUI process can
 * read, and reports what happened.
 *
 * <p>The two processes cannot share a file any other way. The settings app owns
 * its private storage and SystemUI cannot enter it, and a font is far too large
 * to carry through the preference mirror that every other setting uses. So the
 * app copies the bytes to a directory both sides can reach and records the path;
 * see [io.github.yixing233.hyperduo.Prefs.KEY_FONT_PATH].
 *
 * <p>The destination is the app's own external media directory. It is the one
 * shared location the app may write to without a storage permission on any
 * supported version, and unlike a granted content URI it keeps working after a
 * reboot - which matters because the hooked process reloads the font whenever
 * SystemUI restarts, long after the picker is gone.
 */
internal object FontInstaller {

    /** Subdirectory under the app's external media dir. */
    private const val DIR_NAME = "fonts"

    /** Read permission for everyone: the hooked process is a different uid. */
    private const val WORLD_READ = true

    /**
     * What an install attempt produced.
     *
     * <p>A sealed result rather than an exception, because every outcome except
     * "the storage is unavailable" is something the sheet has to tell the user
     * about in its own words, and the two failures carry different advice.
     */
    internal sealed interface Result {
        /** [path] is where the font now lives; [name] is what to show for it. */
        data class Installed(val path: String, val name: String, val remoteName: String = "",
                             val revision: String = java.util.UUID.randomUUID().toString()) : Result

        /** The picked file is not a font. */
        data object NotAFont : Result

        /** The copy could not be made; [detail] is for the log, not the user. */
        data class Failed(val detail: String?) : Result
    }

    /**
     * Reads [source], checks it really is a font, and installs it.
     *
     * <p>Runs on the IO dispatcher: the file is read and written whole, and the
     * picker can hand back a stream from anywhere - a cloud provider will block
     * for as long as its download takes.
     */
    internal suspend fun install(context: Context, source: Uri): Result =
        withContext(Dispatchers.IO) {
            try {
                val input = context.contentResolver.openInputStream(source)
                    ?: return@withContext Result.Failed("cannot open the picked file")
                input.use { raw ->
                    // Wrapped before anything reads it. The signature check below
                    // has to look at the first bytes and then hand the stream on
                    // whole, and a provider's own stream need not support mark or
                    // reset at all - so the rewind is provided here rather than
                    // asked of the source.
                    val stream = BufferedInputStream(raw, MARK_LIMIT)
                    // Checked before anything is written, and before the previous
                    // font is touched: rejecting a wrong pick must leave whatever
                    // the user already had exactly as it was.
                    if (!isFont(stream)) {
                        return@withContext Result.NotAFont
                    }
                    val dir = destinationDir(context)
                        ?: return@withContext Result.Failed("external storage is unavailable")
                    val name = displayName(context, source)
                    val target = File(dir, name)
                    // Reported rather than swallowed: "the copy call returned" is
                    // not the same as "the file is there and parses", and the user
                    // has to be told when those disagree.
                    writeVerified(stream, target)?.let { why ->
                        return@withContext Result.Failed(why)
                    }
                    // The hooked process runs as a different uid, so the default
                    // permissions of a freshly written file are not enough on
                    // their own.
                    target.setReadable(WORLD_READ, false)
                    dir.setReadable(WORLD_READ, false)
                    dir.setExecutable(true, false)
                    val service = io.github.yixing233.hyperduo.HyperDuoApp.xposedService
                    val remoteName = if (io.github.yixing233.hyperduo.ColorOsFontPublisher.supportedDevice() && service != null) {
                        io.github.yixing233.hyperduo.ColorOsFontPublisher.exportNow(context, service, target.absolutePath)
                    } else ""
                    Result.Installed(target.absolutePath, name, remoteName)
                }
            } catch (t: Throwable) {
                Result.Failed(t.message ?: t.javaClass.simpleName)
            }
        }

    /** Deletes [path] if it is one of ours, so removing a font leaves nothing behind. */
    internal suspend fun remove(path: String) = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext
        try {
            val file = File(path)
            val dir = file.parentFile ?: return@withContext
            // Only ever inside our own directory: the path comes back from
            // preferences, which are a file on disk that anything with root could
            // have edited, and this must not turn into "delete whatever path is
            // named here".
            if (dir.name == DIR_NAME) {
                file.delete()
            }
        } catch (ignored: Throwable) {
            // A font left behind is a few hundred KB; not worth surfacing.
        }
    }

    /** The name to file the font under, derived from the picker's own suggestion. */
    private fun displayName(context: Context, source: Uri): String {
        val raw = queryName(context, source) ?: "font"
        // The name is used as a file name, so it is stripped of anything that
        // would move it out of the directory or upset the file system. A provider
        // is free to return a path with separators in it.
        val cleaned = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._\\u4e00-\\u9fff-]"), "_")
            .take(64)
        return when {
            cleaned.isBlank() -> "font.ttf"
            cleaned.contains('.') -> cleaned
            else -> "$cleaned.ttf"
        }
    }

    private fun queryName(context: Context, source: Uri): String? = try {
        context.contentResolver.query(source, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (ignored: Throwable) {
        null
    }

    /**
     * The directory the copy goes in, created if needed.
     *
     * <p>Null when no external media directory exists - a device with the storage
     * unmounted - which the caller reports rather than guessing a fallback.
     */
    private fun destinationDir(context: Context): File? {
        val roots = context.getExternalMediaDirs()
        for (root in roots) {
            if (root == null) continue
            val dir = File(root, DIR_NAME)
            if (dir.isDirectory || dir.mkdirs()) {
                return dir
            }
        }
        return null
    }

    /**
     * Writes the stream straight to [target] and proves the file is usable.
     *
     * <p>A rename-into-place was tried first and does not work here: the shared
     * media directory is a FUSE mount, and a rename across it can report success
     * while leaving no file behind - which is exactly what happened on device
     * (the app said "installed", the directory's timestamp moved, and nothing was
     * in it). Writing the bytes directly and then reading them back is the only
     * form that both works and can be trusted, because the check is the file
     * system's own answer rather than the write call's.
     *
     * <p>The read-back is parsed, not just measured. A length check would accept a
     * file whose tail never landed - and a font cut short is exactly the case that
     * looks fine to every cheap test and then fails inside the renderer, where the
     * user sees nothing at all. Holding a real [Typeface] is the only proof that
     * what is on disk is a font.
     */
    private fun writeVerified(input: InputStream, target: File): String? {
        try {
            target.outputStream().use { out ->
                input.copyTo(out)
                out.flush()
            }
        } catch (t: Throwable) {
            return "cannot write " + target.absolutePath + ": " + (t.message ?: t.javaClass.simpleName)
        }
        // Ask the file system what it actually holds, and say what it answered.
        // The first version of this feature trusted the write call and reported
        // success for a file that was not there; the message has to carry the
        // observation so a recurrence names itself instead of being guessed at.
        val length = if (target.isFile) target.length() else -1L
        if (length <= 0L) {
            return StringBuilder("the copy did not land: ")
                .append(target.absolutePath)
                .append(" readable=").append(target.isFile)
                .append(" length=").append(length)
                .toString()
        }
        return try {
            Typeface.createFromFile(target)
            null
        } catch (t: Throwable) {
            StringBuilder("the copied file is not a usable font (length=")
                .append(length)
                .append("): ")
                .append(t.message ?: t.javaClass.simpleName)
                .toString()
        }
    }

    /**
     * Whether the stream starts with a font signature, leaving it rewound.
     *
     * <p>It reads the first four bytes and then puts them back. That rewind is the
     * whole reason this takes a stream rather than an array: the caller copies the
     * same stream afterwards, and an earlier version of this feature forgot the
     * rewind - so the copy started four bytes in, every installed file was a font
     * missing its own header, and the renderer quietly fell back to the system
     * face while the settings screen cheerfully reported a font was in use.
     *
     * <p>Marks generously before reading, because a mark is only honoured up to
     * the reader's buffer size and four bytes is comfortably inside any of them.
     */
    private fun isFont(input: InputStream): Boolean {
        input.mark(MARK_LIMIT)
        val head = ByteArray(4)
        var read = 0
        try {
            while (read < head.size) {
                val n = input.read(head, read, head.size - read)
                if (n < 0) break
                read += n
            }
        } finally {
            // Unconditional: the caller must get its stream back whether or not
            // this check passed, or a rejected file would corrupt the next read.
            input.reset()
        }
        if (read < head.size) return false
        return when {
            head[0] == 0x00.toByte() && head[1] == 0x01.toByte() &&
                head[2] == 0x00.toByte() && head[3] == 0x00.toByte() -> true          // TrueType
            head[0] == 'O'.code.toByte() && head[1] == 'T'.code.toByte() &&
                head[2] == 'T'.code.toByte() && head[3] == 'O'.code.toByte() -> true  // OpenType/CFF
            head[0] == 't'.code.toByte() && head[1] == 't'.code.toByte() &&
                head[2] == 'c'.code.toByte() && head[3] == 'f'.code.toByte() -> true  // collection
            else -> false
        }
    }

    /** Bytes to reserve for the signature check. Four are read; the rest is slack. */
    private const val MARK_LIMIT = 1024
}
