plugins {
    `java-library`
}

dependencies {
    api(platform(project(":summer-dependencies")))
    api(project(":summer-common-autoconfigure"))
    api("org.springframework.boot:spring-boot-starter")
}
