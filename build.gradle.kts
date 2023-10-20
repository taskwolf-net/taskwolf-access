plugins {
    id("java")
}

group = "net.taskwolf"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/TaskwolfNET/taskwolf-core")
        credentials {
            username = System.getenv("GITHUB_USERNAME") ?: providers.gradleProperty("githubUsername").get()
            password = System.getenv("GITHUB_ACCESS_TOKEN") ?: providers.gradleProperty("githubAccessToken").get()
        }
    }
}

dependencies {
    testCompileOnly(platform("org.junit:junit-bom:5.10.0"))
    testCompileOnly("org.junit.jupiter:junit-jupiter:5.10.0")

    compileOnly("net.taskwolf:core:1.0.0-SNAPSHOT")

    compileOnly("com.google.inject:guice:7.0.0")

    compileOnly("com.google.guava:guava:32.1.3-jre")

    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")

    compileOnly("com.datastax.oss:java-driver-core:4.17.0")

    compileOnly("org.json:json:20231013")
    compileOnly("commons-io:commons-io:2.14.0")

    compileOnly("org.springframework.boot:spring-boot-starter-web:3.1.5")

    compileOnly("io.jsonwebtoken:jjwt:0.12.3")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    val dependencies = configurations.runtimeClasspath.get().map(::zipTree)
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}