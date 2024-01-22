plugins {
  id("java")
}

group = "net.taskwolf"
version = "1.0.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_20
java.targetCompatibility = JavaVersion.VERSION_20

repositories {
  mavenCentral()
  maven {
    name = "GitHubPackages"
    url = uri("https://maven.pkg.github.com/TaskwolfNET/taskwolf-core")
    credentials {
      username = System.getenv("GITHUB_USERNAME")
        ?: providers.gradleProperty("githubUsername").get()
      password = System.getenv("GITHUB_ACCESS_TOKEN")
        ?: providers.gradleProperty("githubAccessToken").get()
    }
  }
}

dependencies {
  testCompileOnly(platform("org.junit:junit-bom:5.10.1"))
  testCompileOnly("org.junit.jupiter:junit-jupiter:5.10.1")

  compileOnly("net.taskwolf:core:1.0.0-SNAPSHOT")

  compileOnly("com.google.inject:guice:7.0.0")

  compileOnly("com.google.guava:guava:33.0.0-jre")

  compileOnly("org.projectlombok:lombok:1.18.30")
  annotationProcessor("org.projectlombok:lombok:1.18.30")
  testCompileOnly("org.projectlombok:lombok:1.18.30")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.30")

  compileOnly("com.datastax.oss:java-driver-core:4.17.0")

  compileOnly("org.json:json:20231013")
  compileOnly("commons-io:commons-io:2.15.1")

  compileOnly("org.redisson:redisson:3.26.0")

  compileOnly("org.springframework.boot:spring-boot-starter-web:3.2.1")

  compileOnly("io.jsonwebtoken:jjwt:0.12.3")

  compileOnly("com.sun.mail:javax.mail:1.6.2")
}

tasks.test {
  useJUnitPlatform()
}

tasks.jar {
  val dependencies = configurations.runtimeClasspath.get().map(::zipTree)
  from(dependencies)
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}