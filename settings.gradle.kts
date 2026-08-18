pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "summer"

include(
    "summer-dependencies",
    "summer-common",
    "summer-mvc",
    "summer-data-jpa",
    "summer-security",
    "summer-common-autoconfigure",
    "summer-web-autoconfigure",
    "summer-data-jpa-autoconfigure",
    "summer-security-autoconfigure",
    "summer-common-starter",
    "summer-web-starter",
    "summer-data-jpa-starter",
    "summer-security-starter",
    "summer-boot-starter",
    "summer-data-jpa-test",
)
