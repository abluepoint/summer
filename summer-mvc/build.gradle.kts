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

    testImplementation("org.springframework:spring-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("ch.qos.logback:logback-classic")
}
