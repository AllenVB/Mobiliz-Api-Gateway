plugins {
    java
    jacoco
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.0"
}

group = "com.mobiliz"
version = "0.0.1-SNAPSHOT"
description = "Translation middleware exposing the Yetkili Servis legacy API under a clean English contract"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Stands in for the legacy API in end-to-end tests. The standalone (shaded) build is
    // used deliberately: it keeps WireMock's own Jetty and Jackson off the test classpath,
    // where they would otherwise collide with Boot's.
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

spotless {
    java {
        target("src/**/*.java")
        // AOSP style: four-space indent and a 100-column limit, which is what this code
        // base already uses. Chosen over the two-space Google default so applying the
        // formatter is a no-op on existing files rather than a rewrite of every line.
        googleJavaFormat("1.36.1").aosp()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
    format("yaml") {
        target("src/**/*.yaml", "src/**/*.yml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
