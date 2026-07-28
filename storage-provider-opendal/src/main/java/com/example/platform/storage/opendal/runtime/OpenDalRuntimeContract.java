package com.example.platform.storage.opendal.runtime;

import com.example.platform.render.domain.storage.error.StorageError;
import com.example.platform.storage.opendal.OpenDalStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Production Native Runtime Contract validator for OpenDAL.
 *
 * Validates that the JVM runtime environment satisfies OpenDAL's requirements:
 * <ul>
 *   <li>{@code --enable-native-access=ALL-UNNAMED} is present</li>
 *   <li>Native library can be loaded</li>
 *   <li>OS/architecture is supported</li>
 * </ul>
 *
 * <p>This class is idempotent — multiple calls to {@link #validateRuntime()} are safe.
 */
public final class OpenDalRuntimeContract {

    private static final Logger log = LoggerFactory.getLogger(OpenDalRuntimeContract.class);

    private static final AtomicBoolean validated = new AtomicBoolean(false);
    private static volatile RuntimeValidationResult lastResult;

    private OpenDalRuntimeContract() {
    }

    public static final String REQUIRED_JVM_ARG = "--enable-native-access=ALL-UNNAMED";

    public static final String REQUIRED_JVM_ARG_MODULE = "--enable-native-access=org.apache.opendal";

    public static List<String> requiredJvmArgs() {
        return List.of(REQUIRED_JVM_ARG);
    }

    public static boolean nativeAccessAvailable() {
        try {
            org.apache.opendal.NativeLibrary.loadLibrary();
            return true;
        } catch (Throwable t) {
            log.debug("Native access not available: {}", t.getMessage());
            return false;
        }
    }

    public static boolean isPlatformSupported() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();

        boolean linux = os.contains("linux");
        boolean macos = os.contains("mac") || os.contains("darwin");
        boolean x86_64 = arch.contains("amd64") || arch.contains("x86_64");
        boolean aarch64 = arch.contains("aarch64") || arch.contains("arm64");

        return (linux && (x86_64 || aarch64)) || (macos && aarch64);
    }

    public static RuntimeValidationResult validateRuntime() {
        if (validated.get() && lastResult != null) {
            return lastResult;
        }
        RuntimeValidationResult result = doValidate();
        lastResult = result;
        validated.set(true);
        return result;
    }

    private static RuntimeValidationResult doValidate() {
        if (!nativeAccessAvailable()) {
            String msg = "Native access not enabled. Add " + REQUIRED_JVM_ARG +
                    " to JVM arguments.";
            log.error(msg);
            return RuntimeValidationResult.ofFailure(
                    StorageError.ErrorCode.STORAGE_PROVIDER_UNAVAILABLE, msg);
        }

        if (!isPlatformSupported()) {
            String os = System.getProperty("os.name");
            String arch = System.getProperty("os.arch");
            String msg = "Unsupported platform: " + os + "/" + arch +
                    ". Supported: linux-x86_64, linux-aarch64, osx-aarch64";
            log.error(msg);
            return RuntimeValidationResult.ofFailure(
                    StorageError.ErrorCode.STORAGE_NOT_IMPLEMENTED, msg);
        }

        try {
            var operator = org.apache.opendal.Operator.of("memory", java.util.Map.of());
            operator.close();
        } catch (Exception e) {
            String msg = "Native operator creation failed: " + e.getMessage();
            log.error(msg);
            return RuntimeValidationResult.ofFailure(
                    StorageError.ErrorCode.STORAGE_PROVIDER_UNAVAILABLE, msg);
        }

        log.info("OpenDAL runtime validation passed");
        return RuntimeValidationResult.ofSuccess();
    }

    public static boolean smokeCheck() {
        try {
            org.apache.opendal.NativeLibrary.loadLibrary();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void validateRuntimeOrThrow() {
        RuntimeValidationResult result = validateRuntime();
        if (!result.success()) {
            throw new OpenDalStorageException(result.errorCode(), result.message());
        }
    }

    public static void reset() {
        validated.set(false);
        lastResult = null;
    }

    public record RuntimeValidationResult(
            boolean success,
            StorageError.ErrorCode errorCode,
            String message
    ) {
        public static RuntimeValidationResult ofSuccess() {
            return new RuntimeValidationResult(true, null, "OpenDAL runtime validated");
        }

        public static RuntimeValidationResult ofFailure(StorageError.ErrorCode code, String message) {
            return new RuntimeValidationResult(false, code, message);
        }
    }
}
