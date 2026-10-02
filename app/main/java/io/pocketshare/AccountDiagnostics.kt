package io.pocketshare

/** Sanitize before truncation so a truncated credential can never escape redaction. */
object AccountDiagnostics {
    fun sanitize(output: String, password: String): String {
        var text = if (password.isEmpty()) output else output.replace(password, "[REDACTED]")
        text = text.replace(Regex("(?i)\\b[0-9a-f]{32,}\\b"), "[REDACTED HASH]")
        return text.filter { it == '\n' || it == '\t' || !it.isISOControl() }
            .trim().takeLast(6000).ifBlank { "No subprocess diagnostic output" }
    }
}
