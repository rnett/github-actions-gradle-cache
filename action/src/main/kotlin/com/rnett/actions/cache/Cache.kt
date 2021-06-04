package com.rnett.actions.cache

import com.rnett.action.Path
import com.rnett.action.cache.cache
import com.rnett.action.core.inputs
import com.rnett.action.core.log
import com.rnett.action.core.outputs
import com.rnett.action.currentOS
import com.rnett.action.github.github


@OptIn(ExperimentalStdlibApi::class)
internal suspend fun cache(userKey: String?, userRestoreKeys: List<String>?, additionalPaths: List<String>) {
    log.info("Restoring full cache...")

    val keyPrefix = inputs.getOptional("cache-key-prefix")?.ifBlank { null }
    val keyPostfix = inputs.getOptional("cache-key-postfix")?.ifBlank { null }

    val baseKeyParts = listOfNotNull(
        keyPrefix,
        "gradle",
        "auto",
        "cache",
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

        addAll(
            Path("~/.gradle/caches").takeIf { it.exists }?.children.orEmpty()
                .filter { it.isDir && !it.name.startsWith("build-cache") }
                .map {
                    "~/.gradle/caches/${it.name}"
                })

        add("~/.konan")

        addAll(additionalPaths.filterNot { it.startsWith("!") })
    }.minus(additionalPaths.filter { it.startsWith("!") }.map { it.removePrefix("!") })

    log.info("Caching files:\n${dirs.joinToString("\n") { "\t" + it }}")


    val usedKey = cache.restoreCache(dirs, key, restoreKeys)

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