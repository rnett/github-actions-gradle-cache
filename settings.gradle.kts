pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://oss.sonatype.org/content/repositories/snapshots") {
            mavenContent { snapshotsOnly() }
        }
    }
}

rootProject.name = "github-actions-gradle-cache"

include("shared", "action", "action-post", "cache")