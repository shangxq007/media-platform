package com.example.platform.studio.camera;
import com.example.platform.studio.serialization.CanonicalJson;
import java.math.*;
import java.util.Map;
public final class Quaternion{
    private static final MathContext MC=new MathContext(34,RoundingMode.HALF_EVEN);
    private static final int CANONICAL_SCALE=12;
    private static final BigDecimal NORMALIZATION_TOLERANCE=new BigDecimal("0.0000000001");
    public static final Quaternion IDENTITY=new Quaternion(DecimalValue.of("0"),DecimalValue.of("0"),DecimalValue.of("0"),DecimalValue.of("1"));
    private final DecimalValue x,y,z,w;
    public Quaternion(DecimalValue x,DecimalValue y,DecimalValue z,DecimalValue w){if(x==null||y==null||z==null||w==null)throw new IllegalArgumentException("quaternion components required");
        var norm2=x.value().multiply(x.value(),MC).add(y.value().multiply(y.value(),MC),MC).add(z.value().multiply(z.value(),MC),MC).add(w.value().multiply(w.value(),MC),MC);
        if(norm2.signum()==0)throw new IllegalArgumentException("zero quaternion forbidden");var norm=norm2.sqrt(MC);var nx=x.value().divide(norm,MC);var ny=y.value().divide(norm,MC);var nz=z.value().divide(norm,MC);var nw=w.value().divide(norm,MC);
        boolean flip=nw.signum()<0||(nw.signum()==0&&(nx.signum()<0||(nx.signum()==0&&(ny.signum()<0||(ny.signum()==0&&nz.signum()<0)))));if(flip){nx=nx.negate();ny=ny.negate();nz=nz.negate();nw=nw.negate();}
        nx=canonicalRound(nx);ny=canonicalRound(ny);nz=canonicalRound(nz);nw=canonicalRound(nw);
        var roundedNorm2=nx.multiply(nx).add(ny.multiply(ny)).add(nz.multiply(nz)).add(nw.multiply(nw));
        if(roundedNorm2.signum()==0||roundedNorm2.subtract(BigDecimal.ONE).abs().compareTo(NORMALIZATION_TOLERANCE)>0)
            throw new IllegalArgumentException("quaternion normalization is outside schema-v1 tolerance");
        this.x=DecimalValue.from(nx);this.y=DecimalValue.from(ny);this.z=DecimalValue.from(nz);this.w=DecimalValue.from(nw);}
    private static BigDecimal canonicalRound(BigDecimal value){var rounded=value.setScale(CANONICAL_SCALE,RoundingMode.HALF_EVEN).stripTrailingZeros();return rounded.signum()==0?BigDecimal.ZERO:rounded;}
    public DecimalValue x(){return x;}public DecimalValue y(){return y;}public DecimalValue z(){return z;}public DecimalValue w(){return w;}
    public String canonicalJson(){return CanonicalJson.object(Map.of("w",CanonicalJson.quote(w.canonical()),"x",CanonicalJson.quote(x.canonical()),"y",CanonicalJson.quote(y.canonical()),"z",CanonicalJson.quote(z.canonical())));}
}
