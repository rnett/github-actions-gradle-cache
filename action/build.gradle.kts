import com.rnett.action.githubAction

plugins{
    kotlin("js")
    id("com.github.rnett.ktjs-github-action")
}

dependencies{
    implementation(project(":shared"))
    implementation("com.github.rnett.ktjs-github-action:kotlin-js-action:1.2.0-SNAPSHOT")
}

kotlin {
    js(IR) {
        githubAction()
    }
}