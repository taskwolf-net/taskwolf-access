plugins {
  id("java")
  id("io.freefair.lombok") version "8.13"
}

group = "com.dulno"
version = "1.0.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21
java.targetCompatibility = JavaVersion.VERSION_21

repositories {
  mavenCentral()
  mavenLocal()
}

dependencies {
  testCompileOnly(platform("org.junit:junit-bom:5.12.0"))
  testCompileOnly("org.junit.jupiter:junit-jupiter:5.12.0")

  compileOnly("com.dulno:core:1.0.0-SNAPSHOT")
  compileOnly("com.dulno:workflow:1.0.0-SNAPSHOT")
  compileOnly("com.dulno:table:1.0.0-SNAPSHOT")
  compileOnly("com.dulno:process:1.0.0-SNAPSHOT")
  compileOnly("com.dulno:webhook:1.0.0-SNAPSHOT")
  compileOnly("com.dulno:device:1.0.0-SNAPSHOT")

  compileOnly("com.google.inject:guice:7.0.0")

  compileOnly("com.google.guava:guava:33.4.0-jre")

  compileOnly("org.projectlombok:lombok:1.18.36")
  annotationProcessor("org.projectlombok:lombok:1.18.36")
  testCompileOnly("org.projectlombok:lombok:1.18.36")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.36")

  compileOnly("com.datastax.oss:java-driver-core:4.17.0")

  compileOnly("org.json:json:20250107")
  compileOnly("commons-io:commons-io:2.18.0")

  compileOnly("org.springframework.boot:spring-boot-starter-web:3.4.3")

  compileOnly("io.jsonwebtoken:jjwt:0.12.6")

  compileOnly("com.sun.mail:javax.mail:1.6.2")

  compileOnly("com.stripe:stripe-java:28.4.0")

  compileOnly("com.maxmind.geoip2:geoip2:4.2.1")

  compileOnly("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20240325.1")
}

tasks.test {
  useJUnitPlatform()
}

tasks.jar {
  val dependencies = configurations.runtimeClasspath.get().map(::zipTree)
  from(dependencies)
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}