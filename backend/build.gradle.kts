plugins {
    id("org.springframework.boot") version "3.4.4"
    id("io.spring.dependency-management") version "1.1.7"
    java
    jacoco
}

group = "com.fashionrental"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
    }
}

configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("net.coobird:thumbnailator:0.4.20")
    implementation("software.amazon.awssdk:s3:2.25.60")
    // OpenAPI / Swagger UI (dev only — disabled in prod via application.yml)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")

    "integrationTestImplementation"("org.springframework.boot:spring-boot-testcontainers")
    "integrationTestImplementation"("org.testcontainers:junit-jupiter")
    "integrationTestImplementation"("org.testcontainers:postgresql")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Full exception messages (not just class+line) in the console report — cheap and
    // saves a round trip to the HTML report whenever a test fails in CI.
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests against real PostgreSQL via Testcontainers."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter(tasks.named("test"))
}

tasks.named("check") {
    dependsOn(integrationTest)
}

tasks.jacocoTestReport {
    // Aggregate coverage from both unit tests and Testcontainers integration tests.
    executionData(tasks.test.get(), integrationTest.get())
    reports {
        xml.required.set(true)
        csv.required.set(true)
        html.required.set(true)
    }
}

tasks.register("checkCoverageThreshold") {
    description = "Verifies that code coverage meets minimum thresholds."
    group = "verification"
    dependsOn("jacocoTestReport")

    doLast {
        val buildDir = layout.buildDirectory.asFile.get()
        val coverageFile = File("$buildDir/reports/jacoco/test/jacocoTestReport.csv")
        require(coverageFile.exists()) { "Coverage report not found at $coverageFile" }

        val lines = coverageFile.readLines()
        if (lines.size < 2) throw GradleException("Coverage CSV is empty")

        val headerLine = lines[0]
        val headers = headerLine.split(",")

        val lineMissedIdx = headers.indexOf("LINE_MISSED")
        val lineCoveredIdx = headers.indexOf("LINE_COVERED")

        require(lineMissedIdx >= 0 && lineCoveredIdx >= 0) { "LINE_MISSED or LINE_COVERED not found in CSV" }

        // Sum coverage across all data rows (skip header at index 0)
        var totalLineMissed = 0
        var totalLineCovered = 0

        for (i in 1 until lines.size) {
            val values = lines[i].split(",")
            if (values.size > lineCoveredIdx) {
                try {
                    totalLineMissed += values[lineMissedIdx].toInt()
                    totalLineCovered += values[lineCoveredIdx].toInt()
                } catch (e: NumberFormatException) {
                    // Skip rows with non-numeric values
                }
            }
        }

        val totalLines = totalLineMissed + totalLineCovered
        val lineCoveragePercent = if (totalLines > 0) (totalLineCovered * 100) / totalLines else 0

        println("Backend Test Coverage Report:")
        println("  Lines covered: $totalLineCovered")
        println("  Lines missed: $totalLineMissed")
        println("  Total lines: $totalLines")
        println("  Coverage: $lineCoveragePercent%")
        println("  Threshold: 85%")

        if (lineCoveragePercent < 85) {
            throw GradleException("Backend line coverage $lineCoveragePercent% is below threshold of 85%")
        }

        println("✓ Backend coverage check passed!")
    }
}
