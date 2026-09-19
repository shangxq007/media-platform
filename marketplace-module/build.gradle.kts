plugins { id("java-library") }
dependencies {
    api(project(":shared-kernel"))
    api(project(":media-module"))
    api(project(":identity-access-module")) // published ProjectScope value in event facts
    implementation(project(":outbox-event-module"))
    implementation(project(":typed-schema-module"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
