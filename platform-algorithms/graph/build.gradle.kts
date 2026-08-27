plugins {
    id("java-library")
    id("jacoco")
}

group = "com.example.platform"
version = "0.2.0-SNAPSHOT"

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
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    testImplementation("org.jgrapht:jgrapht-core:1.5.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-Xmx2g", "-XX:+HeapDumpOnOutOfMemoryError", "--enable-native-access=ALL-ALL")
    systemProperty("aws.ec2.metadata.disabled", "true")
    systemProperty("http.nonProxyHosts", "localhost|127.0.0.1")
    systemProperty("http.proxyHost", "")
    systemProperty("https.proxyHost", "")
    environment("AWS_ACCESS_KEY_ID", "test-access-key")
    environment("AWS_SECRET_ACCESS_KEY", "test-secret-key")
    environment("AWS_REGION", "us-east-1")
    systemProperty("faof2.repositoryRoot", rootProject.projectDir.absolutePath)
}

tasks.withType<JacocoReport> {
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}
