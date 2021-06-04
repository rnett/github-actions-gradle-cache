import com.rnett.action.githubAction

plugins {
    kotlin("js")
    id("com.github.rnett.ktjs-github-action")
    id("com.github.gmazzo.buildconfig")
}

ext["pomName"] = "GH Action gradle cache post-run action"
description = "GitHub action to save the glob cache"

dependencies {
    implementation(project(":shared"))
}

kotlin {
    js(IR) {
        githubAction()
    }
}