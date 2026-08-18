plugins {
    `java-platform`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api(platform(libs.spring.cloud.bom))
    api(platform(libs.spring.modulith.bom))

    constraints {
        api(project(":summer-common"))
        api(project(":summer-mvc"))
        api(project(":summer-data-jpa"))
        api(project(":summer-security"))
        api(project(":summer-common-autoconfigure"))
        api(project(":summer-web-autoconfigure"))
        api(project(":summer-data-jpa-autoconfigure"))
        api(project(":summer-security-autoconfigure"))
        api(project(":summer-common-starter"))
        api(project(":summer-web-starter"))
        api(project(":summer-data-jpa-starter"))
        api(project(":summer-security-starter"))
        api(project(":summer-boot-starter"))
    }
}
