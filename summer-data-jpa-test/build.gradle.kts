plugins {
    alias(libs.plugins.spring.boot)
    java
}

sourceSets {
    named("main") {
        java.setSrcDirs(listOf("src/boot4/java"))
        resources.setSrcDirs(listOf("src/boot4/resources"))
    }
    named("test") {
        java.setSrcDirs(listOf("src/boot4Test/java"))
        resources.setSrcDirs(listOf("src/boot4Test/resources"))
    }
}

dependencies {
    implementation(platform(project(":summer-dependencies")))
    implementation(project(":summer-boot-starter"))
    implementation(project(":summer-data-jpa"))
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(platform(project(":summer-dependencies")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-authorization-server-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("summer-sample.jar")
}
