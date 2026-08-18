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
    api("org.springframework.data:spring-data-jpa")
    api("jakarta.persistence:jakarta.persistence-api")
    api("org.freemarker:freemarker")
    api("org.springframework:spring-beans")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-core")
}
