pluginManagement{
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://oss.sonatype.org/content/repositories/snapshots"){
            mavenContent { snapshotsOnly() }
        }
    }
}

buildCache {
    local {
        isEnabled = false
    }
}

rootProject.name = "test"