// Spring AI Adapter - Optional module for OpenAI-compatible chat providers
// This module is NOT included in platform-app by default.
// To enable, add: implementation(project(":spring-ai-adapter")) to platform-app/build.gradle.kts
// and configure: platform.ai.spring-ai.enabled=true
plugins { id("java-library") }

dependencies {
    api(project(":shared-kernel"))
    api(project(":ai-module"))
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.ai:spring-ai-starter-model-openai")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
