package com.example.platform.render.app.operation;

import com.example.platform.operation.invocation.OperationInvocationContext;
import com.example.platform.operation.invocation.OperationInvocationPort;
import com.example.platform.operation.invocation.OperationInvocationResult;
import com.example.platform.operation.operation.OperationRequest;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalOperationInvocationArchitectureTest {

    private static final String FROZEN_H7_CONTROLLER_SHA256 =
            "29f884f53d3b7adc640859bdf78cdab4fa0468cd6e303fef8131a6a4dc940702";
    private static final Path ROOT = repositoryRoot(Path.of(System.getProperty("user.dir")));
    private static final Path RENDER_MAIN = ROOT.resolve("render-module/src/main/java");
    private static final Path SERVICE = RENDER_MAIN.resolve(
            "com/example/platform/render/app/operation/CanonicalOperationInvocationService.java");
    private static final Path CONTROLLER = ROOT.resolve(
            "platform-app/src/main/java/com/example/platform/web/render/"
                    + "TimelineMediaClipOperationController.java");

    @Test
    void exactlyOneRenderImplementationOwnsNoSemanticOrWriterAuthority() throws Exception {
        List<Path> implementations;
        try (var files = Files.walk(RENDER_MAIN)) {
            implementations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).contains("implements OperationInvocationPort"))
                    .toList();
        }
        assertEquals(List.of(SERVICE), implementations);

        String source = read(SERVICE);
        assertFalse(source.contains("new OperationPlanner"));
        assertFalse(source.contains("TimelineRevisionSaveService"));
        assertFalse(source.contains("Repository"));
        assertFalse(source.contains("java.lang.reflect"));
        assertFalse(source.contains("Class.forName"));
        assertFalse(source.contains("TargetRevisionRef"));
        assertFalse(source.contains("saveRevision"));
    }

    @Test
    void publicSurfaceIsOnlyTheOperationOwnedInvocationContract() throws Exception {
        var publicMethods = Arrays.stream(CanonicalOperationInvocationService.class
                        .getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();
        assertEquals(1, publicMethods.size());
        var invoke = publicMethods.getFirst();
        assertEquals("invoke", invoke.getName());
        assertEquals(OperationInvocationResult.class, invoke.getReturnType());
        assertArrayEquals(
                new Class<?>[]{OperationRequest.class, OperationInvocationContext.class},
                invoke.getParameterTypes());
        assertTrue(OperationInvocationPort.class
                .isAssignableFrom(CanonicalOperationInvocationService.class));
    }

    @Test
    void renderDeclaresOnlyTheExactOperationInvocationDependency() throws Exception {
        String module = read(RENDER_MAIN.resolve("com/example/platform/render/package-info.java"));
        assertEquals(1, count(module, "\"operation :: invocation\""));
        assertFalse(module.contains("\"operation\","));
    }

    @Test
    void existingH7HttpControllerRemainsByteIdenticalToFrozenH7Baseline() throws Exception {
        byte[] controllerBytes = Files.readAllBytes(CONTROLLER);
        assertEquals(FROZEN_H7_CONTROLLER_SHA256, sha256(controllerBytes));

        byte[] mutatedBytes = controllerBytes.clone();
        mutatedBytes[0] ^= 1;
        assertFalse(FROZEN_H7_CONTROLLER_SHA256.equals(sha256(mutatedBytes)));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException failure) {
            throw new AssertionError("cannot read " + path, failure);
        }
    }

    private static int count(String text, String token) {
        int count = 0;
        for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + token.length())) {
            count++;
        }
        return count;
    }

    private static Path repositoryRoot(Path start) {
        for (Path current = start.toAbsolutePath().normalize(); current != null;
             current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
                return current;
            }
        }
        throw new IllegalStateException("repository root not found from " + start);
    }
}
