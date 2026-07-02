plugins {
    id("java")
}

group = "de.verdox.solarminer"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "verdoxRepositorySnapshots"
        url = uri("https://repo.verdox.de/snapshots")
    }
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
    implementation("com.intelligt.modbus:jlibmodbus:1.2.9.11") {
        exclude(group = "com.google.android.things", module = "androidthings")
    }

    implementation("com.google.guava:guava:33.3.1-jre")
    implementation("de.verdox:vserializer:1.1-SNAPSHOT")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}