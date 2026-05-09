package com.example.platform.sandbox.app;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Sandbox runtime service — disabled by default.
 *
 * <p>This is a placeholder facade for future Wasm/script sandbox execution.
 * The actual {@link com.example.platform.sandbox.domain.SandboxExecutor} SPI
 * is intentionally not wired here to avoid pulling in heavy dependencies.</p>
 *
 * <p><strong>Future work:</strong> Wasm runtime integration (e.g., Wasmtime, Wasmer)
 * will be added when the execution requirements are finalized. Until then,
 * this service returns stub responses indicating the module is disabled.</p>
 */
@Service
public class SandboxRuntimeService {

    private static final String DISABLED_STATUS = "disabled";

    public Map<String, Object> overview() {
        return Map.of(
                "module", "sandbox-runtime-module",
                "status", DISABLED_STATUS,
                "description", "沙箱运行时模块，预留 Wasm 与不可信脚本隔离执行位。"
        );
    }

    /**
     * Always returns a disabled response. Real execution requires a configured
     * {@link com.example.platform.sandbox.domain.SandboxExecutor} bean.
     */
    public Map<String, Object> execute(String code, String language, long timeout) {
        return Map.of(
                "status", DISABLED_STATUS,
                "message", "Sandbox execution is not enabled. Configure a SandboxExecutor bean to activate."
        );
    }

    public boolean isEnabled() {
        return false;
    }
}
