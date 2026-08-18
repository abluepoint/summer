plugins {
    `java-library`
}

dependencies {
    api(platform(project(":summer-dependencies")))
    api(project(":summer-common-starter"))
    api(project(":summer-security-autoconfigure"))
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-security-oauth2-authorization-server")
    api("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
}
