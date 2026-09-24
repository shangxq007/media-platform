package com.example.platform.artifact.domain.typed;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record VersionRange(String expression) {
    private static final Pattern P = Pattern.compile("^(?:>=([0-9]+(?:\\.[0-9]+)*))?(?:<=([0-9]+(?:\\.[0-9]+)*))?$");
    public VersionRange { if (expression == null || expression.isBlank() || !P.matcher(expression.trim()).matches()) throw new IllegalArgumentException("malformed version range"); }
    public boolean accepts(String version) {
        if (version == null || version.isBlank()) return false; Matcher m=P.matcher(expression.trim()); if(!m.matches()) return false;
        return (m.group(1)==null || compare(version,m.group(1))>=0) && (m.group(2)==null || compare(version,m.group(2))<=0);
    }
    private static int compare(String a,String b){String[] x=a.split("\\."),y=b.split("\\."); for(int i=0;i<Math.max(x.length,y.length);i++){int c=Integer.compare(i<x.length?Integer.parseInt(x[i]):0,i<y.length?Integer.parseInt(y[i]):0);if(c!=0)return c;}return 0;}
}
