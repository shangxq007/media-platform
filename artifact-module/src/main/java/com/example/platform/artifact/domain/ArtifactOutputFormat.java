package com.example.platform.artifact.domain;

/** Accepted Render output representation, not source-media structural/probe authority.
 * MIME is supplied by the Storage write and persisted there; signature checks prevent
 * relabeling supported containers. They do not establish full codec/playback validity. */
public enum ArtifactOutputFormat {
    MP4("video/mp4","mp4",ArtifactMediaType.VIDEO), WEBM("video/webm","webm",ArtifactMediaType.VIDEO),
    QUICKTIME("video/quicktime","mov",ArtifactMediaType.VIDEO), WAV("audio/wav","wav",ArtifactMediaType.AUDIO),
    MP3("audio/mpeg","mp3",ArtifactMediaType.AUDIO), FLAC("audio/flac","flac",ArtifactMediaType.AUDIO),
    PNG("image/png","png",ArtifactMediaType.IMAGE), JPEG("image/jpeg","jpg",ArtifactMediaType.IMAGE);
    private final String mime,extension;private final ArtifactMediaType mediaType;
    ArtifactOutputFormat(String mime,String extension,ArtifactMediaType mediaType){this.mime=mime;this.extension=extension;this.mediaType=mediaType;}
    public String mimeType(){return mime;}
    public String fileName(){return "output."+extension;}
    public ArtifactMediaType mediaType(){return mediaType;}
    public static ArtifactOutputFormat require(String mime) {
        for(var format:values())if(format.mime.equals(mime))return format;
        throw new IllegalArgumentException("Unsupported accepted output MIME: "+mime);
    }
    public void validate(byte[] bytes) {
        java.util.Objects.requireNonNull(bytes);
        boolean valid=switch(this) {
            case MP4 -> isoBrand(bytes,false);
            case QUICKTIME -> isoBrand(bytes,true);
            case WEBM -> prefix(bytes,0x1a,0x45,0xdf,0xa3) && new String(bytes,0,Math.min(bytes.length,4096),java.nio.charset.StandardCharsets.ISO_8859_1).contains("webm");
            case WAV -> (ascii(bytes,0,"RIFF")||ascii(bytes,0,"RF64")) && ascii(bytes,8,"WAVE");
            case MP3 -> ascii(bytes,0,"ID3") || (bytes.length>=4 && (bytes[0]&255)==255 && (bytes[1]&224)==224 && (bytes[1]&6)!=0);
            case FLAC -> ascii(bytes,0,"fLaC");
            case PNG -> prefix(bytes,137,80,78,71,13,10,26,10);
            case JPEG -> prefix(bytes,255,216,255);
        };
        if(!valid)throw new IllegalArgumentException("Accepted output bytes do not match MIME "+mime);
    }
    private static boolean isoBrand(byte[] bytes,boolean quicktime) {
        if(bytes.length<16 || !ascii(bytes,4,"ftyp"))return false;
        int size=java.nio.ByteBuffer.wrap(bytes,0,4).getInt();
        if(size<16 || size>bytes.length)return false;
        boolean qt=ascii(bytes,8,"qt  "),mp4=false;
        var brands=java.util.Set.of("isom","iso2","iso3","iso4","iso5","iso6","mp41","mp42","avc1","dash","M4V ");
        for(int offset=8;offset+4<=size;offset+=4) {
            if(offset==12)continue;
            String brand=new String(bytes,offset,4,java.nio.charset.StandardCharsets.US_ASCII);
            qt|=brand.equals("qt  ");mp4|=brands.contains(brand);
        }
        return quicktime?qt:mp4&&!qt;
    }
    private static boolean ascii(byte[] bytes,int offset,String value) {
        if(bytes.length<offset+value.length())return false;
        for(int i=0;i<value.length();i++)if((bytes[offset+i]&255)!=value.charAt(i))return false;
        return true;
    }
    private static boolean prefix(byte[] bytes,int... expected) {
        if(bytes.length<expected.length)return false;
        for(int i=0;i<expected.length;i++)if((bytes[i]&255)!=expected[i])return false;
        return true;
    }
}
