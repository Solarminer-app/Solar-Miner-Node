//import com.google.protobuf.gradle.*

plugins {
    java
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.vaadin") version "24.6.5"
    id("org.graalvm.buildtools.native") version "0.10.6"
}

extra["vaadinVersion"] = "24.6.5"
group = "de.verdox"
version = "0.0.1-SNAPSHOT"

tasks.bootJar {
    layered {
        enabled = true
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor.set(JvmVendorSpec.GRAAL_VM)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.vaadin.com/vaadin-addons")
    maven {
        name = "verdoxRepositorySnapshots"
        url = uri("https://repo.verdox.de/snapshots")
    }
}



dependencies {
    implementation("com.openhtmltopdf:openhtmltopdf-core:1.0.10")
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.google.code.gson:gson:2.12.1")
    implementation("com.vaadin:vaadin-charts-flow")
    implementation("org.commonmark:commonmark:0.21.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:2.7.4")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework:spring-context")
    implementation("com.vaadin:vaadin-spring-boot-starter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    implementation("com.intelligt.modbus:jlibmodbus:1.2.9.11") {
        exclude(group = "com.google.android.things", module = "androidthings")
    }

    implementation(project(":proto"))
    implementation(project(":pv-api"))

    implementation("io.grpc:grpc-protobuf:1.50.2")
    implementation("io.grpc:grpc-stub:1.50.2")
    implementation("com.google.protobuf:protobuf-java:3.21.12")

    implementation("de.verdox:vserializer:1.1-SNAPSHOT")

    implementation("com.influxdb:influxdb-client-java:7.2.0")

    // Optional: Für Annotation-Kompatibilität (z. B. bei Java 9+)
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
dependencyManagement {
    imports {
        mavenBom("com.vaadin:vaadin-bom:${property("vaadinVersion")}")
    }
}

vaadin {
    optimizeBundle = true
    productionMode = true
}

tasks.withType<Test> {
    useJUnitPlatform()
}

graalvmNative {
    binaries {
        named("main") {
            buildArgs.add("-J-Xmx32G")
            buildArgs.add("--initialize-at-run-time=org.slf4j,ch.qos.logback,io.grpc.netty.shaded")
        }
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage") {
    imageName.set("verdox/solar-miner:latest")
    environment.put("BP_NATIVE_IMAGE_BUILD_ARGUMENTS", "-J-Xmx6G -march=compatibility --initialize-at-build-time=org.slf4j,ch.qos.logback --initialize-at-run-time=io.grpc,io.netty,io.grpc.netty.shaded --trace-class-initialization=io.grpc.netty.shaded.io.netty.buffer.PooledByteBufAllocator")
    publish.set(false)
}


val dockerImageName = "verdox/solar-miner"
val imageVersion = project.version.toString()
val versionTag = "$dockerImageName:$imageVersion"
val latestTag = "$dockerImageName:latest"

val buildDockerImage = tasks.register<Exec>("buildDockerImage") {
    dependsOn("bootJar")
    workingDir = project.rootDir
    commandLine("docker", "build", "-t", versionTag, "-t", latestTag, ".")
}

val pushDockerVersion = tasks.register<Exec>("pushDockerVersion") {
    dependsOn(buildDockerImage)
    commandLine("docker", "push", versionTag)
}

val pushDockerLatest = tasks.register<Exec>("pushDockerLatest") {
    dependsOn(pushDockerVersion)
    commandLine("docker", "push", latestTag)
}

tasks.register("buildAndPush") {
    group = "docker"
    description = "Builds and deploys to docker hub"
    dependsOn(pushDockerLatest)
}

