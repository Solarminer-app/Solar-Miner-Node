import com.google.protobuf.gradle.*

plugins {
    id("java")
    id("com.google.protobuf") version "0.9.4"
}

group = "de.verdox.pv-miner"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()  // Stellt sicher, dass ein JAR mit Quellen generiert wird
    withJavadocJar()  // Falls du Javadoc benötigst
}

dependencies {
    // gRPC Abhängigkeiten
    implementation("io.grpc:grpc-protobuf:1.45.1")
    implementation("io.grpc:grpc-stub:1.45.1")

    if (JavaVersion.current().isJava9Compatible()) {
        // Workaround for @javax.annotation.Generated
        // see: https://github.com/grpc/grpc-java/issues/3633
        implementation("javax.annotation:javax.annotation-api:1.3.1")
    }

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.named("sourcesJar") {
    dependsOn("generateProto")
}

tasks.test {
    useJUnitPlatform()
}

protobuf {
    // Legt den Protoc-Compiler fest
    protoc {
        artifact = "com.google.protobuf:protoc:3.19.1"
    }
    // Konfiguriert das gRPC-Plugin für protoc
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.45.1"
        }
    }
    // Wendet das gRPC-Plugin auf alle generierten Proto-Aufgaben an
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
            }
        }
    }
}


sourceSets {
    main {
        java {
            srcDir("build/generated/source/proto/main/java")
            srcDir("build/generated/source/proto/main/grpc")
        }
    }
}