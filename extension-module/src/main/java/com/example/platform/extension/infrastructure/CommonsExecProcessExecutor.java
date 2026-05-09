package com.example.platform.extension.infrastructure;

import com.example.platform.extension.app.ProcessExecutor;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.PumpStreamHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Runs an external process via Apache Commons Exec; stdout/stderr are discarded.
 */
@Component
public class CommonsExecProcessExecutor implements ProcessExecutor {

    @Override
    public int execute(String executable, String... processArgs) {
        CommandLine commandLine = new CommandLine(executable);
        if (processArgs != null && processArgs.length > 0) {
            commandLine.addArguments(processArgs, false);
        }
        DefaultExecutor commandExecutor = new DefaultExecutor();
        commandExecutor.setStreamHandler(new PumpStreamHandler(
                OutputStream.nullOutputStream(),
                OutputStream.nullOutputStream(),
                InputStream.nullInputStream()));
        try {
            return commandExecutor.execute(commandLine);
        } catch (ExecuteException e) {
            return e.getExitValue();
        } catch (IOException e) {
            throw new IllegalStateException("failed to execute: " + executable, e);
        }
    }
}
