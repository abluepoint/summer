plugins {
    `java-library`
}

dependencies {
    api(platform(project(":summer-dependencies")))
    api(project(":summer-common"))
    api("org.springframework.data:spring-data-jpa")
    api("jakarta.persistence:jakarta.persistence-api")
    api("org.freemarker:freemarker")
    api("org.springframework:spring-beans")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-core")
}
