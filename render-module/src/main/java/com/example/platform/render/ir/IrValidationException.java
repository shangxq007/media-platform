package com.example.platform.render.ir;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Thrown when IR validation fails, carrying a list of typed validation errors.
 */
public class IrValidationException extends RuntimeException {

    private final List<IrValidationError> errors;

    public IrValidationException(List<IrValidationError> errors) {
        super(buildMessage(errors));
        this.errors = Collections.unmodifiableList(Objects.requireNonNull(errors, "errors must not be null"));
    }

    public List<IrValidationError> errors() {
        return errors;
    }

    private static String buildMessage(List<IrValidationError> errors) {
        if (errors.isEmpty()) {
            return "IR validation failed with no errors";
        }
        if (errors.size() == 1) {
            return errors.get(0).toString();
        }
        return "IR validation failed with " + errors.size() + " errors: " + errors.get(0);
    }
}
