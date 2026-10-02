import io.pocketshare.DirectoryPath;

public class TestDirectoryPath {
    public static void main(String[] args) {
        String[] valid = {"/storage/emulated/0/Download", "/data/media/0", "/storage/My files/照片", "/storage/O'Brien", "/"};
        String[] invalid = {"", "Download", "content://folder", "/a\n[global]", "/a\rb", "/a\u0000b", "/a/%U", "/a/../b", "/a/./b"};
        for (String value : valid) if (!DirectoryPath.INSTANCE.isValid(value)) throw new AssertionError("Rejected valid path: " + value);
        for (String value : invalid) if (DirectoryPath.INSTANCE.isValid(value)) throw new AssertionError("Accepted invalid path: " + value);
        if (!"'/storage/O'\"'\"'Brien'".equals(DirectoryPath.INSTANCE.shellQuote("/storage/O'Brien"))) throw new AssertionError("Shell quoting failed");
        System.out.println("PASS: 14 manual directory path cases and apostrophe shell quoting.");
    }
}
