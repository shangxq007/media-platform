package com.example.platform.render.api.event;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
final class RenderEventIdentity {
 private RenderEventIdentity() {}
 static String require(String value,String field){if(value==null||value.isBlank()||value.indexOf('\0')>=0)throw new IllegalArgumentException(field+" required");return value;}
 static String key(String type,String tenant,String project,String job,String detail){return type+":"+UUID.nameUUIDFromBytes((tenant+"\0"+project+"\0"+job+"\0"+detail).getBytes(StandardCharsets.UTF_8));}
}
