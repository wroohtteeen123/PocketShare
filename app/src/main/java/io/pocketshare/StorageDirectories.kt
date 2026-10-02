package io.pocketshare

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** NUL-separated paths preserve spaces, quotes and Unicode in folder names. */
object StorageDirectories {
    const val ROOT = "/storage"
    fun accepts(path: String) = DirectoryPath.isValid(path) && (path == ROOT || path.startsWith("$ROOT/"))
    fun parent(path: String) = if (path == ROOT) ROOT else path.substringBeforeLast('/').takeIf(::accepts) ?: ROOT
    fun command(path: String): String {
        require(accepts(path))
        return "p=${DirectoryPath.shellQuote(path)}; cd \"\$p\" && [ -r . ] && [ -x . ] || exit 1; " +
            "printf 'OK\\0'; for f in \"\$p\"/* \"\$p\"/.[!.]* \"\$p\"/..?*; do " +
            "if [ -d \"\$f\" ]; then printf '%s\\0' \"\$f\"; fi; done"
    }
    fun parse(path: String, output: String): List<String> {
        require(output.startsWith("OK\u0000"))
        return output.substring(3).split('\u0000').filter { accepts(it) && parent(it) == path }
            .distinct().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.substringAfterLast('/') })
    }
    fun list(path: String): List<String> {
        val process = ProcessBuilder("su", "-c", command(path)).redirectErrorStream(true).start()
        val reader = Executors.newSingleThreadExecutor()
        try {
            val output = reader.submit<String> { process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
            check(process.waitFor(30, TimeUnit.SECONDS)) { "Root request timed out" }
            check(process.exitValue() == 0) { "Directory unavailable" }
            return parse(path, output.get(2, TimeUnit.SECONDS))
        } finally {
            process.destroyForcibly()
            reader.shutdownNow()
        }
    }
}
