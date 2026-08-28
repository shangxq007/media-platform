import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    archiveFileName.set("media-platform-launcher.jar")
    mainClass.set("com.example.platform.distribution.PlatformDistributionLauncher")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val allInOneFile = layout.buildDirectory.file("libs/media-platform-all-in-one.jar")
val allInOneJar = tasks.register("allInOneJar") {
    group = "distribution"
    description = "Builds the executable host with the unchanged provider plugin nested under embedded-plugins/."
    dependsOn(providerJar, tasks.named("bootJar"))
    inputs.file(tasks.named<BootJar>("bootJar").flatMap { it.archiveFile })
    inputs.file(providerJar)
    outputs.file(allInOneFile)
    doLast {
        val launcher = tasks.named<BootJar>("bootJar").get().archiveFile.get().asFile
        val plugin = providerJar.get().asFile
        val output = allInOneFile.get().asFile
        val temporary = layout.buildDirectory.file("tmp/allInOneJar/media-platform-all-in-one.jar").get().asFile
        temporary.parentFile.mkdirs()
        val fixedTime = 315532800000L
        ZipOutputStream(temporary.outputStream().buffered()).use { target ->
            ZipFile(launcher).use { source ->
                source.entries().asSequence().forEach { original ->
                    val entry = ZipEntry(original.name)
                    entry.time = fixedTime
                    entry.method = original.method
                    if (original.method == ZipEntry.STORED) {
                        entry.size = original.size
                        entry.compressedSize = original.size
                        entry.crc = original.crc
                    }
                    target.putNextEntry(entry)
                    if (!original.isDirectory) {
                        source.getInputStream(original).use { it.copyTo(target) }
                    }
                    target.closeEntry()
                }
            }
            val directory = ZipEntry("embedded-plugins/")
            directory.time = fixedTime
            target.putNextEntry(directory)
            target.closeEntry()
            val bytes = plugin.readBytes()
            val embedded = ZipEntry("embedded-plugins/${plugin.name}")
            embedded.time = fixedTime
            target.putNextEntry(embedded)
            target.write(bytes)
            target.closeEntry()
        }
        output.parentFile.mkdirs()
        Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

val stageModularDistribution = tasks.register<Sync>("stageModularDistribution") {
    group = "distribution"
    description = "Stages the application artifact plus plugins/exact-provider-plugin.jar."
    into(layout.buildDirectory.dir("distributions/modular"))
    from(tasks.named("bootJar"))
    from(providerJar) {
        into("plugins")
    }
    dependsOn(providerJar)
}

tasks.register<Zip>("modularDistribution") {
    group = "distribution"
    description = "Produces the modular application-plus-plugins distribution archive."
    archiveFileName.set("media-platform-modular.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(stageModularDistribution)
    dependsOn(stageModularDistribution)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.register("verifyDualDistributionPluginDigest") {
    group = "verification"
    description = "Verifies producer, modular, and embedded provider plugin bytes have the same SHA-256."
    dependsOn(providerJar, stageModularDistribution, allInOneJar)
    doLast {
        val producer = providerJar.get().asFile.toPath()
        val modular = layout.buildDirectory.file(
            "distributions/modular/plugins/${producer.fileName}").get().asFile.toPath()
        val outer = allInOneFile.get().asFile
        val entryName = "embedded-plugins/${producer.fileName}"
        val digest = MessageDigest.getInstance("SHA-256")
        fun sha(bytes: ByteArray): String = digest.digest(bytes).joinToString("") { "%02x".format(it) }
        val producerDigest = sha(Files.readAllBytes(producer))
        val modularDigest = sha(Files.readAllBytes(modular))
        val embeddedBytes = ZipFile(outer).use { zip ->
            val entry = zip.getEntry(entryName) ?: error("missing $entryName")
            zip.getInputStream(entry).readAllBytes()
        }
        val embeddedDigest = sha(embeddedBytes)
        check(producerDigest == modularDigest && producerDigest == embeddedDigest) {
            "plugin digest mismatch producer=$producerDigest modular=$modularDigest embedded=$embeddedDigest"
        }
        println("FFMPEG_PROVIDER_PLUGIN_SHA256=$producerDigest")
    }
}

tasks.test {
    useJUnitPlatform()
    dependsOn(providerJar, stageModularDistribution, allInOneJar)
    systemProperty("distribution.modular.plugin",
        layout.buildDirectory.file("distributions/modular/plugins/ffmpeg-provider-plugin-1.0.0.jar")
            .get().asFile.absolutePath)
    systemProperty("distribution.allinone.jar",
        layout.buildDirectory.file("libs/media-platform-all-in-one.jar").get().asFile.absolutePath)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
