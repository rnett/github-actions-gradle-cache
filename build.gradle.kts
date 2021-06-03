import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.10" apply false
    kotlin("js") version "1.5.10" apply false
    kotlin("multiplatform") version "1.5.10" apply false
    id("com.github.rnett.ktjs-github-action") version "1.2.1-SNAPSHOT" apply false
}

allprojects {
    group = "com.github.rnett.github-actions-gradle-cache"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots"){
            mavenContent { snapshotsOnly() }
        }
        jcenter()
    }

}