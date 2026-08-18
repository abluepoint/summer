plugins {
    `java-library`
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src/boot4/java"))
        resources.setSrcDirs(listOf("src/boot4/resources"))
    }
    test {
        java.setSrcDirs(listOf("src/boot4Test/java"))
        resources.setSrcDirs(listOf("src/boot4Test/resources"))
    }
}

dependencies {
    api(platform(project(":summer-dependencies")))
    api("org.springframework:spring-context")
    api("org.slf4j:slf4j-api")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}
