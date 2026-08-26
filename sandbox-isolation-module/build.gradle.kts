plugins {
    id("java-library")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    environment("HOME", "/phase17-ambient-home-not-mounted")
    environment("AWS_SECRET_ACCESS_KEY", "phase17-ambient-aws-secret")
}
