import com.rnett.action.githubAction

plugins {
    kotlin("js")
    id("com.github.rnett.ktjs-github-action")
    id("com.github.gmazzo.buildconfig")
}

ext["pomName"] = "GH Action gradle cache setup action"
description = "GitHub action to set up GitHub Actions gradle build cache"

dependencies {
    implementation(project(":shared"))
}

buildConfig {
    packageName("com.rnett.actions.cache")
    buildConfigField("String", "VERSION", "\"${version.toString()}\"")
}

kotlin {
    js(IR) {
        githubAction()
    }
}