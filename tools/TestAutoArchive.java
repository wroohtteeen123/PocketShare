import io.pocketshare.AutoArchiveEngine;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.tukaani.xz.XZInputStream;
import org.apache.commons.compress.archivers.tar.*;

public class TestAutoArchive {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("pocketshare-archive-test-");
        File input = Files.createDirectory(root.resolve("source")).toFile();
        File output = Files.createDirectory(root.resolve("output")).toFile();
        Path folder = Files.createDirectory(input.toPath().resolve("中文 folder"));
        Files.createDirectory(folder.resolve("empty"));
        byte[] bytes = new byte[8193]; new Random(42).nextBytes(bytes);
        Files.write(folder.resolve("a ' file.bin"), bytes);
        AutoArchiveEngine engine = AutoArchiveEngine.INSTANCE;
        if (engine.separate(input, input) || engine.separate(input, folder.toFile())) throw new AssertionError("overlap");
        String signature = engine.snapshot(folder.toFile()).getSignature();
        for (AutoArchiveEngine.Format format : AutoArchiveEngine.Format.values()) {
            File archive = engine.archive(folder.toFile(), output, format, 1, signature);
            Map<String, byte[]> contents = new HashMap<>();
            if (format == AutoArchiveEngine.Format.ZIP) {
                try (ZipInputStream in = new ZipInputStream(new FileInputStream(archive))) {
                    ZipEntry entry; while ((entry = in.getNextEntry()) != null) contents.put(entry.getName(), in.readAllBytes());
                }
            } else {
                InputStream raw = new FileInputStream(archive);
                try (TarArchiveInputStream in = new TarArchiveInputStream(format == AutoArchiveEngine.Format.XZ ? new XZInputStream(raw) : new GZIPInputStream(raw))) {
                    TarArchiveEntry entry; while ((entry = in.getNextEntry()) != null) contents.put(entry.getName(), in.readAllBytes());
                }
            }
            if (!Arrays.equals(bytes, contents.get("中文 folder/a ' file.bin")) || !contents.containsKey("中文 folder/empty/")) throw new AssertionError(format + ": " + contents.keySet());
        }
        Files.write(folder.resolve("new.txt"), new byte[] {1});
        try { engine.archive(folder.toFile(), output, AutoArchiveEngine.Format.ZIP, 1, signature); throw new AssertionError("stale accepted"); }
        catch (IllegalStateException expected) { }
        if (output.listFiles((d,n)->n.endsWith(".partial")).length != 0) throw new AssertionError("leftovers");
        if (!Arrays.equals(bytes, Files.readAllBytes(folder.resolve("a ' file.bin")))) throw new AssertionError("source changed");
        Thread.currentThread().interrupt();
        try { engine.snapshot(folder.toFile()); throw new AssertionError("cancel ignored"); } catch (Exception expected) { if (!(expected instanceof InterruptedIOException)) throw expected; } finally { Thread.interrupted(); }
        System.out.println("PASS: ZIP/TAR.XZ/TAR.GZ round trips, Unicode, spaces, empty folders, overlap rejection, change detection, cancellation, originals retained. Fixtures: " + root);
    }
}
