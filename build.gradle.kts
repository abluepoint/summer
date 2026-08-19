import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

abstract class VerifyModuleBoundaries : DefaultTask() {
    @get:Input
    abstract val violations: ListProperty<String>

    @TaskAction
    fun verify() {
        val detectedViolations = violations.get()
        check(detectedViolations.isEmpty()) {
            "Illegal module dependencies detected:\n${detectedViolations.joinToString("\n")}"
        }
    }
}

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

    if (name != "summer-sample") {
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
    "summer-mvc-autoconfigure" to setOf("summer-dependencies", "summer-common", "summer-mvc"),
    "summer-data-jpa-autoconfigure" to setOf("summer-dependencies", "summer-common", "summer-data-jpa"),
    "summer-security-autoconfigure" to setOf("summer-dependencies", "summer-common", "summer-security"),
    "summer-common-starter" to setOf("summer-dependencies", "summer-common-autoconfigure"),
    "summer-mvc-starter" to setOf("summer-dependencies", "summer-common-starter", "summer-mvc-autoconfigure"),
    "summer-data-jpa-starter" to setOf("summer-dependencies", "summer-common-starter", "summer-data-jpa-autoconfigure"),
    "summer-security-starter" to setOf("summer-dependencies", "summer-common-starter", "summer-security-autoconfigure"),
    "summer-boot-starter" to setOf(
        "summer-dependencies",
        "summer-common-starter",
        "summer-mvc-starter",
        "summer-data-jpa-starter",
        "summer-security-starter",
    ),
    "summer-sample" to setOf("summer-dependencies", "summer-boot-starter", "summer-data-jpa"),
)

val verifyModuleBoundaries by tasks.registering(VerifyModuleBoundaries::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Fails the build when a project declares a dependency outside the approved module graph."
}

gradle.projectsEvaluated {
    val detectedViolations = allowedProjectDependencies.flatMap { (projectName, allowed) ->
        val declaredProjects = project(":$projectName").configurations
            .flatMap { configuration -> configuration.dependencies.withType(ProjectDependency::class.java) }
            .map { dependency -> dependency.path.removePrefix(":") }
            .toSet()

        declaredProjects
            .filterNot(allowed::contains)
            .sorted()
            .map { forbidden -> "$projectName -> $forbidden" }
    }
    verifyModuleBoundaries.configure {
        violations.set(detectedViolations)
    }
}

tasks.named("check") {
    dependsOn(verifyModuleBoundaries)
    dependsOn(subprojects.map { project -> "${project.path}:check" })
}
