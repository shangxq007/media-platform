package com.example.platform.render.integration.extension;

import com.example.platform.extension.api.port.*;
import com.example.platform.extension.app.ToolRegistry;
import com.example.platform.extension.domain.*;
import com.example.platform.sandbox.LocalSandboxProcessExecutionAdapter;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual bounded sandbox process; no provider or rendering capability is claimed. */
class ExtensionSandboxToolRunnerAdapterTest {
    @TempDir Path workspace;
    @Test void publicToolPortExecutesAllowedArgumentsThroughTheCanonicalSandbox() throws Exception {
        ToolCatalog tools=new ToolRegistry();String executable=Path.of("/usr/bin/printf").toRealPath().toString();
        tools.registerExecutable("print",executable);
        tools.registerTool(new ToolDefinition("print","Print","test",executable,List.of(),ToolExecutionSafetyPolicy.defaults()));
        ProcessToolRunner runner=new ExtensionSandboxToolRunnerAdapter(tools,new LocalSandboxProcessExecutionAdapter());
        var result=runner.execute(new ToolExecutionRequest("print",List.of("%s","literal ; text"),Map.of(),workspace.toString(),5000));
        assertEquals(0,result.exitCode(),result.stderr());assertEquals("literal ; text",result.stdout());assertFalse(result.timedOut());
    }
    @Test void unregisteredExecutableCannotExecuteOrWrite() {
        ToolCatalog tools=new ToolRegistry();var runner=new ExtensionSandboxToolRunnerAdapter(tools,new LocalSandboxProcessExecutionAdapter());
        Path marker=workspace.resolve("denied");
        assertThrows(IllegalArgumentException.class,()->runner.run(new ToolRunRequest("/usr/bin/touch",List.of(marker.toString()),5000)));
        assertFalse(Files.exists(marker));assertTrue(tools.listTools().isEmpty());
    }
}
