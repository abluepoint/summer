plugins {
    alias(libs.plugins.spring.boot)
    java
}

dependencies {
    implementation(platform(project(":summer-dependencies")))
    implementation(project(":summer-boot-starter"))
    implementation(project(":summer-data-jpa"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.session:spring-session-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-quartz")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation(libs.resilience4j.circuitbreaker)
    implementation(libs.resilience4j.retry)
    implementation(libs.resilience4j.ratelimiter)
    implementation(libs.resilience4j.bulkhead)
    implementation(libs.resilience4j.timelimiter)
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("org.flywaydb:flyway-mysql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(platform(project(":summer-dependencies")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-authorization-server-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("com.h2database:h2")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("summer-sample.jar")
}

// The full Sample suite retains architecture graphs and many Spring/JPA contexts.
tasks.named<org.gradle.api.tasks.testing.Test>("test") {
    maxHeapSize = "2g"
}
