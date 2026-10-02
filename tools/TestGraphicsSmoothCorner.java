import androidx.graphics.shapes.Cubic;
import io.pocketshare.GraphicsSmoothCorner;

/** Verifies the generated app geometry, including straight-edge curvature continuity. */
public class TestGraphicsSmoothCorner {
    static float[] points(Cubic c) {
        return new float[]{c.getAnchor0X(),c.getAnchor0Y(),c.getControl0X(),c.getControl0Y(),
            c.getControl1X(),c.getControl1Y(),c.getAnchor1X(),c.getAnchor1Y()};
    }
    static double[] tangent(float[] p, boolean end) {
        int a=end?4:0,b=end?6:2;
        return new double[]{3*(p[b]-p[a]),3*(p[b+1]-p[a+1])};
    }
    static double curvature(float[] p, boolean end) {
        var v=tangent(p,end);int a=end?2:0;
        double ax=6*(p[a+4]-2*p[a+2]+p[a]),ay=6*(p[a+5]-2*p[a+3]+p[a+1]);
        return (v[0]*ay-v[1]*ax)/Math.pow(v[0]*v[0]+v[1]*v[1],1.5);
    }
    static void near(double a,double b) {
        if(Math.abs(a-b)>0.0001)throw new AssertionError(a+" != "+b);
    }
    public static void main(String[] args) {
        var shape = GraphicsSmoothCorner.INSTANCE;
        near(GraphicsSmoothCorner.SMOOTHING, .6);
        for (float available : new float[]{20,24,28,32,38.4f,56,160}) {
            float radius = Math.min(24, available);
            float smoothing = shape.smoothingFor(radius, available);
            near(smoothing, Math.min(.6, Math.max(0, available/radius-1)));
            var curves = shape.create(smoothing);
            if(curves.isEmpty()) throw new AssertionError("Missing corner");
            var first=points(curves.get(0));var last=points(curves.get(curves.size()-1));
            near(first[0],0);near(first[1],1+smoothing);
            near(last[6],1+smoothing);near(last[7],0);
            if(smoothing>0) {
                near(curvature(first,false),0);near(curvature(last,true),0);
            }
            for(int i=1;i<curves.size();i++) {
                var prev=points(curves.get(i-1));var next=points(curves.get(i));
                near(prev[6],next[0]);near(prev[7],next[1]);
                var a=tangent(prev,true);var b=tangent(next,false);
                near((a[0]*b[1]-a[1]*b[0]) / (Math.hypot(a[0],a[1])*Math.hypot(b[0],b[1])),0);
            }
            for(var c:curves)for(float x:points(c))
                if(x<-.00001||x*radius>available+.0001)throw new AssertionError("Corner exceeds bounds");
        }
        System.out.println("PASS: radius 24 / smoothing 60%, joined tangents, and short-control bounds without radius shrinkage.");
    }
}