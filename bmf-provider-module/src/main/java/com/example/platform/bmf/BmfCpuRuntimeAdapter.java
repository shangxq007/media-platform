package com.example.platform.bmf;

import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.workerfabric.domain.providernative.ExecutionCommand;
import com.example.platform.workerfabric.domain.providernative.ProcessInvocationSpec;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeOutputFiles;
import com.example.platform.workerfabric.domain.providernative.RuntimeAdapter;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionBundle;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionContext;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Invokes the versioned BMF Graph program through the worker's typed process boundary. */
public final class BmfCpuRuntimeAdapter implements RuntimeAdapter<BmfCpuTranscodePlan> {
    private static final String INPUT_PREFIX = "@platform-materialized-input:";

    // Kept with provider source so a deployed Worker runs exactly the graph verified by this build.
    // The Python runtime and BMF libraries are separately pinned worker dependencies.
    static final String GRAPH_PROGRAM = """
            import os
            import sys
            import bmf

            source, output = sys.argv[1:3]
            print('BMF_GRAPH_START', flush=True)
            (bmf.graph().decode({'input_path': source})['video']
                .scale(64, 48)
                .encode(None, {'output_path': output, 'format': 'mp4',
                               'video_params': {'codec': 'libx264', 'pix_fmt': 'yuv420p'}})
                .run())
            if not os.path.isfile(output) or os.stat(output).st_size == 0:
                raise RuntimeError('BMF graph produced no media output')
            print('BMF_GRAPH_DONE', flush=True)
            """;

    private final Path python;

    public BmfCpuRuntimeAdapter(Path python) {
        Objects.requireNonNull(python, "python");
        if (!python.isAbsolute() || !python.equals(python.normalize())) {
            throw new IllegalArgumentException("BMF Python must be an absolute normalized path");
        }
        this.python = python;
    }

    @Override
    public RuntimeExecutionBundle adapt(BmfCpuTranscodePlan plan, RuntimeExecutionContext context) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "context");
        if (!plan.executableTaskId().equals(context.executableTaskId())
                || !plan.providerBindingPin().equals(context.providerBindingPin())) {
            throw new ProviderNativeExecutionFailure(ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH,
                    "BMF runtime requires the exact task and provider binding");
        }
        var invocation = ProcessInvocationSpec.of(python.toString(), List.of(
                "-s", "-c", GRAPH_PROGRAM, materializedInputToken(plan.inputId()),
                ProviderNativeOutputFiles.RENDER_OUTPUT));
        var command = new ExecutionCommand(context.executableTaskId(), context.providerBindingPin(),
                context.platformExecutionAttemptId(), context.platformOwnershipGeneration(), 0, invocation);
        return new RuntimeExecutionBundle(context.executableTaskId(), context.providerBindingPin(),
                context.platformExecutionAttemptId(), context.platformOwnershipGeneration(), List.of(command));
    }

    public static String materializedInputToken(ExecutionInputId inputId) {
        return INPUT_PREFIX + Objects.requireNonNull(inputId, "inputId").value();
    }
}
