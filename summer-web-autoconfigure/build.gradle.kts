plugins {
    `java-library`
}

dependencies {
    api(platform(project(":summer-dependencies")))
    annotationProcessor(platform(project(":summer-dependencies")))
    api(project(":summer-common"))
    api(project(":summer-mvc"))
    api("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
