import io.pocketshare.StorageDirectories;
import java.util.List;

public class TestStorageDirectories {
    public static void main(String[] args) {
        StorageDirectories d = StorageDirectories.INSTANCE;
        if (!d.accepts("/storage") || !d.accepts("/storage/emulated/0") ||
            d.accepts("/storage-other") || d.accepts("/storage/../data")) throw new AssertionError("bounds");
        if (!d.parent("/storage").equals("/storage") || !d.parent("/storage/emulated").equals("/storage")) throw new AssertionError("parent");
        List<String> paths = d.parse("/storage", "OK\0/storage/中文 空格\0/storage/a'b\0/storage/.hidden\0/storage/x%y\0/data\0/storage/a/b\0");
        if (paths.size() != 3 || !paths.contains("/storage/a'b") || !paths.contains("/storage/中文 空格")) throw new AssertionError(paths);
        if (!d.command("/storage/a'b").contains("'\"'\"'")) throw new AssertionError("quoting");
        try { d.parse("/storage", "permission denied"); throw new AssertionError("error accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PASS: storage bounds, parent, NUL parsing, Unicode, spaces, quotes and failed output");
    }
}
