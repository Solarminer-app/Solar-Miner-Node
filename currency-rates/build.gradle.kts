import org.gradle.kotlin.dsl.named
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.graalvm.buildtools.native")
}

description = "SolarMiner Currency Rates Service"

base {
    archivesName.set("currency-rates")
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
    val currencyRatesImage =
        providers.gradleProperty("currencyRatesImage")

    imageName.set(
        currencyRatesImage.map { image ->
            "$image:${project.version}"
        }
    )

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
