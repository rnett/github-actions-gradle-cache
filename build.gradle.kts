plugins {
    kotlin("jvm") version "1.5.10" apply false
    kotlin("js") version "1.5.10" apply false
    kotlin("multiplatform") version "1.5.10" apply false
    kotlin("plugin.serialization") version "1.5.10" apply false
    id("com.github.rnett.ktjs-github-action") version "1.2.1-SNAPSHOT" apply false
    id("org.jetbrains.dokka") version "1.4.32" apply false
    id("com.vanniktech.maven.publish") version "0.15.1" apply false
    id("com.github.gmazzo.buildconfig") version "3.0.0" apply false
}

allprojects {
    group = "com.github.rnett.github-actions-gradle-cache"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots") {
            mavenContent { snapshotsOnly() }
        }
        jcenter()
    }
}

subprojects {
    afterEvaluate {
        apply(plugin = "org.jetbrains.dokka")
        apply(plugin = "org.gradle.maven-publish")
        apply(plugin = "com.vanniktech.maven.publish")
        val project = this

        extensions.getByType<com.vanniktech.maven.publish.MavenPublishBaseExtension>().apply {
            if (!version.toString().toLowerCase().endsWith("snapshot")) {
                val stagingProfileId = project.findProperty("sonatypeRepositoryId")?.toString()
                publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.DEFAULT, stagingProfileId)
            }

            pom {
                name.set(project.ext["pomName"].toString())
                description.set(project.description)
                inceptionYear.set("2021")
                url.set("https://github.com/rnett/github-actions-gradle-cache/")

                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                scm {
                    url.set("https://github.com/rnett/github-actions-gradle-cache.git")
                    connection.set("scm:git:git://github.com/rnett/github-actions-gradle-cache.git")
                    developerConnection.set("scm:git:ssh://git@github.com/rnett/github-actions-gradle-cache.git")
                }

                developers {
                    developer {
                        id.set("rnett")
                        name.set("Ryan Nett")
                        url.set("https://github.com/rnett/")
                    }
                }
            }
        }
    }
}