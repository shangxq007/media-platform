package com.example.platform.timeline;
import com.example.platform.timeline.api.serialization.TimelineFrameRateCodec;
import com.example.platform.render.domain.interchange.RenderFrameRateCodec;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.time.FrameRate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class RenderTimeBoundaryTest {
 static Set<String> violations(Path root)throws Exception {
  Set<String> found=new HashSet<>();try(var files=Files.walk(root)){
   for(Path p:files.filter(x->x.toString().contains("/src/main/")&&x.toString().endsWith(".java")).toList()){
    String path=p.toString().replace('\\','/'),s=Files.readString(p);
    if(path.contains("shared-kernel/")&&Set.of("CanonicalFrameRateCodec.java","RenderInitiator.java","RationalTime.java").contains(p.getFileName().toString()))found.add(path);
    if(s.contains("com.example.platform.shared.time.CanonicalFrameRateCodec")||s.contains("com.example.platform.shared.time.RationalTime")||s.contains("com.example.platform.shared.events.RenderInitiator"))found.add(path);
    if(p.getFileName().toString().equals("FrameRate.java")&&!path.contains("shared-kernel/"))found.add(path);
    if(path.contains("shared-kernel/")&&Set.of("FrameRate.java","MediaTime.java","CanonicalCommandFingerprint.java").contains(p.getFileName().toString())&&s.contains("com.fasterxml.jackson"))found.add(path);
   }
  }return found;
 }
 @Test void retainedValuesAreFrameworkNeutralAndOldDefinitionsAreUnreachable()throws Exception {
  Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("AGENTS.md")))root=root.getParent();assertEquals(Set.of(),violations(root));
  for(String name:List.of("com.example.platform.shared.time.CanonicalFrameRateCodec","com.example.platform.shared.time.RationalTime","com.example.platform.shared.events.RenderInitiator"))assertThrows(ClassNotFoundException.class,()->Class.forName(name));
  assertEquals("com.example.platform.shared.time",FrameRate.class.getPackageName());
  assertEquals("com.example.platform.render.ir",com.example.platform.render.ir.RationalTime.class.getPackageName());
 }
 @Test void guardsDetectReintroducedSharedInfrastructure(@TempDir Path root)throws Exception {
  Map<String,String> cases=Map.of("render-module/src/main/java/FrameRate.java","record FrameRate(double fps){}",
   "shared-kernel/src/main/java/CanonicalFrameRateCodec.java","class CanonicalFrameRateCodec{}",
   "render-module/src/main/java/Bad.java","import com.example.platform.shared.events.RenderInitiator;",
   "shared-kernel/src/main/java/FrameRate.java","import com.fasterxml.jackson.databind.JsonNode;");
  for(var e:cases.entrySet()){Path p=root.resolve(e.getKey());Files.createDirectories(p.getParent());Files.writeString(p,e.getValue());}assertEquals(cases.size(),violations(root).size());
 }
 @Test void separateAdaptersEnforceTheSameExactValueDomainAndExplicitMissingPolicy()throws Exception {
  var mapper=new ObjectMapper();
  for(String json:List.of("{\"num\":30000,\"den\":1001}","{\"num\":60000,\"den\":2002}","{\"num\":24,\"den\":1}","{\"num\":2147483647,\"den\":1}")){
   var node=mapper.readTree(json);assertEquals(TimelineFrameRateCodec.parse(node,false),RenderFrameRateCodec.parse(node,false));
  }
  assertEquals(FrameRate.of(30000,1001),TimelineFrameRateCodec.parse(mapper.readTree("{\"num\":60000,\"den\":2002}"),false));
  for(String bad:List.of("{}","[]","30","\"30\"","{\"num\":30}","{\"num\":30,\"den\":0}","{\"num\":-30,\"den\":1}","{\"num\":30.0,\"den\":1}","{\"num\":2147483648,\"den\":1}","{\"num\":9223372036854775808,\"den\":1}")){
   var node=mapper.readTree(bad);assertThrows(TimelineFrameRateCodec.InvalidCanonicalRateException.class,()->TimelineFrameRateCodec.parse(node,true));assertThrows(RenderFrameRateCodec.InvalidCanonicalRateException.class,()->RenderFrameRateCodec.parse(node,true));
  }
  assertEquals(FrameRate.of(30,1),TimelineFrameRateCodec.parse(mapper.missingNode(),true));assertEquals(FrameRate.of(30,1),RenderFrameRateCodec.parse(mapper.missingNode(),true));
  assertThrows(TimelineFrameRateCodec.InvalidCanonicalRateException.class,()->TimelineFrameRateCodec.parse(mapper.missingNode(),false));assertThrows(RenderFrameRateCodec.InvalidCanonicalRateException.class,()->RenderFrameRateCodec.parse(mapper.missingNode(),false));
 }
 @Test void initiatorWireContractRoundTripsWithoutInventingSystemOrAuthorityData()throws Exception {
  var mapper=new ObjectMapper();
  String principal="{\"kind\":\"PRINCIPAL\",\"actorId\":\"actor\",\"actorType\":\"USER\",\"tenantId\":\"tenant\"}";
  var value=RenderInitiator.restore(ActorType.USER,"actor","tenant");
  assertEquals(mapper.readTree(principal),mapper.valueToTree(value));assertEquals(value,mapper.readValue(principal,RenderInitiator.class));
  var system=RenderInitiator.restore(ActorType.SYSTEM,"scheduler","tenant");assertEquals(system,mapper.readValue(mapper.writeValueAsString(system),RenderInitiator.class));
  assertThrows(Exception.class,()->mapper.readValue(principal.replace("PRINCIPAL","SYSTEM"),RenderInitiator.class));
  assertThrows(Exception.class,()->mapper.readValue(principal.replace("\"tenant\"","\"\""),RenderInitiator.class));
  assertThrows(NullPointerException.class,()->RenderInitiator.from(null));
 }
 @Test void invalidRateCannotBecomeAnAbsentOptionalSegmentPlan() {
  var planner=new com.example.platform.render.app.timeline.SegmentTimelinePlanner();
  String json="{\"schemaVersion\":\"1.0\",\"id\":\"timeline\",\"project\":{\"frameRate\":{\"num\":30,\"den\":0},\"duration\":{\"frame\":60}},\"renderGraph\":{\"segmentPolicy\":{\"enabled\":true,\"segmentDuration\":{\"frame\":30}}}}";
  assertThrows(TimelineFrameRateCodec.InvalidCanonicalRateException.class,()->planner.planFromTimelineJson(json));
  assertTrue(planner.planFromTimelineJson(json.replace("\"den\":0","\"den\":1")).isPresent());
 }
}
