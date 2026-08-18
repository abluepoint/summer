plugins {
    `java-library`
}

dependencies {
    api(platform(project(":summer-dependencies")))
    annotationProcessor(platform(project(":summer-dependencies")))
    api(project(":summer-common"))
    api(project(":summer-security"))
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.security:spring-security-config")
    api("org.springframework:spring-webmvc")
    api("org.springframework.security:spring-security-oauth2-authorization-server")
    api("org.springframework.security:spring-security-oauth2-jose")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}
