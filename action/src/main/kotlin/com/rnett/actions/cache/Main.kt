package com.rnett.actions.cache

import com.rnett.action.Path
import com.rnett.action.cache.cache.restoreCache
import com.rnett.action.core.*
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
private suspend fun cache(userKey: String?, userRestoreKeys: List<String>?, additionalPaths: List<String>) {
    log.info("Restoring full cache...")

    val keyPrefix = inputs.getOptional("cache-key-prefix")?.ifBlank { null }
    val keyPostfix = inputs.getOptional("cache-key-postfix")?.ifBlank { null }

    val baseKeyParts = listOfNotNull(
        keyPrefix,
        "gradle",
        "autocache",
        currentOS.name,
        github.context.workflow,
        github.context.job,
        keyPostfix
    )

    val key = userKey ?: (baseKeyParts + listOf(
        github.context.hashFiles(
            listOf(
                "**/*.gradle*",
                "**/buildSrc/src/**",
                "**/gradle-wrapper.properties",
                "**/gradle.properties",
                "**/*gradle.lockfile",
            )
        )
    )).joinToString("-")

    log.info("Using cache key $key")

    val restoreKeys = if (userKey == null)
        buildList {
            add(baseKeyParts.joinToString("-"))
            add(baseKeyParts.dropLast(1).joinToString("-"))
            add(baseKeyParts.dropLast(2).joinToString("-"))

            if (keyPostfix != null)
                baseKeyParts.dropLast(3).joinToString("-")
        }
    else
        userRestoreKeys ?: emptyList()

    log.info("With fallback key:\n${restoreKeys.joinToString("\n") { "\t" + it }}")

    val dirs = buildList {
        addAll(listOf(
            "nodejs",
            "wrapper",
            "yarn",
            "jdks",
        ).map { "~/.gradle/$it" })

        addAll(Path("~/.gradle/caches").takeIf { it.exists }?.children.orEmpty()
            .filter { it.isDir && !it.name.startsWith("build-cache") }
            .map {
                "~/.gradle/caches/${it.name}"
            })

        add("~/.konan")

        addAll(additionalPaths.filterNot { it.startsWith("!") })
    }.minus(additionalPaths.filter { it.startsWith("!") }.map { it.removePrefix("!") })

    log.info("Caching files:\n${dirs.joinToString("\n") { "\t" + it }}")


    val usedKey = restoreCache(dirs, key, restoreKeys)

    if (usedKey != null) {
        if (usedKey == key) {
            log.info("Exact cache hit")
        } else {
            log.info("Cache hit on fallback key $usedKey")
        }
        outputs["cache-hit"] = "true"
    } else {
        log.info("Cache miss")
    }

    with(SharedState) {
        cachePaths = dirs
        exactMatch = (usedKey == key).toString()
        primaryKey = key
    }
}

suspend fun main() = runOrFail {
    val baseUrl = (
            env["ACTIONS_CACHE_URL"] ?: env["ACTIONS_RUNTIME_URL"] ?: error("Cache Service Url not found"))
        .replace("pipelines", "artifactcache") + "_apis/artifactcache/"

    maskSecret(baseUrl)

    val runtimeToken = env["ACTIONS_RUNTIME_TOKEN"] ?: error("Could not get cache access token")
    maskSecret(runtimeToken)

    val version = inputs.getOrElse("version") { "" }.ifBlank { BuildConfig.VERSION }
    val isPush = inputs.getOrElse("is-push") { "" }.ifBlank { "true" }
    val enable = inputs.getOrElse("use-build-cache") { "" }.ifBlank { "true" }

    val fullCache = inputs.getOptional("full-cache")?.ifBlank { null }?.toBoolean() ?: true
    val cacheKey = inputs.getOptional("cache-key")?.ifBlank { null }
    val restoreKeys = inputs.getOptional("restore-keys")?.ifBlank { null }?.split("\n", ",")
    val additionalPaths = inputs.getOptional("cache-paths")?.split("\n", ",").orEmpty()

    addInitScript(version, baseUrl, runtimeToken, isPush.toBoolean())
    if (enable.toBoolean())
        enableBuildCache()

    SharedState.didCache = fullCache.toString()
    if (fullCache)
        cache(cacheKey, restoreKeys, additionalPaths)
}