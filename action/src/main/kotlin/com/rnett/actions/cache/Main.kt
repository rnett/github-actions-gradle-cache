package com.rnett.actions.cache

import com.rnett.action.Path
import com.rnett.action.core.env
import com.rnett.action.core.inputs
import com.rnett.action.core.maskSecret
import com.rnett.action.core.runOrFail

private fun getInitScript(version: String, baseUrl: String, token: String, isPush: Boolean) =
    """
        initscript{
            repositories{
                mavenCentral()
                ${
        if (version.lowercase().endsWith("snapshot")) {
            """maven("https://oss.sonatype.org/content/repositories/snapshots") {
                    mavenContent { snapshotsOnly() }
                }"""
        } else
            ""
    }
                    
            }
            dependencies{
                classpath("com.github.rnett.github-actions-gradle-cache:cache:$version")
            }
        }


        gradle.settingsEvaluated{
            apply<com.rnett.actions.cache.GHActionsBuildCachePlugin>()
            buildCache{
                remote<com.rnett.actions.cache.GHActionsBuildCache>{
                    baseUrl = "$baseUrl"
                    token = "$token"
                    isPush = $isPush
                }
            }
        }
    """.trimIndent()

private fun addInitScript(version: String, baseUrl: String, token: String, isPush: Boolean) {
    Path("~/.gradle/init.d/gh_actions_cache.init.gradle.kts")
        .also { it.parent.mkdir() }
        .write(getInitScript(version, baseUrl, token, isPush))
}

private fun enableBuildCache() {
    Path("~/.gradle/gradle.properties")
        .also { it.parent.mkdir() }
        .append("org.gradle.caching=true")
}

fun main() = runOrFail {
    val baseUrl = (
            env["ACTIONS_CACHE_URL"] ?: env["ACTIONS_RUNTIME_URL"] ?: error("Cache Service Url not found"))
        .replace("pipelines", "artifactcache") + "_apis/artifactcache/"

    maskSecret(baseUrl)

    val runtimeToken = env["ACTIONS_RUNTIME_TOKEN"] ?: error("Could not get cache access token")
    maskSecret(runtimeToken)

    val version = inputs.getOrElse("version") { "" }.ifBlank { BuildConfig.VERSION }
    val isPush = inputs.getOrElse("is-push") { "" }.ifBlank { "true" }
    val enable = inputs.getOrElse("enable") { "" }.ifBlank { "true" }
    addInitScript(version, baseUrl, runtimeToken, isPush.toBoolean())
    if (enable.toBoolean())
        enableBuildCache()
}