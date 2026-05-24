package com.example.platform.sandbox.domain;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class DefaultSandboxSecurityPolicy implements SandboxSecurityPolicy {

    private static final Set<String> ALLOWED_LANGUAGES = Set.of(
            "groovy", "javascript", "js", "python", "py", "wasm"
    );

    private static final List<String> BLOCKED_PATTERNS = List.of(
            "Runtime.getRuntime", "ProcessBuilder", "exec(", "System.exit",
            "java.io.File", "java.nio.file", "Socket(", "ServerSocket(",
            "URL(", "ClassLoader", "reflect.", "Unsafe",
            "System.setProperty", "System.getenv",
            "groovy.lang.GroovyShell", "groovy.lang.GroovyClassLoader",
            "org.codehaus.groovy.runtime", "invokeMethod",
            "metaClass", ".class.", "getClass()", "forName(",
            "getMethod(", "getDeclaredMethod(", "getConstructor(",
            "setAccessible(", "newInstance(", "invoke(",
            "Thread.sleep", "Thread.start", "Thread.stop",
            "java.lang.Process", "java.lang.reflect",
            "javax.script.ScriptEngineManager",
            "java.net.URL", "java.net.HttpURLConnection",
            "java.io.BufferedReader", "java.io.InputStreamReader",
            "java.io.FileInputStream", "java.io.FileOutputStream",
            "java.lang.System.console", "java.lang.System.load",
            "java.lang.System.loadLibrary", "java.lang.Runtime.exec"
    );

    @Override
    public boolean isAllowed(String command) {
        if (command == null || command.isBlank()) return false;
        String lower = command.toLowerCase().trim();
        return ALLOWED_LANGUAGES.contains(lower);
    }

    public boolean isCodeSafe(String code) {
        if (code == null) return true;
        String lower = code.toLowerCase();
        for (String pattern : BLOCKED_PATTERNS) {
            if (lower.contains(pattern.toLowerCase())) {
                return false;
            }
        }
        return true;
    }
}
