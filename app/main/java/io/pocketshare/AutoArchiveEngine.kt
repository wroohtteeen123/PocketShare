package io.pocketshare

import java.io.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.*
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

/** Streaming compression; originals are never changed or removed. */
object AutoArchiveEngine {
    enum class Format(val extension: String) { ZIP("zip"), XZ("tar.xz"), GZIP("tar.gz") }
    data class Entry(val file: File, val name: String, val size: Long, val modified: Long, val directory: Boolean)
    data class Snapshot(val entries: List<Entry>, val signature: String)
    fun separate(input: File, output: File): Boolean {
        val a = input.canonicalFile.toPath(); val b = output.canonicalFile.toPath()
        return !a.startsWith(b) && !b.startsWith(a)
    }
    private fun checkCanceled() { if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Canceled") }
    fun snapshot(item: File): Snapshot {
        val entries = ArrayList<Entry>()
        val root = requireNotNull(item.absoluteFile.parentFile).canonicalFile.toPath()
        fun visit(file: File, name: String, depth: Int) {
            checkCanceled()
            check(depth < 128 && entries.size < 100000) { "Too many entries or directory nesting too deep" }
            val attr = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            check(!attr.isSymbolicLink && (attr.isDirectory || attr.isRegularFile)) { "Symbolic links and special files are not supported: $name" }
            check(file.canonicalFile.toPath().startsWith(root)) { "Path escaped source directory" }
            entries.add(Entry(file, name, if (attr.isDirectory) 0 else attr.size(), attr.lastModifiedTime().toMillis(), attr.isDirectory))
            if (attr.isDirectory) {
                val children = file.listFiles() ?: error("Cannot read directory: $name")
                children.sortedBy { it.name }.forEach { visit(it, "$name/${it.name}", depth + 1) }
            }
        }
        visit(item, item.name, 0)
        val digest = MessageDigest.getInstance("SHA-256")
        entries.forEach { digest.update("${it.name}\u0000${it.size}\u0000${it.modified}\u0000${it.directory}\u0000".toByteArray(Charsets.UTF_8)) }
        return Snapshot(entries, digest.digest().joinToString("") { "%02x".format(it) })
    }
    fun archive(item: File, output: File, format: Format, level: Int, expected: String): File {
        require(level in 1..6)
        check(separate(item, output)) { "Overlapping directories" }
        val before = snapshot(item)
        check(before.signature == expected) { "Source changed; retry after it settles" }
        val target = File(output, "${item.name.take(80)}-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.${format.extension}")
        val partial = File.createTempFile(".pocketshare-", ".partial", output)
        try {
            FileOutputStream(partial).buffered(128 * 1024).use { raw ->
                if (format == Format.ZIP) {
                    ZipOutputStream(raw).use { zip ->
                        zip.setLevel(level)
                        before.entries.forEach { entry ->
                            checkCanceled()
                            zip.putNextEntry(ZipEntry(entry.name + if (entry.directory) "/" else "").apply { time = entry.modified })
                            if (!entry.directory) copy(entry, zip)
                            zip.closeEntry()
                        }
                    }
                } else {
                    val compressed = if (format == Format.XZ) {
                        val options = LZMA2Options(level)
                        val runtime = Runtime.getRuntime()
                        check(options.encoderMemoryUsage * 1024L + 24 * 1024 * 1024 < runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) {
                            "Not enough memory; select a lower compression level"
                        }
                        XZOutputStream(raw, options)
                    }
                        else object : GZIPOutputStream(raw, 128 * 1024) { init { def.setLevel(level) } }
                    TarArchiveOutputStream(compressed, "UTF-8").use { tar ->
                        tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                        tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                        tar.setAddPaxHeadersForNonAsciiNames(true)
                        before.entries.forEach { entry ->
                            checkCanceled()
                            tar.putArchiveEntry(TarArchiveEntry(entry.name + if (entry.directory) "/" else "").apply {
                                size = entry.size; setModTime(entry.modified)
                            })
                            if (!entry.directory) copy(entry, tar)
                            tar.closeArchiveEntry()
                        }
                    }
                }
            }
            checkCanceled()
            check(snapshot(item).signature == expected) { "Source changed while compressing; retry later" }
            check(!target.exists() && partial.renameTo(target)) { "Cannot publish archive" }
            return target
        } finally {
            // Only our uniquely-created incomplete output is removed.
            if (partial.exists()) partial.delete()
        }
    }
    private fun copy(entry: Entry, output: OutputStream) {
        check(!Files.isSymbolicLink(entry.file.toPath())) { "Source became a symbolic link" }
        Files.newInputStream(entry.file.toPath(), NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(128 * 1024)
            var count = 0L
            while (true) {
                checkCanceled()
                val n = input.read(buffer)
                if (n < 0) break
                count += n
                check(count <= entry.size) { "Source grew during compression" }
                output.write(buffer, 0, n)
            }
            check(count == entry.size) { "Source shrank during compression" }
        }
    }
}
