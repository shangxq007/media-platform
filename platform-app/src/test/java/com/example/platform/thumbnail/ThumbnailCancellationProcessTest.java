package com.example.platform.thumbnail;

import static org.junit.jupiter.api.Assertions.*;
import com.example.platform.sandbox.execution.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real bounded sandbox child: cancellation is requested only after the child writes its marker. */
class ThumbnailCancellationProcessTest {
    @TempDir Path root;
    @Test void cancelsAnActuallyStartedProcessBeforeItsTimeout() throws Exception {
        Path input=Files.write(root.resolve("input"),new byte[]{1});
        Path marker=root.resolve(".sandbox-output/started");
        var request=new ExecutionRequest("cancel-child","cancel-child",TaskCapability.THUMBNAIL,
                root.toString(),Map.of(),List.of("-c",
                "from pathlib import Path; import time; Path('/workspace/.sandbox-output/started').write_text('started'); time.sleep(10)"),
                4,"","",Map.of("executable",Path.of("/usr/bin/python3").toRealPath().toString(),"input",input.toString()));
        long start=System.nanoTime();
        var result=new ThumbnailExecutionBackend().execute(request,()->Files.exists(marker));
        assertTrue(Files.exists(marker),"the native child must actually have started: "+result);
        assertFalse(result.success());assertEquals("CANCELLED",result.errorCode());
        assertTrue(Duration.ofNanos(System.nanoTime()-start).compareTo(Duration.ofSeconds(4))<0,
                "cancellation must terminate and reap the child before timeout");
    }
    @Test void nonzeroNativeExitNeverReportsSuccess() throws Exception {
        Path input=Files.write(root.resolve("input"),new byte[]{1});
        var request=new ExecutionRequest("failed-child","failed-child",TaskCapability.THUMBNAIL,
                root.toString(),Map.of(),List.of("-c","raise SystemExit(7)"),
                4,"","",Map.of("executable",Path.of("/usr/bin/python3").toRealPath().toString(),"input",input.toString()));
        var result=new ThumbnailExecutionBackend().execute(request,()->false);
        assertFalse(result.success());assertEquals(7,result.exitCode(),result.toString());assertEquals("PROCESS_CRASHED",result.errorCode());
    }
    @Test void alreadyCancelledRequestNeverStartsItsInvalidExecutable() {
        var request=new ExecutionRequest("cancel-first","cancel-first",TaskCapability.THUMBNAIL,
                root.toString(),Map.of(),List.of(),1,"","",Map.of());
        var result=new ThumbnailExecutionBackend().execute(request,()->true);
        assertFalse(result.success());assertEquals("CANCELLED",result.errorCode());
    }
}
