plugins {
    id("org.springframework.boot") version "3.1.4"
    id("io.spring.dependency-management") version "1.1.0"
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.spring") version "1.9.0"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("ca.uhn.hapi:hapi-base:2.3")
    implementation("ca.uhn.hapi:hapi-structures-v23:2.3")

    // fhir
    implementation("ca.uhn.hapi.fhir:hapi-fhir-base:6.6.0")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-structures-r4:6.6.0")

    implementation("org.apache.httpcomponents.client5:httpclient5:5.3")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
