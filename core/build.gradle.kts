import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    java
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.10.6"
}

group = "de.verdox.pv_miner"
version = "0.0.1-SNAPSHOT"
description = "core"

tasks.bootBuildImage {
    imageName.set("verdox/solar-miner-core:${project.version}")
    tags.add("verdox/solar-miner-core:latest")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

graalvmNative {
    binaries {
        named("main") {
            buildArgs.add("-H:+ReportExceptionStackTraces")
        }
    }
}

dependencies {
    implementation("io.netty:netty-handler:4.2.15.Final")

    implementation("io.grpc:grpc-okhttp:1.50.2")
    implementation("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation(project(":proto"))

    implementation("io.grpc:grpc-stub:1.45.1")
    implementation("com.google.protobuf:protobuf-java:3.21.12")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<BootBuildImage>("bootBuildImage") {
    environment.put("BP_NATIVE_IMAGE_BUILD_ARGUMENTS", "-march=compatibility")
    docker {
        publishRegistry {
            username = System.getenv("DOCKER_USER")
            password = System.getenv("DOCKER_PASS")
        }
    }
}
