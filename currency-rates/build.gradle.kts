import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.named
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    java
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.hibernate.orm") version "6.6.9.Final"
    id("org.graalvm.buildtools.native") version "0.9.28"
}

group = "de.verdox.currency-rates"
version = "0.0.1-SNAPSHOT"
description = "currency-rates"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")

    runtimeOnly("com.h2database:h2")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<BootBuildImage>("bootBuildImage") {
    environment.put("BP_NATIVE_IMAGE", "true")
    environment.put(
        "BP_NATIVE_IMAGE_BUILD_ARGUMENTS",
        "-march=compatibility --initialize-at-run-time=sun.security.util.Password,sun.security.util.Password\$ConsoleHolder"
    )
    docker {
        publishRegistry {
            username = System.getenv("DOCKER_USER")
            password = System.getenv("DOCKER_PASS")
        }
    }
}
