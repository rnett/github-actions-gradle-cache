plugins {
    kotlin("multiplatform")
}

ext["pomName"] = "GH Action gradle cache shared constants"
description = "Shared constants between the cache setup action and the cache itself"

kotlin {
    jvm {
        compilations.all { kotlinOptions.jvmTarget = "1.8" }
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
        }
    }
    js(IR) {
        nodejs()
    }
    sourceSets {
        val jsMain by getting {
            dependencies {
                api("com.github.rnett.ktjs-github-action:kotlin-js-action:1.2.1-SNAPSHOT")
            }
        }
    }
}