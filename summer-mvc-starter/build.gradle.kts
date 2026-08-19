plugins {
    `java-library`
}

dependencies {
    api(platform(project(":summer-dependencies")))
    api(project(":summer-common-starter"))
    api(project(":summer-mvc-autoconfigure"))
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-validation")
}
