plugins {
    `java-library`
}

dependencies {
    api(platform(project(":summer-dependencies")))
    api(project(":summer-common-starter"))
    api(project(":summer-mvc-starter"))
    api(project(":summer-data-jpa-starter"))
    api(project(":summer-security-starter"))
    api("org.springframework.boot:spring-boot-starter-actuator")
}
