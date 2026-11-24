plugins {
    id("java-library")
}

group = "com.example.platform"
version = "1.3.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

dependencies {
    implementation(project(":provider-plugin-runtime-module"))
    implementation(project(":media-execution-plan-module"))
    implementation(project(":worker-fabric-module"))
    implementation(project(":sandbox-isolation-module"))
    implementation(project(":extension-module"))
    // ABI companion referenced by canonical execution-plan record signatures.
    compileOnly(project(":render-module"))
    // #20 timed-text rasterization lowering reads the typed TimedTextMaterializationRequirement
    // and its authored StyledText/TextFrame values (no cycle: font-text -> shared-kernel only).
    compileOnly(project(":font-text-module"))
    compileOnly("org.pf4j:pf4j:3.15.0")
    annotationProcessor("org.pf4j:pf4j:3.15.0")

    testImplementation(project(":artifact-module"))
    testImplementation(project(":storage-module"))
    testImplementation(project(":render-module"))
    // #20 raster lowering tests construct the typed TimedTextMaterializationRequirement values.
    testImplementation(project(":font-text-module"))
    // Canonical Audio Mix authority is needed by the TimelineDocument fixture of the
    // typed-chain integration test (render/timeline expose it only as an implementation dep).
    testImplementation(project(":audio-module"))
    testImplementation(project(":shared-kernel"))
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("ffmpeg-provider-plugin")
    manifest {
        attributes(
            "Plugin-Id" to "media.transcode.ffmpeg",
            "Plugin-Version" to project.version,
            "Plugin-Class" to "com.example.platform.ffmpeg.FfmpegProviderPlugin",
            "Plugin-Provider" to "media-platform",
        )
    }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// Explicit Linux runtime tool distribution, separate from the Java application classpath.
// The bounded provider emits H.264 with libx264; a host ffmpeg without that encoder is insufficient.
val ffmpegLinuxRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
dependencies {
    ffmpegLinuxRuntime("org.bytedeco:ffmpeg:7.1.1-1.5.12:linux-x86_64-gpl")
}
tasks.register<Copy>("stageFfmpegLinuxRuntime") {
    group = "distribution"
    description = "Stages the pinned Linux FFmpeg runtime and license files; never executes it."
    from(ffmpegLinuxRuntime.map { zipTree(it) })
    into(layout.buildDirectory.dir("ffmpeg-runtime"))
    includeEmptyDirs = false
    eachFile {
        if (name == "ffmpeg" || name == "ffprobe") permissions { unix("rwxr-xr-x") }
    }
}

// Integration tests use the same declared GPL runtime as the real Worker, not host PATH codecs.
tasks.test {
    dependsOn(tasks.named("stageFfmpegLinuxRuntime"))
    systemProperty("media.platform.ffmpeg.binary-dir",
        layout.buildDirectory.dir("ffmpeg-runtime/org/bytedeco/ffmpeg/linux-x86_64-gpl").get().asFile.absolutePath)
}
