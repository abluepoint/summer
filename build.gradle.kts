import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

data class ModuleBoundaryDefinition(
    val name: String,
    val responsibility: String,
    val allowedProjectDependencies: Set<String>,
)

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

fun findDependencyCycles(graph: Map<String, Set<String>>): List<String> {
    val state = mutableMapOf<String, Int>()
    val stack = mutableListOf<String>()
    val cycles = linkedSetOf<String>()
    lateinit var visit: (String) -> Unit

    visit = { module ->
        when (state[module]) {
            1 -> {
                val start = stack.indexOf(module)
                cycles += (stack.subList(start, stack.size) + module).joinToString(" -> ")
            }
            2 -> Unit
            else -> {
                state[module] = 1
                stack += module
                graph[module].orEmpty().sorted().forEach { dependency ->
                    if (dependency in graph) visit(dependency)
                }
                stack.removeAt(stack.lastIndex)
                state[module] = 2
            }
        }
    }

    graph.keys.sorted().forEach(visit)
    return cycles.toList()
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

val moduleDefinitions = listOf(
    ModuleBoundaryDefinition("summer-dependencies", "dependency platform", emptySet()),
    ModuleBoundaryDefinition("summer-common", "foundation capability contracts", setOf("summer-dependencies")),
    ModuleBoundaryDefinition("summer-mvc", "HTTP parameter, validation and response capability", setOf("summer-dependencies", "summer-common")),
    ModuleBoundaryDefinition("summer-data-jpa", "repository and SQL template capability", setOf("summer-dependencies", "summer-common")),
    ModuleBoundaryDefinition("summer-security", "security protocol and principal capability", setOf("summer-dependencies", "summer-common")),
    ModuleBoundaryDefinition("summer-common-autoconfigure", "Common conditional assembly", setOf("summer-dependencies", "summer-common")),
    ModuleBoundaryDefinition("summer-mvc-autoconfigure", "MVC conditional assembly", setOf("summer-dependencies", "summer-common", "summer-mvc")),
    ModuleBoundaryDefinition("summer-data-jpa-autoconfigure", "Data JPA conditional assembly", setOf("summer-dependencies", "summer-common", "summer-data-jpa")),
    ModuleBoundaryDefinition("summer-security-autoconfigure", "Security conditional assembly", setOf("summer-dependencies", "summer-common", "summer-security")),
    ModuleBoundaryDefinition("summer-common-starter", "Common dependency entry point", setOf("summer-dependencies", "summer-common-autoconfigure")),
    ModuleBoundaryDefinition("summer-mvc-starter", "MVC dependency entry point", setOf("summer-dependencies", "summer-common-starter", "summer-mvc-autoconfigure")),
    ModuleBoundaryDefinition("summer-data-jpa-starter", "Data JPA dependency entry point", setOf("summer-dependencies", "summer-common-starter", "summer-data-jpa-autoconfigure")),
    ModuleBoundaryDefinition("summer-security-starter", "Security dependency entry point", setOf("summer-dependencies", "summer-common-starter", "summer-security-autoconfigure")),
    ModuleBoundaryDefinition("summer-boot-starter", "aggregate dependency entry point", setOf(
        "summer-dependencies",
        "summer-common-starter",
        "summer-mvc-starter",
        "summer-data-jpa-starter",
        "summer-security-starter",
    )),
    ModuleBoundaryDefinition("summer-sample", "reference application and integration evidence", setOf("summer-dependencies", "summer-boot-starter", "summer-data-jpa")),
)

val allowedProjectDependencies = moduleDefinitions.associate { it.name to it.allowedProjectDependencies }

val verifyModuleBoundaries by tasks.registering(VerifyModuleBoundaries::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Fails the build when module registration, dependencies or cycles violate the approved module graph."
}

gradle.projectsEvaluated {
    val expectedModules = moduleDefinitions.map { it.name }.toSet()
    val declaredProjects = subprojects.associateBy { it.name }
    val discoveredModuleDirectories = rootDir.listFiles()
        ?.asSequence()
        ?.filter { candidate ->
            candidate.isDirectory && (
                candidate.resolve("build.gradle.kts").isFile ||
                    candidate.resolve("build.gradle").isFile
                )
        }
        ?.map { it.name }
        ?.toSet()
        .orEmpty()
    val detectedViolations = mutableListOf<String>()

    detectedViolations += moduleDefinitions
        .groupingBy { it.name }
        .eachCount()
        .filter { (_, count) -> count > 1 }
        .keys
        .sorted()
        .map { "Module is registered more than once in the boundary catalog: $it" }
    if (moduleDefinitions.size != 15) {
        detectedViolations += "Expected 15 registered modules but found ${moduleDefinitions.size}"
    }
    detectedViolations += moduleDefinitions
        .filter { it.responsibility.isBlank() }
        .map { "Module ${it.name} has no declared responsibility" }
    detectedViolations += moduleDefinitions
        .flatMap { definition ->
            (definition.allowedProjectDependencies - expectedModules).map { dependency ->
                "Module ${definition.name} allows an unregistered dependency: $dependency"
            }
        }
    detectedViolations += expectedModules
        .filterNot(declaredProjects::containsKey)
        .sorted()
        .map { "Module is registered in the boundary catalog but missing from settings: $it" }
    detectedViolations += declaredProjects.keys
        .filterNot(expectedModules::contains)
        .sorted()
        .map { "Unregistered Gradle module: $it" }
    detectedViolations += discoveredModuleDirectories
        .filterNot(declaredProjects::containsKey)
        .sorted()
        .map { "Module directory is not registered in settings.gradle.kts: $it" }

    val dependencyGraph = declaredProjects.mapValues { (_, project) ->
        project.configurations
            .flatMap { configuration -> configuration.dependencies.withType(ProjectDependency::class.java) }
            .map { dependency -> dependency.path.removePrefix(":") }
            .toSet()
    }
    dependencyGraph.forEach { (projectName, declaredDependencies) ->
        val allowed = allowedProjectDependencies[projectName].orEmpty()
        detectedViolations += declaredDependencies
            .filterNot(allowed::contains)
            .sorted()
            .map { forbidden ->
                "Illegal project dependency: $projectName -> $forbidden (allowed: ${allowed.sorted().joinToString(", ")})"
            }
    }
    detectedViolations += findDependencyCycles(dependencyGraph)
        .map { cycle -> "Dependency cycle detected: $cycle" }
    check(findDependencyCycles(mapOf("a" to setOf("b"), "b" to setOf("a"))) == listOf("a -> b -> a")) {
        "Dependency-cycle detector failed its positive case"
    }
    check(findDependencyCycles(mapOf("a" to setOf("b"), "b" to emptySet())).isEmpty()) {
        "Dependency-cycle detector reported a false positive"
    }
    verifyModuleBoundaries.configure {
        violations.set(detectedViolations)
    }
}

tasks.named("check") {
    dependsOn(verifyModuleBoundaries)
    dependsOn(subprojects.map { project -> "${project.path}:check" })
}
