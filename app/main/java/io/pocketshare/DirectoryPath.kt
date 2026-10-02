package io.pocketshare

object DirectoryPath {
    fun shellQuote(path: String): String = "'" + path.replace("'", "'\"'\"'") + "'"
    // Samba expands percent substitutions; reject these and config line breaks.
    fun isValid(path: String): Boolean = path.startsWith('/') &&
        path.none { it.isISOControl() || it == '%' } &&
        path.split('/').none { it == "." || it == ".." }
}
