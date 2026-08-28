plugins {
    id("java-library")
}

group = "com.example.platform"
version = "1.0.0"

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
    compileOnly("org.pf4j:pf4j:3.15.0")
    annotationProcessor("org.pf4j:pf4j:3.15.0")

    testImplementation(project(":artifact-module"))
    testImplementation(project(":storage-module"))
    testImplementation(project(":render-module"))
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
