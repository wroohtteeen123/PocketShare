import io.pocketshare.SmbTransportPolicy;

public class TestSmbTransportPolicy {
    public static void main(String[] args) {
        String encrypted = SmbTransportPolicy.INSTANCE.config(false, true);
        if (!encrypted.contains("server min protocol = SMB3_00") || !encrypted.contains("server smb encrypt = required")) throw new AssertionError("Encryption must be enforced");
        for (boolean guest : new boolean[]{false, true}) {
            String optional = SmbTransportPolicy.INSTANCE.config(guest, false);
            if (!optional.contains("server min protocol = SMB2_02") || !optional.contains("server smb encrypt = if_required")) throw new AssertionError("Optional mode changed");
        }
        try { SmbTransportPolicy.INSTANCE.config(true, true); throw new AssertionError("Guest encryption must be rejected, never downgraded"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PASS: required encryption, optional authenticated/guest modes, and guest encryption rejection.");
    }
}
