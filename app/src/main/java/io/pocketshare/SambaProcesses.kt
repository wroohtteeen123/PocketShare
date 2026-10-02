package io.pocketshare

import android.content.Context
import java.io.File

/** Match executable paths, never global process names or untrusted PID files. */
object SambaProcesses {
    val components = linkedMapOf(
        "bin/smbd" to "文件共享 · smbd",
        "libexec/samba/samba-dcerpcd" to "RPC 调度 · samba-dcerpcd",
        "libexec/samba/rpcd_classic" to "共享列表 · rpcd_classic",
        "libexec/samba/rpcd_lsad" to "账户查询 · rpcd_lsad",
        "libexec/samba/rpcd_winreg" to "注册表 · rpcd_winreg",
        "libexec/samba/rpcd_epmapper" to "端点映射 · rpcd_epmapper",
        "libexec/samba/rpcd_fsrvp" to "快照 · rpcd_fsrvp",
        "libexec/samba/rpcd_mdssvc" to "搜索 · rpcd_mdssvc",
        "libexec/samba/rpcd_spoolss" to "打印 · rpcd_spoolss"
    )
    private fun quote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"
    private fun scan(context: Context, component: String?, kill: Boolean): RootAccess.CommandResult {
        require(component == null || components.containsKey(component))
        val base = File(context.filesDir, "native-samba").canonicalPath
        val choices = (component?.let { listOf(it) } ?: components.keys.toList()).joinToString("\n") { key ->
            val exact = quote("$base/$key")
            val deleted = quote("$base/$key (deleted)")
            val action = if (kill) "kill -9 \"\$pid\" || exit 1" else "printf '%s\\t%s\\n' ${quote(key)} \"\$pid\""
            "$exact|$deleted) $action ;;"
        }
        return RootAccess.execute("for proc in /proc/[0-9]*; do " +
            "exe=\$(readlink \"\$proc/exe\" 2>/dev/null) || continue; pid=\${proc##*/}; " +
            "case \"\$exe\" in $choices esac; done")
    }
    fun snapshot(context: Context): Map<String, List<String>> {
        val result = scan(context, null, false)
        check(result.succeeded) { "无法读取进程，请检查 root 授权" }
        return result.output.lineSequence().map { it.split('\t') }
            .filter { it.size == 2 && components.containsKey(it[0]) }
            .groupBy({ it[0] }, { it[1] })
    }
    fun kill(context: Context, component: String? = null) = scan(context, component, true)
}
