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
    api("org.springframework:spring-webmvc")
    api("jakarta.servlet:jakarta.servlet-api")
    api("jakarta.validation:jakarta.validation-api")
    api("tools.jackson.core:jackson-databind")

    testImplementation("org.springframework:spring-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("ch.qos.logback:logback-classic")
}
