plugins {
    id("org.springframework.boot") version "3.1.4"
    id("io.spring.dependency-management") version "1.1.0"
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.spring") version "1.9.0"
    kotlin("plugin.jpa") version "1.9.0"
}

group = "itmo.deniill"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("ca.uhn.hapi:hapi-base:2.3") // HAPI HL7v2
    implementation("ca.uhn.hapi:hapi-structures-v23:2.3") // structures for ADT messages (v2.3)

    implementation("ca.uhn.hapi.fhir:hapi-fhir-base:6.10.0")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-structures-r4:6.10.0")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
