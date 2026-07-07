plugins {
    id("java")
    id("maven-publish")
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                url = "https://github.com/Solarminer-app/Solar-Miner-Node"
                licenses {
                    license {
                        name = "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
                        url = "https://github.com/Solarminer-app/Solar-Miner-Node?tab=AGPL-3.0-1-ov-file"
                    }
                }
                developers {
                    developer {
                        id = "verdox"
                        name = "Lukas Jonsson"
                        email = "mail.ysp@web.de"
                    }
                }
            }
        }
    }
/*    repositories {
        maven {
            name = "verdox"
            url = uri("https://repo.verdox.de/snapshots")
            credentials {
                username = (findProperty("reposilite.verdox.user") ?: System.getenv("REPO_USER")).toString()
                password = (findProperty("reposilite.verdox.key") ?: System.getenv("REPO_PASSWORD")).toString()
            }
        }
    }*/
}