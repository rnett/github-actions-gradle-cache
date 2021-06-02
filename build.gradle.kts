import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.10"
    application
//    `java-gradle-plugin`
}

group = "com.github.rnett.github-actions-gradle-cache"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-client:1.6.0")

    testImplementation(kotlin("test"))
}

kotlin{
    target{
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
        }
        sourceSets.all{

        }
    }
}

application{
    mainClass.set("com.rnett.actions.cache.TestKt")
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}