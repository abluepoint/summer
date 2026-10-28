plugins {
    `java-library`
}

dependencies {
    api(platform(project(":summer-dependencies")))
    api(project(":summer-common"))
    api("org.springframework.security:spring-security-web")
    api("org.springframework.security:spring-security-config")
    api("org.springframework.security:spring-security-oauth2-core")
    api("org.springframework.security:spring-security-oauth2-authorization-server")
    api("org.springframework.security:spring-security-oauth2-jose")
    api("org.springframework:spring-webmvc")
    api("org.springframework.security:spring-security-oauth2-resource-server")
    api("tools.jackson.core:jackson-databind")
    api("jakarta.servlet:jakarta.servlet-api")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.springframework:spring-test")
}
