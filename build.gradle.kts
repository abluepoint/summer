import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    base
    alias(libs.plugins.spring.boot) apply false
}

allprojects {
    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()
}

subprojects {
    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
            withSourcesJar()
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(libs.versions.java.get().toInt())
            options.compilerArgs.addAll(listOf("-parameters", "-Xlint:deprecation", "-Xlint:unchecked"))
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
            }
        }

        dependencies.add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    if (name != "summer-data-jpa-test") {
        pluginManager.apply("maven-publish")

        pluginManager.withPlugin("java") {
            extensions.configure<PublishingExtension> {
                publications.create<MavenPublication>("mavenJava") {
                    from(components["java"])
                }
            }
        }

        pluginManager.withPlugin("java-platform") {
            extensions.configure<PublishingExtension> {
                publications.create<MavenPublication>("mavenPlatform") {
                    from(components["javaPlatform"])
                }
            }
        }
    }
}

val allowedProjectDependencies = mapOf(
    "summer-dependencies" to emptySet(),
    "summer-common" to setOf("summer-dependencies"),
    "summer-mvc" to setOf("summer-dependencies", "summer-common"),
    "summer-data-jpa" to setOf("summer-dependencies", "summer-common"),
    "summer-security" to setOf("summer-dependencies", "summer-common"),
    "summer-common-autoconfigure" to setOf("summer-dependencies", "summer-common"),
    "summer-web-autoconfigure" to setOf("summer-dependencies", "summer-common", "summer-mvc"),
    "summer-data-jpa-autoconfigure" to setOf("summer-dependencies", "summer-common", "summer-data-jpa"),
    "summer-security-autoconfigure" to setOf("summer-dependencies", "summer-common", "summer-security"),
    "summer-common-starter" to setOf("summer-dependencies", "summer-common-autoconfigure"),
    "summer-web-starter" to setOf("summer-dependencies", "summer-common-starter", "summer-web-autoconfigure"),
    "summer-data-jpa-starter" to setOf("summer-dependencies", "summer-common-starter", "summer-data-jpa-autoconfigure"),
    "summer-security-starter" to setOf("summer-dependencies", "summer-common-starter", "summer-security-autoconfigure"),
    "summer-boot-starter" to setOf(
        "summer-dependencies",
        "summer-common-starter",
        "summer-web-starter",
        "summer-data-jpa-starter",
        "summer-security-starter",
    ),
    "summer-data-jpa-test" to setOf("summer-dependencies", "summer-boot-starter", "summer-data-jpa"),
)

val verifyModuleBoundaries by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Fails the build when a project declares a dependency outside the approved module graph."

    doLast {
        val violations = mutableListOf<String>()

        allowedProjectDependencies.forEach { (projectName, allowed) ->
            val module = project(":$projectName")
            val declaredProjects = module.configurations
                .flatMap { configuration -> configuration.dependencies.withType(ProjectDependency::class.java) }
                .map { dependency -> dependency.path.removePrefix(":") }
                .toSet()

            declaredProjects
                .filterNot(allowed::contains)
                .sorted()
                .forEach { forbidden -> violations += "$projectName -> $forbidden" }
        }

        check(violations.isEmpty()) {
            "Illegal module dependencies detected:\n${violations.joinToString("\n")}"
        }
    }
}

tasks.named("check") {
    dependsOn(verifyModuleBoundaries)
    dependsOn(subprojects.map { project -> "${project.path}:check" })
}
