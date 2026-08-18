plugins {
    `java-library`
}

sourceSets {
    named("main") {
        java.setSrcDirs(listOf("src/boot4/java"))
        resources.setSrcDirs(listOf("src/boot4/resources"))
    }
    named("test") {
        java.setSrcDirs(listOf("src/boot4Test/java"))
        resources.setSrcDirs(listOf("src/boot4Test/resources"))
    }
}

dependencies {
    api(platform(project(":summer-dependencies")))
    api(project(":summer-common"))
    api("org.springframework.security:spring-security-web")
    api("org.springframework.security:spring-security-oauth2-core")
    api("org.springframework.security:spring-security-oauth2-resource-server")
    api("tools.jackson.core:jackson-databind")
    api("jakarta.servlet:jakarta.servlet-api")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}
