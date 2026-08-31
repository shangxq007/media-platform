import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    id("java")
    id("org.springframework.boot")
}

group = "com.example.platform"
version = "1.0.0"

dependencies {
    implementation(project(":provider-plugin-runtime-module"))
    implementation(project(":worker-fabric-module"))
    implementation(project(":extension-module"))
    implementation(project(":media-execution-plan-module"))
    implementation(project(":sandbox-isolation-module"))
    implementation(project(":artifact-module"))
    implementation(project(":storage-module"))
    implementation(project(":render-module"))
    implementation(project(":shared-kernel"))
    implementation("io.micrometer:micrometer-core")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val providerProject = project(":ffmpeg-provider-module")
evaluationDependsOn(providerProject.path)
val providerJar = providerProject.tasks.named<Jar>("jar").flatMap { it.archiveFile }

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("media-platform-all-in-one.jar")
    mainClass.set("com.example.platform.distribution.PlatformDistributionLauncher")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    dependsOn(providerJar)
    from(providerJar) {
        into("embedded-plugins")
    }
}

val executableJar = tasks.named<BootJar>("bootJar").flatMap { it.archiveFile }

tasks.register("verifyBundledDistributionPluginDigest") {
    group = "verification"
    description = "Verifies producer and platform-bundled provider plugin bytes have the same SHA-256."
    dependsOn(providerJar, tasks.named("bootJar"))
    doLast {
        val producer = providerJar.get().asFile.toPath()
        val outer = executableJar.get().asFile
        val entryName = "embedded-plugins/${producer.fileName}"
        val digest = MessageDigest.getInstance("SHA-256")
        fun sha(bytes: ByteArray): String = digest.digest(bytes).joinToString("") { "%02x".format(it) }
        val producerDigest = sha(Files.readAllBytes(producer))
        val embeddedBytes = ZipFile(outer).use { zip ->
            val entry = zip.getEntry(entryName) ?: error("missing $entryName")
            zip.getInputStream(entry).readAllBytes()
        }
        val embeddedDigest = sha(embeddedBytes)
        check(producerDigest == embeddedDigest) {
            "plugin digest mismatch producer=$producerDigest embedded=$embeddedDigest"
        }
        println("FFMPEG_PROVIDER_PLUGIN_SHA256=$producerDigest")
    }
}

tasks.test {
    useJUnitPlatform()
    dependsOn(providerJar, tasks.named("bootJar"))
    systemProperty("distribution.provider.jar", providerJar.get().asFile.absolutePath)
    systemProperty("distribution.executable.jar", executableJar.get().asFile.absolutePath)
    systemProperty("distribution.repository.root", rootProject.projectDir.absolutePath)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
