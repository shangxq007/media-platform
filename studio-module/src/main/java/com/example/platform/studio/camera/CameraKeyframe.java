package com.example.platform.studio.camera;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.studio.serialization.CanonicalJson;
import java.util.Map;
public record CameraKeyframe(MediaTime time,CameraPose pose){public CameraKeyframe{if(time==null||pose==null)throw new IllegalArgumentException("keyframe fields required");}public String canonicalJson(){return CanonicalJson.object(Map.of("pose",pose.canonicalJson(),"time",CanonicalJson.object(Map.of("ticks",Long.toString(time.ticks()),"timeScale",Long.toString(time.timeScale())))));}}
