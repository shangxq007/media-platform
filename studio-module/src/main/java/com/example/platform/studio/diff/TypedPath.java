package com.example.platform.studio.diff;
import java.util.List;
public record TypedPath(List<String>segments){public TypedPath{if(segments==null||segments.isEmpty()||segments.stream().anyMatch(s->s==null||s.isBlank()||s.contains("/")))throw new IllegalArgumentException("typed semantic path segments required");segments=List.copyOf(segments);}public static TypedPath of(String...segments){return new TypedPath(List.of(segments));}public String canonical(){return String.join("/",segments);}}
