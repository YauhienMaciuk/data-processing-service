plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.billogram"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Unit tests are *Test classes (fast, no Docker); integration tests are *IT classes (Spring + Postgres via Testcontainers)
tasks.test {
    description = "Runs unit tests."
    useJUnitPlatform()
    filter { excludeTestsMatching("*IT") }
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests against Postgres in Testcontainers (requires Docker)."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*IT") }
    shouldRunAfter(tasks.test)
}

jacoco {
    toolVersion = "0.8.15"
}

// Entry point only bootstraps Spring; nothing to test
val coverageExclusions = listOf("com/billogram/dataprocessing/DataProcessingServiceApplication*")

tasks.jacocoTestReport {
    dependsOn(tasks.test, integrationTest)
    executionData(tasks.test.get(), integrationTest.get())
    classDirectories.setFrom(sourceSets.main.get().output.classesDirs.map { fileTree(it) { exclude(coverageExclusions) } })
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test, integrationTest)
    executionData(tasks.test.get(), integrationTest.get())
    classDirectories.setFrom(sourceSets.main.get().output.classesDirs.map { fileTree(it) { exclude(coverageExclusions) } })
    violationRules {
        rule {
            // All code must be covered: unit tests for logic, integration tests (*IT) for SQL and HTTP wiring
            limit { counter = "LINE"; minimum = "1.0".toBigDecimal() }
            limit { counter = "BRANCH"; minimum = "1.0".toBigDecimal() }
        }
    }
}

tasks.check {
    dependsOn(integrationTest, tasks.jacocoTestCoverageVerification)
    finalizedBy(tasks.jacocoTestReport)
}
