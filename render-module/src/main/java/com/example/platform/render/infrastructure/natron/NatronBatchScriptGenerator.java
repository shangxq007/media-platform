package com.example.platform.render.infrastructure.natron;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Materializes the Natron Python batch script for {@code video.natron_vignette} per render job.
 */
@Component
public class NatronBatchScriptGenerator {

    public static final String TEMPLATE_CLASSPATH =
            "natron/templates/video.natron_vignette/batch_vignette.py";

    public static final String TEMPLATE_COLOR_GRADE_CLASSPATH =
            "natron/templates/video.natron_color_grade/batch_color_grade.py";

    public Path generate(NatronPocJob job, Path jobNatronDir) throws IOException {
        Files.createDirectories(jobNatronDir);
        String scriptName = "video.natron_color_grade".equals(job.effectKey())
                ? "batch_color_grade.py"
                : "batch_vignette.py";
        Path scriptPath = jobNatronDir.resolve(scriptName);
        String template = loadTemplate(resolveTemplateClasspath(job.effectKey()));
        String content = template
                .replace("__INPUT__", escapeForPythonString(job.inputLocalPath()))
                .replace("__OUTPUT__", escapeForPythonString(job.outputLocalPath()));
        Files.writeString(scriptPath, content, StandardCharsets.UTF_8);
        return scriptPath.toAbsolutePath();
    }

    private static String resolveTemplateClasspath(String effectKey) {
        if ("video.natron_color_grade".equals(effectKey)) {
            return TEMPLATE_COLOR_GRADE_CLASSPATH;
        }
        return TEMPLATE_CLASSPATH;
    }

    private static String loadTemplate(String classpath) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpath);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String escapeForPythonString(String path) {
        return path.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
