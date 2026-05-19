package com.example.platform.render.infrastructure.gpac;

import com.example.platform.extension.app.ProcessToolRunner;

/**
 * @deprecated Use {@link GPACEnvironmentValidator} instead. This class is retained for
 *             backward compatibility and delegates to GPACEnvironmentValidator.
 */
@Deprecated
public class GpacEnvironmentValidator {

    private final GPACEnvironmentValidator delegate;

    public GpacEnvironmentValidator(ProcessToolRunner processToolRunner) {
        this.delegate = new GPACEnvironmentValidator(processToolRunner);
    }

    public boolean validate() {
        return delegate.validate();
    }
}
