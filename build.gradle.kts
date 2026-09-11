import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.*

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    kotlin("plugin.jpa") version "2.4.10"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.sonarqube") version "7.5.0.8588"
    jacoco
}

group = "io.shopfast"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Contrato OpenAPI gerado em runtime (/v3/api-docs). E o que permite ao OWASP ZAP
    // descobrir sozinho rotas, metodos, query params e corpos no Modulo 3 (DAST).
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

    // NOTA DIDATICA: versoes propositalmente desatualizadas, com CVEs conhecidos.
    // Serao usadas no modulo de SCA (Dependency-Check / Trivy).
    implementation("org.apache.commons:commons-text:1.15.0")
    implementation("commons-io:commons-io:2.22.0")

    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget = JvmTarget.JVM_21
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

sonar {
    properties {
        // Fonte unica de verdade: sonar-project.properties.
        // O plugin Gradle NAO le esse arquivo sozinho (ele e do sonar-scanner CLI),
        // entao carregamos aqui explicitamente.
        val propsFile = rootProject.file("sonar-project.properties")
        if (propsFile.exists()) {
            val loaded = Properties()
            propsFile.reader(Charsets.UTF_8).use { loaded.load(it) }
            loaded.stringPropertyNames().forEach { key -> property(key, loaded.getProperty(key)) }
        }

        // Ambiente sobrescreve o arquivo (util em CI).
        System.getenv("SONAR_HOST_URL")?.let { property("sonar.host.url", it) }
    }
}
