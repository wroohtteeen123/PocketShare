import androidx.graphics.shapes.*;
import io.pocketshare.OfficialCloverShape;
import java.util.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Independent Cartesian expansion of AndroidX clover4's mirrored seed points. */
public class TestOfficialClover {
    public static void main(String[] args) throws Exception {
        float[] vertices = {
            .5f,.074f, .725f,-.099f, 1.099f,.275f,
            .926f,.5f, 1.099f,.725f, .725f,1.099f,
            .5f,.926f, .275f,1.099f, -.099f,.725f,
            .074f,.5f, -.099f,.275f, .275f,-.099f
        };
        var rounding = new ArrayList<CornerRounding>();
        for (int i=0; i<12; i++) rounding.add(new CornerRounding(i%3==0 ? 0f : .476f, 0f));
        var reference = RoundedPolygonKt.RoundedPolygon(vertices, CornerRounding.Unrounded, rounding, .5f, .5f).normalized();
        var actual = OfficialCloverShape.INSTANCE.getPolygon();
        if (actual.getCubics().size()!=reference.getCubics().size()) throw new AssertionError("Cubic count differs");
        float max = 0;
        Path2D.Float path = new Path2D.Float();
        var first = actual.getCubics().get(0);
        path.moveTo(first.getAnchor0X(), first.getAnchor0Y());
        for (int i=0;i<actual.getCubics().size();i++) {
            var c = actual.getCubics().get(i);
            var ref = reference.getCubics().get(i);
            float[] x={c.getAnchor0X(),c.getAnchor0Y(),c.getControl0X(),c.getControl0Y(),c.getControl1X(),c.getControl1Y(),c.getAnchor1X(),c.getAnchor1Y()};
            float[] y={ref.getAnchor0X(),ref.getAnchor0Y(),ref.getControl0X(),ref.getControl0Y(),ref.getControl1X(),ref.getControl1Y(),ref.getAnchor1X(),ref.getAnchor1Y()};
            for (int k=0;k<8;k++) max=Math.max(max,Math.abs(x[k]-y[k]));
            path.curveTo(x[2],x[3],x[4],x[5],x[6],x[7]);
        }
        if (max>0.00001f) throw new AssertionError("Geometry differs: "+max);
        path.closePath();
        var image = new BufferedImage(320,320,BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xF8F9FC));g.fillRect(0,0,320,320);
        g.translate(32,32);g.scale(256,256);g.setColor(new Color(0x0593FA));g.fill(path);g.dispose();
        ImageIO.write(image,"png",new File(args[0]));
        System.out.println("PASS: official Clover4Leaf geometry; "+actual.getCubics().size()+" cubics; maximum normalized coordinate error "+max);
    }
}
