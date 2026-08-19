plugins {
    `java-library`
}

dependencies {
    api(platform(project(":summer-dependencies")))
    api("org.springframework:spring-context")
    api("org.slf4j:slf4j-api")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}
