import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    java
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.10.6" apply false
}

group = "de.verdox.solarminer"
version = providers.gradleProperty("pcAgentVersion").orElse("0.0.1-SNAPSHOT").get()
description = "pc-agent"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val proxyProjectDir = providers.gradleProperty("solarminer.proxy.project-dir")
    .map { rootProject.file(it) }
    .orElse(rootProject.projectDir.parentFile.resolve("solarminer-stratum-proxy"))
    .get()

val mainSourceSet = sourceSets.getByName("main")
val embeddedProxy = sourceSets.create("embeddedProxy") {
    java.srcDir(proxyProjectDir.resolve("src/main/java"))
    resources.setSrcDirs(emptyList<String>())
    compileClasspath += mainSourceSet.output + mainSourceSet.compileClasspath
}

val validateEmbeddedProxySource = tasks.register("validateEmbeddedProxySource") {
    doLast {
        if (!proxyProjectDir.resolve("src/main/java").isDirectory) {
            throw GradleException("Stratum proxy source is required for PC-Agent builds: $proxyProjectDir")
        }
    }
}
tasks.named(embeddedProxy.compileJavaTaskName) {
    dependsOn(validateEmbeddedProxySource)
}

configurations[embeddedProxy.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[embeddedProxy.compileOnlyConfigurationName].extendsFrom(configurations.compileOnly.get())
configurations[embeddedProxy.annotationProcessorConfigurationName].extendsFrom(configurations.annotationProcessor.get())

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")
    implementation("com.github.oshi:oshi-core:7.3.1")
    implementation("org.apache.commons:commons-compress:1.28.0")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
    add(embeddedProxy.implementationConfigurationName, "io.netty:netty-handler:4.2.15.Final")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<BootJar>("standaloneJar") {
    group = "distribution"
    description = "Builds one executable PC-Agent JAR with the embedded Stratum proxy"
    dependsOn(tasks.named("classes"))
    dependsOn(tasks.named(embeddedProxy.classesTaskName))
    archiveFileName.set("solarminer-pc-agent-standalone.jar")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    mainClass.set("de.verdox.solarminer.pcagent.PcAgentApplication")
    targetJavaVersion.set(JavaVersion.VERSION_21)
    classpath = mainSourceSet.runtimeClasspath + embeddedProxy.runtimeClasspath
}

tasks.named<BootRun>("bootRun") {
    group = "application"
    description = "Runs the PC-Agent with the bundled proxy available for local proxy mode"
    dependsOn(tasks.named("classes"), tasks.named(embeddedProxy.classesTaskName))
    mainClass.set("de.verdox.solarminer.pcagent.PcAgentApplication")
    classpath = mainSourceSet.runtimeClasspath + embeddedProxy.runtimeClasspath
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
    workingDir = rootProject.projectDir
    args("--solarminer.agent.standalone=true")
}
