package com.rnett.actions.cache

import com.rnett.action.Path
import com.rnett.action.core.inputs
import com.rnett.action.core.log
import com.rnett.action.core.runOrFail
import com.rnett.action.currentOS
import com.rnett.action.github.github

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
    log.info("Adding init script for GitHub actions build cache")
    Path("~/.gradle/init.d/gh_actions_cache.init.gradle.kts")
        .also { it.parent.mkdir() }
        .write(getInitScript(version, baseUrl, token, isPush))
}

private fun enableBuildCache() {
    log.info("Enabling build cache for all projects")
    Path("~/.gradle/gradle.properties")
        .also { it.parent.mkdir() }
        .append("org.gradle.caching=true")
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun newCache() {

    val baseKeyParts = listOfNotNull(
        "gradle",
        "auto",
        "cache",
        currentOS.name,
        github.context.workflow,
        github.context.job,
        github.context.hashFiles(
            listOf(
                "**/*.gradle*",
                "**/buildSrc/src/**",
                "**/gradle-wrapper.properties",
                "**/gradle.properties",
                "**/*gradle.lockfile",
            )
        )
    )

    val fullCaches = inputs.getOptional("full-caches").orEmpty().ifBlank { Caching.gradleFullCaches.joinToString("\n") }
    val piecewiseCaches =
        inputs.getOptional("piecewise-caches").orEmpty().ifBlank { Caching.gradlePiecewiseCaches.joinToString { "\n" } }
    val fullKey = inputs.getOptional("full-key").orEmpty().ifBlank { baseKeyParts.joinToString("-") }
    val fullRestoreKeys = inputs.getOptional("full-restore-keys").orEmpty().ifBlank {
        buildList {
            add(baseKeyParts.joinToString("-"))
            add(baseKeyParts.dropLast(1).joinToString("-"))
            add(baseKeyParts.dropLast(2).joinToString("-"))
            add(baseKeyParts.dropLast(3).joinToString("-"))
        }.joinToString("\n")
    }
    val piecewiseKey = inputs.getOptional("piecewise-key").orEmpty().ifBlank { "gradle-auto-cache" }
    Caching.restoreAction(
        fullCaches.split("\n"),
        piecewiseCaches.split("\n"),
        fullKey,
        fullRestoreKeys.split("\n", ","),
        piecewiseKey
    )
}

suspend fun main() = runOrFail {
//    val baseUrl = (
//            env["ACTIONS_CACHE_URL"] ?: env["ACTIONS_RUNTIME_URL"] ?: error("Cache Service Url not found"))
//        .replace("pipelines", "artifactcache") + "_apis/artifactcache/"
//
//    maskSecret(baseUrl)
//
//    val runtimeToken = env["ACTIONS_RUNTIME_TOKEN"] ?: error("Could not get cache access token")
//    maskSecret(runtimeToken)
//
//    val version = inputs.getOrElse("version") { "" }.ifBlank { BuildConfig.VERSION }
//    val isPush = inputs.getOrElse("is-push") { "" }.ifBlank { "true" }
//    val enable = inputs.getOrElse("use-build-cache") { "" }.ifBlank { "true" }
//
//    val fullCache = inputs.getOptional("full-cache")?.ifBlank { null }?.toBoolean() ?: true
//    val cacheKey = inputs.getOptional("cache-key")?.ifBlank { null }
//    val restoreKeys = inputs.getOptional("restore-keys")?.ifBlank { null }?.split("\n", ",")
//    val additionalPaths = inputs.getOptional("cache-paths")?.split("\n", ",").orEmpty()
//
//    addInitScript(version, baseUrl, runtimeToken, isPush.toBoolean())
//    if (enable.toBoolean())
//        enableBuildCache()
//
//    SharedState.didCache = fullCache.toString()
//    if (fullCache)
//        cache(cacheKey, restoreKeys, additionalPaths)
    newCache()
}