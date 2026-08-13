plugins { id("java-library") }

dependencies {
    implementation(project(":typed-schema-module"))
    implementation(project(":billing-module")) // cost ports rehomed to billing (K2)
    api(project(":shared-kernel"))
    api(project(":policy-governance-module"))
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-jooq")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
