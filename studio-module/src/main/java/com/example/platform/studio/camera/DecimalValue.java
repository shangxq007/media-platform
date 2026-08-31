package com.example.platform.studio.camera;

import java.math.BigDecimal;

public final class DecimalValue implements Comparable<DecimalValue> {
    private static final BigDecimal MAX=new BigDecimal("1000000000");
    private final BigDecimal value;
    private DecimalValue(BigDecimal value){this.value=canonicalize(value);}
    public static DecimalValue of(String text){
        if(text==null||text.isBlank()||text.indexOf('e')>=0||text.indexOf('E')>=0)throw new IllegalArgumentException("finite plain decimal required");
        try{return new DecimalValue(new BigDecimal(text));}catch(NumberFormatException e){throw new IllegalArgumentException("finite plain decimal required",e);}
    }
    static DecimalValue from(BigDecimal value){return new DecimalValue(value);}
    private static BigDecimal canonicalize(BigDecimal input){if(input==null)throw new IllegalArgumentException("decimal required");var v=input.stripTrailingZeros();if(v.scale()<0)v=v.setScale(0);if(v.scale()>12||v.abs().compareTo(MAX)>0)throw new IllegalArgumentException("decimal range or scale invalid");if(v.signum()==0)return BigDecimal.ZERO;return v;}
    BigDecimal value(){return value;} public String canonical(){return value.toPlainString();}
    public int compareTo(DecimalValue other){return value.compareTo(other.value);}public boolean isPositive(){return value.signum()>0;}
    @Override public boolean equals(Object o){return o instanceof DecimalValue d&&value.compareTo(d.value)==0;}@Override public int hashCode(){return value.stripTrailingZeros().hashCode();}@Override public String toString(){return canonical();}
}
