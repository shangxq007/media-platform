package com.example.platform.thumbnail;

import com.example.platform.artifact.domain.*;
import com.example.platform.media.api.Asset;
import com.example.platform.media.api.MediaAssets;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.api.*;
import com.example.platform.storage.domain.BlobStorage;
import io.temporal.activity.Activity;
import io.temporal.spring.boot.ActivityImpl;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ActivityImpl(taskQueues = "media-platform-tasks")
public class ThumbnailActivitiesImpl implements ThumbnailActivities {
    private static final long MAX_INPUT_BYTES = 512L * 1024L * 1024L;
    private final ThumbnailTaskStore tasks; private final MediaAssets assets; private final BlobStorage storage;
    private final StorageOutputPort outputs; private final ArtifactCommitService commits;
    private final Path root; private final String ffmpeg; private final String ffprobe;
    public ThumbnailActivitiesImpl(ThumbnailTaskStore tasks, MediaAssets assets, BlobStorage storage, StorageOutputPort outputs,
            ArtifactCommitService commits, @Value("${app.storage.local-root:./.data/storage}") String root,
            @Value("${thumbnail.ffmpeg-path:ffmpeg}") String ffmpeg,
            @Value("${thumbnail.ffprobe-path:ffprobe}") String ffprobe){this.tasks=tasks;this.assets=assets;this.storage=storage;this.outputs=outputs;this.commits=commits;this.root=Path.of(root).toAbsolutePath().normalize();this.ffmpeg=ffmpeg;this.ffprobe=ffprobe;}
    @Override public String extractAndCommit(String taskId,String tenant,String project) {
        tasks.status(taskId,ThumbnailContracts.Status.RUNNING,null,null);
        var req=tasks.request(tenant,project,taskId).orElseThrow(()->new IllegalArgumentException("thumbnail task not found"));
        assets.requireReadScope(tenant,project); Asset source=assets.findById(tenant,req.sourceAssetId()).filter(a->project.equals(a.projectId())).orElseThrow(()->new IllegalArgumentException("source media unavailable in scope"));
        if(!source.isVideo()) throw new IllegalArgumentException("source media is not a video");
        String bucket="uploads"; byte[] input=storage.get(bucket,source.storageKey()).orElseThrow(()->new IllegalStateException("source object unavailable"));
        if (input.length == 0 || input.length > MAX_INPUT_BYTES) throw new IllegalArgumentException("source exceeds thumbnail input limit");
        Path work=root.resolve("thumbnail-work").resolve(taskId).normalize(); try { Files.createDirectories(work); Path in=work.resolve("input"); Path out=work.resolve("output."+(req.imageFormat().equals("png")?"png":"jpg")); Files.write(in,input);
            double duration = probeDuration(in);
            if (!Double.isFinite(duration) || req.timestampSeconds() > duration) throw new IllegalArgumentException("timestamp outside media duration");
            List<String> args=new ArrayList<>(List.of(ffmpeg,"-hide_banner","-nostdin","-loglevel","error","-ss",Double.toString(req.timestampSeconds()),"-i",in.toString(),"-frames:v","1"));
            if(req.width()!=null) args.addAll(List.of("-vf","scale="+req.width()+":-2")); args.add(out.toString());
            if(req.quality()!=null && req.imageFormat().equals("jpeg")) { int output= args.size()-1; args.add(output, "-q:v"); args.add(output+1, Integer.toString(Math.max(2, Math.min(31, 32-(req.quality()/4))))); }
            Process p=new ProcessBuilder(args).redirectErrorStream(true).start(); if(!p.waitFor(60,TimeUnit.SECONDS)){p.destroyForcibly();throw new IllegalStateException("thumbnail extraction timeout");} if(p.exitValue()!=0||!Files.isRegularFile(out)||Files.size(out)==0) throw new IllegalStateException("thumbnail extraction failed");
            String relative=root.relativize(out).toString().replace(File.separatorChar,'/'); var owner=new StorageOwnershipScope(tenant,project); String key="thumbnail:"+taskId;
            var written=outputs.write(new StorageOutputPort.OutputCommand(owner,new IssuanceIdempotencyKey(key),relative,req.imageFormat().equals("png")?"image/png":"image/jpeg"));
            var issue=written.issuance(); var digest=issue.placement().committedDigest(); ArtifactId aid=new ArtifactId("art-"+UUID.nameUUIDFromBytes((tenant+"\\0"+project+"\\0"+taskId+"\\0"+issue.objectId().value()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var accepted=commits.commit(new ArtifactCommitRequest(aid,tenant,digest,issue.placement().committedLength(),ArtifactMediaType.IMAGE,ArtifactKind.THUMBNAIL,Artifact.CURRENT_SCHEMA_VERSION,issue.objectId(),issue.placement().replicaId(),issue.placement().location().providerId(),ReplicaRole.PRIMARY,issue.placement().location().region(),"thumbnail:"+taskId,List.of(),Instant.now(),Instant.now(),taskId,project));
            tasks.status(taskId,ThumbnailContracts.Status.COMPLETED,accepted.artifact().artifactId().value(),null); return accepted.artifact().artifactId().value();
        } catch (InterruptedException e){Thread.currentThread().interrupt();tasks.status(taskId,ThumbnailContracts.Status.CANCELLED,null,"INTERRUPTED");throw new IllegalStateException("thumbnail interrupted",e);} catch(RuntimeException|IOException e){tasks.status(taskId,ThumbnailContracts.Status.FAILED,null,"EXTRACTION_FAILED");throw new IllegalStateException(e);} finally { try { if(Files.exists(work)) Files.walk(work).sorted(Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(IOException ignored){}}); } catch(IOException ignored){} }
    }
    private double probeDuration(Path input) throws IOException, InterruptedException {
        Process p=new ProcessBuilder(ffprobe,"-v","error","-show_entries","format=duration","-of","default=noprint_wrappers=1:nokey=1",input.toString()).redirectErrorStream(true).start();
        if(!p.waitFor(20,TimeUnit.SECONDS)){p.destroyForcibly();throw new IllegalStateException("media probe timeout");}
        if(p.exitValue()!=0) throw new IllegalArgumentException("unsupported media");
        String value=new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
        try{return Double.parseDouble(value);}catch(NumberFormatException e){throw new IllegalArgumentException("media duration unavailable",e);}
    }
}
