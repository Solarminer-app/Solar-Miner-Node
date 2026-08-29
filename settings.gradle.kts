plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "pv-miner"

include("core")
include("proto")
include("currency-rates")
include("pc-agent")

include("cgminerapi")
include("pv-api")