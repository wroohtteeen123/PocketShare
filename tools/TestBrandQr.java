import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import io.pocketshare.QrPixels;

/** Decode the actual app raster after compositing its transparent pixels on day/night surfaces. */
public class TestBrandQr {
    public static void main(String[] args) throws Exception {
        String address = "smb://192.168.2.164:4450/Share";
        int[] inks = {0xff0593fa, 0xff0593fa};
        int[][] surfaces = {{0xfff3dde2, 0xfff8f9fc}, {0xff26272e, 0xff17181d}};
        for (int mode = 0; mode < inks.length; mode++) {
            int[] pixels = QrPixels.INSTANCE.encode(address, inks[mode]);
            if (pixels[0] != 0) throw new AssertionError("Quiet zone must be transparent");
            for (int surface : surfaces[mode]) {
                int[] composited = pixels.clone();
                for (int i = 0; i < composited.length; i++) {
                    if (composited[i] == 0) composited[i] = surface;
                    else if (composited[i] != inks[mode]) throw new AssertionError("Unexpected QR color");
                }
                LuminanceSource source = new RGBLuminanceSource(QrPixels.SIZE, QrPixels.SIZE, composited);
                if (mode == 1) source = source.invert();
                String decoded;
                try { decoded = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText(); }
                catch (NotFoundException error) { throw new AssertionError("Cannot decode mode " + mode + " on " + Integer.toHexString(surface), error); }
                if (!address.equals(decoded)) throw new AssertionError("Decode mismatch");
            }
        }
        System.out.println("PASS: app QR raster is transparent, uses only brand ink, and decodes on 4 day/night surfaces (night uses inverted decoding).");
    }
}
