package io.pocketshare

import java.io.BufferedReader
import java.io.InputStreamReader

/** Root is used only for the local SMB listener and TCP 445 redirect. */
object RootAccess {
    private const val SMB_PORT = 445
    data class CommandResult(val succeeded: Boolean, val output: String, val exitCode: Int? = null)

    fun isAvailable() = execute("id").output.contains("uid=0")

    fun enableStandardPort(localPort: Int): Boolean {
        val rule = "-p tcp --dport $SMB_PORT -j REDIRECT --to-ports $localPort"
        execute("iptables -t nat -D PREROUTING $rule")
        return execute("iptables -t nat -A PREROUTING $rule").succeeded
    }

    fun disableStandardPort(localPort: Int) {
        val rule = "-p tcp --dport $SMB_PORT -j REDIRECT --to-ports $localPort"
        execute("iptables -t nat -D PREROUTING $rule")
    }

    fun runChecked(command: String) = execute(command).succeeded
    fun runForOutput(command: String) = execute(command).output
    fun fileExists(path: String) = runChecked("test -x '${path.replace("'", "'\\\"'\\\"'")}'")

    fun execute(command: String): CommandResult = try {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readLines().joinToString("\n") }
        val code = process.waitFor()
        CommandResult(code == 0, output.trim(), code)
    } catch (error: Exception) { CommandResult(false, error.message ?: error.javaClass.simpleName) }

    /** Send credentials over stdin, never through su command arguments. */
    fun executeWithInput(command: String, input: String): CommandResult = try {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        // A child that exits early may close stdin; still collect its linker/setup error.
        runCatching { process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(input) } }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        CommandResult(code == 0, output.trim(), code)
    } catch (_: Exception) { CommandResult(false, "Account operation failed") }
}
