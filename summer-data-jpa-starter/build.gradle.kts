plugins {
    `java-library`
}

dependencies {
    api(platform(project(":summer-dependencies")))
    api(project(":summer-common-starter"))
    api(project(":summer-data-jpa-autoconfigure"))
    api("org.springframework.boot:spring-boot-starter-data-jpa")
}
