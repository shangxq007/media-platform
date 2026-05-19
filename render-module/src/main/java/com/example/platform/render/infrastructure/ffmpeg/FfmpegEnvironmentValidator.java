package com.example.platform.render.infrastructure.ffmpeg;

import com.example.platform.extension.app.ProcessToolRunner;

/**
 * @deprecated Use {@link FFmpegEnvironmentValidator} instead. This class is retained for
 *             backward compatibility and delegates to FFmpegEnvironmentValidator.
 */
@Deprecated
public class FfmpegEnvironmentValidator {

    private final FFmpegEnvironmentValidator delegate;

    public FfmpegEnvironmentValidator(ProcessToolRunner processToolRunner) {
        this.delegate = new FFmpegEnvironmentValidator(processToolRunner);
    }

    public boolean validate() {
        return delegate.validate();
    }

    public boolean validateBinary(String toolKey) {
        return delegate.validateBinary(toolKey);
    }
}
