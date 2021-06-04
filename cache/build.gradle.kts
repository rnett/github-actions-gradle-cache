plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-gradle-plugin`
    application
}

ext["pomName"] = "GH Action gradle cache"
description = "A gradle build cache service that uses GitHub Action's cache"

dependencies {
    implementation(project(":shared"))
    implementation("org.apache.httpcomponents:httpclient:4.5.13")
}

kotlin {
    target {
        compilations.all { kotlinOptions.jvmTarget = "1.8" }
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
        }
    }
    sourceSets.all {
        languageSettings {
            useExperimentalAnnotation("kotlin.contracts.ExperimentalContracts")
        }
    }
}

gradlePlugin {
    plugins {
        create("ghActionsCachePlugin") {
            id = "com.github.rnett.github-actions-build-cache"
            displayName = "GitHub Actions build cache plugin"
            description = "GitHub Actions build cache plugin"
            implementationClass = "com.rnett.actions.cache.GHActionsBuildCachePlugin"
        }
    }
}


application {
    mainClass.set("com.rnett.actions.cache.TestKt")
}

tasks.test {
    useJUnit()
}