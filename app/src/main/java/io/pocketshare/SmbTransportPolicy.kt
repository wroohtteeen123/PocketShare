package io.pocketshare

object SmbTransportPolicy {
    fun config(guest: Boolean, requireEncryption: Boolean): String {
        require(!guest || !requireEncryption) { "Guest sessions cannot require SMB encryption" }
        return "server min protocol = ${if (requireEncryption) "SMB3_00" else "SMB2_02"}\n" +
            "server max protocol = SMB3_11\n" +
            "server smb encrypt = ${if (requireEncryption) "required" else "if_required"}"
    }
}
