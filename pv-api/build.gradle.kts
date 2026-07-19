plugins {
    id("java")
    id("maven-publish")
}

description = "SolarMiner PV API"

base {
    archivesName.set("pv-api")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }

    withSourcesJar()
}

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

            artifactId = "pv-api"

            pom {
                name.set("SolarMiner PV API")

                description.set(
                    "Shared API types for SolarMiner " +
                            "photovoltaic integrations."
                )

                url.set(
                    "https://github.com/" +
                            "Solarminer-app/Solar-Miner-Node"
                )

                licenses {
                    license {
                        name.set(
                            "GNU Affero General Public License v3.0"
                        )

                        url.set(
                            "https://github.com/" +
                                    "Solarminer-app/" +
                                    "Solar-Miner-Node/blob/master/LICENSE"
                        )
                    }
                }

                developers {
                    developer {
                        id.set("verdox")
                        name.set("Lukas Jonsson")
                        email.set("mail.ysp@web.de")
                    }
                }

                scm {
                    connection.set(
                        "scm:git:git://github.com/" +
                                "Solarminer-app/" +
                                "Solar-Miner-Node.git"
                    )

                    developerConnection.set(
                        "scm:git:ssh://github.com/" +
                                "Solarminer-app/" +
                                "Solar-Miner-Node.git"
                    )

                    url.set(
                        "https://github.com/" +
                                "Solarminer-app/Solar-Miner-Node"
                    )
                }
            }
        }
    }

    repositories {
        maven {
            name = "verdox"

            val snapshotsRepository =
                providers.gradleProperty(
                    "pvApiSnapshotsRepository"
                )

            val releasesRepository =
                providers.gradleProperty(
                    "pvApiReleasesRepository"
                )

            val isSnapshot =
                project.version
                    .toString()
                    .endsWith("-SNAPSHOT")

            url = uri(
                if (isSnapshot) {
                    snapshotsRepository.get()
                } else {
                    releasesRepository.get()
                }
            )

            credentials {
                username = providers
                    .environmentVariable("REPO_USER")
                    .orNull

                password = providers
                    .environmentVariable("REPO_PASSWORD")
                    .orNull
            }
        }
    }
}