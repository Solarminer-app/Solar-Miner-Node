import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.graalvm.buildtools.native")
}

description = "SolarMiner Core"

base {
    archivesName.set("solar-miner-core")
}

springBoot {
    buildInfo()
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
    implementation(project(":cgminerapi"))
    implementation("io.netty:netty-handler:4.2.15.Final")

    implementation("io.grpc:grpc-okhttp:1.50.2")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation(project(":proto"))

    implementation("io.grpc:grpc-stub:1.45.1")
    implementation("com.google.protobuf:protobuf-java:3.21.12")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<BootBuildImage>("bootBuildImage") {
    val coreImage = providers.gradleProperty("coreImage")

    imageName.set(
        coreImage.map { image ->
            "$image:${project.version}"
        }
    )

    environment.put("BP_NATIVE_IMAGE_BUILD_ARGUMENTS", "-march=compatibility")
    docker {
        publishRegistry {
            username = System.getenv("DOCKER_USER")
            password = System.getenv("DOCKER_PASS")
        }
    }
}
