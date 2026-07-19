import com.github.gradle.node.npm.task.NpmTask
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    java
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.10.6" apply false
    id("com.github.node-gradle.node") version "7.1.0"
}

val projectGroup = providers.gradleProperty("group")

val frontendVersion = providers.gradleProperty("frontendVersion")
val coreVersion = providers.gradleProperty("coreVersion")
val currencyRatesVersion = providers.gradleProperty("currencyRatesVersion")
val pvApiVersion = providers.gradleProperty("pvApiVersion")

val frontendImage = providers.gradleProperty("frontendImage")
val coreImage = providers.gradleProperty("coreImage")
val currencyRatesImage = providers.gradleProperty("currencyRatesImage")

allprojects {
    group = projectGroup.get()

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-parameters")
    }
}

version = frontendVersion.get()
description = "SolarMiner Frontend"

base {
    archivesName.set("solar-miner")
}

project(":core") {
    version = coreVersion.get()
}

project(":currency-rates") {
    version = currencyRatesVersion.get()
}

project(":pv-api") {
    version = pvApiVersion.get()
}

springBoot {
    buildInfo()
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

    maven {
        name = "verdoxRepositorySnapshots"
        url = uri("https://repo.verdox.de/snapshots")
    }
}

dependencies {
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")
    implementation("com.hivemq:hivemq-mqtt-client:1.3.14")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.shredzone.commons:commons-suncalc:3.5")
    implementation("com.openhtmltopdf:openhtmltopdf-core:1.0.10")
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")

    implementation(
        "org.springframework.boot:spring-boot-starter-actuator"
    )
    implementation(
        "org.springframework.boot:spring-boot-starter-data-jpa"
    )
    implementation(
        "org.springframework.boot:spring-boot-starter-web"
    )
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")
    implementation("org.springframework:spring-context")

    implementation("io.projectreactor:reactor-core")
    implementation("com.google.code.gson:gson:2.12.1")
    implementation("org.commonmark:commonmark:0.21.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:2.7.4")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    implementation("com.intelligt.modbus:jlibmodbus:1.2.9.11") {
        exclude(
            group = "com.google.android.things",
            module = "androidthings"
        )
    }

    implementation(project(":proto"))
    implementation(project(":pv-api"))

    implementation("io.grpc:grpc-protobuf:1.50.2")
    implementation("io.grpc:grpc-stub:1.50.2")
    implementation("com.google.protobuf:protobuf-java:3.21.12")

    implementation("de.verdox:vserializer:1.1-SNAPSHOT")
    implementation("com.influxdb:influxdb-client-java:7.2.0")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(
        "org.springframework.boot:spring-boot-starter-test"
    )
    testRuntimeOnly(
        "org.junit.platform:junit-platform-launcher"
    )
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.bootJar {
    layered {
        enabled = true
    }
}

val reactFrontendDirectory =
    layout.projectDirectory.dir("react-frontend")

val reactFrontendOutput =
    layout.buildDirectory.dir("generated/react-frontend")

node {
    version.set("22.14.0")
    download.set(true)
    nodeProjectDir.set(reactFrontendDirectory.asFile)
}

val installReactFrontend by tasks.registering(NpmTask::class) {
    group = "frontend"
    description = "Installs the locked React frontend dependencies."

    args.set(
        listOf(
            "ci",
            "--no-audit",
            "--no-fund"
        )
    )

    inputs.files(
        reactFrontendDirectory.file("package.json"),
        reactFrontendDirectory.file("package-lock.json")
    )

    outputs.file(
        reactFrontendDirectory.file(
            "node_modules/.package-lock.json"
        )
    )
}

val buildReactFrontend by tasks.registering(NpmTask::class) {
    group = "frontend"
    description =
        "Builds the React SPA that Spring Boot serves on port 8080."

    dependsOn(installReactFrontend)

    args.set(
        listOf(
            "run",
            "build"
        )
    )

    inputs.files(
        reactFrontendDirectory.file("package.json"),
        reactFrontendDirectory.file("package-lock.json"),
        reactFrontendDirectory.file("index.html"),
        reactFrontendDirectory.file("vite.config.ts"),
        reactFrontendDirectory.file("postcss.config.mjs")
    )

    inputs.dir(reactFrontendDirectory.dir("app"))
    inputs.dir(reactFrontendDirectory.dir("public"))
    inputs.dir(reactFrontendDirectory.dir("spa"))

    outputs.dir(reactFrontendOutput)
}

tasks.processResources {
    dependsOn(buildReactFrontend)

    from(reactFrontendOutput) {
        into("static")
    }

    from(layout.projectDirectory.dir("device-profiles/bundled")) {
        into("device-profiles/bundled")
    }
}

/*graalvmNative {
    binaries {
        named("main") {
            buildArgs.add("-J-Xmx32G")
            buildArgs.add(
                "--initialize-at-run-time=" +
                        "org.slf4j," +
                        "ch.qos.logback," +
                        "io.grpc.netty.shaded"
            )
        }
    }
}*/

tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set(
        frontendImage.map { image ->
            "$image:${project.version}"
        }
    )

    docker {
        publishRegistry {
            username = System.getenv("DOCKER_USER")
            password = System.getenv("DOCKER_PASS")
        }
    }

/*    environment.put(
        "BP_NATIVE_IMAGE_BUILD_ARGUMENTS",
        "-J-Xmx6G " +
                "-march=compatibility " +
                "--initialize-at-build-time=" +
                "org.slf4j,ch.qos.logback " +
                "--initialize-at-run-time=" +
                "io.grpc,io.netty,io.grpc.netty.shaded " +
                "--trace-class-initialization=" +
                "io.grpc.netty.shaded.io.netty.buffer." +
                "PooledByteBufAllocator"
    )*/

    publish.set(false)
}

tasks.register("printVersion") {
    group = "versioning"
    description =
        "Prints the frontend version for backwards compatibility."

    doLast {
        println(frontendVersion.get())
    }
}

tasks.register("printFrontendVersion") {
    group = "versioning"
    description = "Prints the frontend version."

    doLast {
        println(frontendVersion.get())
    }
}

tasks.register("printCoreVersion") {
    group = "versioning"
    description = "Prints the SolarMiner Core version."

    doLast {
        println(coreVersion.get())
    }
}

tasks.register("printCurrencyRatesVersion") {
    group = "versioning"
    description = "Prints the currency-rates service version."

    doLast {
        println(currencyRatesVersion.get())
    }
}

tasks.register("printPvApiVersion") {
    group = "versioning"
    description = "Prints the PV API version."

    doLast {
        println(pvApiVersion.get())
    }
}

tasks.register("printFrontendImage") {
    group = "versioning"
    description = "Prints the frontend Docker image repository."

    doLast {
        println(frontendImage.get())
    }
}

tasks.register("printCoreImage") {
    group = "versioning"
    description = "Prints the Core Docker image repository."

    doLast {
        println(coreImage.get())
    }
}

tasks.register("printCurrencyRatesImage") {
    group = "versioning"
    description =
        "Prints the currency-rates Docker image repository."

    doLast {
        println(currencyRatesImage.get())
    }
}
