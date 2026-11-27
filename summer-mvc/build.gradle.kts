plugins {
    `java-library`
}

dependencies {
    api(platform(project(":summer-dependencies")))
    api(project(":summer-common"))
    api("org.springframework:spring-webmvc")
    api("jakarta.servlet:jakarta.servlet-api")
    api("jakarta.validation:jakarta.validation-api")
    api("tools.jackson.core:jackson-databind")

    compileOnly("org.springframework.security:spring-security-web")

    testImplementation("org.springframework.security:spring-security-web")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.hibernate.validator:hibernate-validator")
    testRuntimeOnly("ch.qos.logback:logback-classic")
}
