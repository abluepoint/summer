import java.io.File
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider

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

// Keep class directories lazy: candidate validation relocates each project's buildDirectory.
class ProductionClassesArgumentProvider(
    @get:Classpath val outputs: FileCollection
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        // Empty starter SourceSets produce no directory; all main classes tasks still run.
        val directories = outputs.files.filter { it.isDirectory }.map { it.absolutePath }.sorted()
        check(directories.isNotEmpty()) { "No compiled main outputs for the exception architecture guard" }
        return listOf("-Dsummer.architecture.productionClasses=" + directories.joinToString(File.pathSeparator))
    }
}

gradle.projectsEvaluated {
    val mains = rootProject.allprojects.mapNotNull { module ->
        module.extensions.findByType<SourceSetContainer>()?.findByName("main")?.let { module to it }
    }
    val productionClasses = files(mains.map { (_, main) -> main.output.classesDirs })
    tasks.named<Test>("test") {
        dependsOn(mains.map { (module, main) -> module.tasks.named(main.classesTaskName) })
        // Load owned types from their declared outputs before dependency JAR copies.
        classpath = productionClasses + classpath
        jvmArgumentProviders.add(ProductionClassesArgumentProvider(productionClasses))
    }
}
