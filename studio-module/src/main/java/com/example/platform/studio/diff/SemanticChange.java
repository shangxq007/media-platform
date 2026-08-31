package com.example.platform.studio.diff;
import java.util.Comparator;import java.util.Optional;
public record SemanticChange(ChangeKind kind,TypedPath path,Optional<String>before,Optional<String>after){
public static final Comparator<SemanticChange>CANONICAL_ORDER=Comparator.comparing((SemanticChange c)->c.path.canonical()).thenComparing(c->c.kind.name()).thenComparing(c->c.before.orElse("")).thenComparing(c->c.after.orElse(""));
public SemanticChange{if(kind==null||path==null||before==null||after==null)throw new IllegalArgumentException("semantic change fields required");}
public enum ChangeKind{ADDED,REMOVED,REORDERED,REPINNED,FIELD_CHANGED,PARENT_CHANGED,TRANSFORM_CHANGED}
public static SemanticChange changed(ChangeKind kind,TypedPath path,String before,String after){return new SemanticChange(kind,path,Optional.ofNullable(before),Optional.ofNullable(after));}}
