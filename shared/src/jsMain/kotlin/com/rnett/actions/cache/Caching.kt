package com.rnett.actions.cache

import com.rnett.action.Path
import com.rnett.action.cache.cache
import com.rnett.action.core.log
import com.rnett.action.currentOS
import com.rnett.action.exec.exec
import com.rnett.action.github.github
import com.rnett.action.glob.glob
import com.rnett.action.glob.globFlow
import com.rnett.actions.cache.Caching.relativePath
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

object Caching {
    internal fun Path.relativePath(from: Path) = path.removePrefix(from.path)

    val gradlePiecewiseCaches = listOf(
        "~/.gradle/caches/jars*/",
        "~/.gradle/caches/modules*/files*/",
        "~/.gradle/caches/build-cache*/"
    )

    @OptIn(ExperimentalStdlibApi::class)
    val gradleFullCaches = buildList {
        addAll(listOf(
            "nodejs",
            "wrapper",
            "yarn",
            "jdks",
            "caches"
        ).map { "~/.gradle/$it" })

        addAll(gradlePiecewiseCaches.ignoreDirs())

        add("~/.konan")
    }

    private fun List<String>.ignoreDirs() = map {
        val ignored = if (it.startsWith("!")) it else "!$it"
        "$ignored**"
    }

    suspend fun restoreAction(
        fullCaches: List<String>,
        piecewiseCaches: List<String>,
        fullKey: String,
        restoreKeys: List<String>,
        piecewiseKey: String
    ) {
        val (usedKey, restoredArtifacts) = restoreAllCaches(
            fullCaches,
            piecewiseCaches,
            fullKey,
            restoreKeys,
            piecewiseKey
        )
        SharedState.cacheState = CacheState(
            fullCaches,
            piecewiseCaches,
            fullKey,
            usedKey == fullKey,
            piecewiseKey,
            restoredArtifacts
        )
    }

    suspend fun saveAction() {
        val state = SharedState.cacheState

        if (Path("./gradlew").isFile)
            exec.execCommand("./gradlew --stop")
        else {
            runCatching {
                exec.execCommand("gradle --stop")
            }
        }

        globFlow("~/.gradle/caches/*/*.lock", " ~/.gradle/caches/*/gc.properties").collect {
            if (it.isFile)
                it.delete()
        }

        Path("~/.konan/cache/.lock").delete()

        log.info("Cleaning up")

        saveAllCaches(
            state.fullCaches,
            state.piecewiseCaches,
            state.fullKey,
            state.wasExactHit,
            state.piecewiseKey,
            state.restoredArtifacts
        )
    }

    @Serializable
    data class CacheState(
        val fullCaches: List<String>,
        val piecewiseCaches: List<String>,
        val fullKey: String,
        val wasExactHit: Boolean,
        val piecewiseKey: String,
        val restoredArtifactPaths: Set<String>
    ) {
        constructor(
            fullCaches: List<String>,
            piecewiseCaches: List<String>,
            fullKey: String,
            wasExactHit: Boolean,
            artifactKeyPrefix: String,
            restoredArtifacts: Set<Path>
        ) :
                this(
                    fullCaches,
                    piecewiseCaches,
                    fullKey,
                    wasExactHit,
                    artifactKeyPrefix,
                    restoredArtifacts.mapTo(mutableSetOf()) { it.path })

        @Transient
        val restoredArtifacts: Set<Path> = restoredArtifactPaths.mapTo(mutableSetOf()) { Path(it) }
    }

    suspend fun saveAllCaches(
        fullCaches: List<String>,
        piecewiseCaches: List<String>,
        fullKey: String,
        wasExactHit: Boolean,
        piecewiseKey: String,
        restoredArtifacts: Set<Path>
    ) {
        val caches = fullCaches + piecewiseCaches.ignoreDirs()
        if (!wasExactHit) {
            log.info("Saving full caches")
            cache.saveCache(caches, fullKey)
        } else {
            log.info("Full cache was exact hit, not saving")
        }
        log.info("Saving piecewise caches")
        PiecewiseSharedCache.savePiecewiseCaches(fullKey, piecewiseCaches, piecewiseKey, restoredArtifacts)
    }

    suspend fun restoreAllCaches(
        fullCaches: List<String>,
        piecewiseCaches: List<String>,
        fullKey: String,
        restoreKeys: List<String>,
        piecewiseKey: String,
    ): Pair<String?, Set<Path>> {
        val caches = fullCaches + piecewiseCaches.ignoreDirs()

        val cacheUsed = cache.restoreCache(caches, fullKey, restoreKeys)
        when (cacheUsed) {
            null -> log.info("Cache miss on full caches")
            fullKey -> log.info("Exact cache hit")
            else -> log.info("Restored from key $cacheUsed")
        }

        return cacheUsed to PiecewiseSharedCache.restorePiecewiseCaches(fullKey, piecewiseKey, restoreKeys)
    }
}


/**
 * [prefix] is a prefix identifying the directory being cached.  I.e. modules-2/files-2.1
 * [directory] is the actual directory to cache the items of
 * [fullKey] is the full cache key, i.e. local to this job & hash & os
 * [piecewiseKey] is the artifact key, which should be more general than [fullKey] to allow for sharing.  i.e. no need for OS, gradle handles that w/ hashing.
 *
 * Each file in [directory] has the cache key of [piecewiseKey]-[prefix]-`$relName` where `$relName`.
 * It isn't actually stored in that key, it's stored in that key + timestamp + random, and that key is used as a restore key.
 *
 * A (newline separated) list of all files is stored at `index-$fullKey`. All paths in there are relative, too.
 */
class PiecewiseSharedCache(
    val prefix: String,
    val directory: Path,
    val fullKey: String,
    val piecewiseKey: String
) {
    constructor(fullPath: Path, fullKey: String, artifactKeyPrefix: String) : this(
        when {
            fullPath.isDescendantOf(github.context.workspacePath) -> fullPath.relativePath(github.context.workspacePath)
            fullPath.isDescendantOf(Path("~")) -> fullPath.relativePath(Path("~"))
            else -> fullPath.path
        }, fullPath, fullKey, artifactKeyPrefix
    )

    companion object {
        const val fullIndexVersion: String = "full-index"
        private fun fullIndexKey(key: String) = "$fullIndexVersion-${currentOS.name}-$key"

        suspend fun savePiecewiseCaches(
            fullKey: String,
            piecewiseCaches: List<String>,
            artifactKeyPrefix: String,
            restoredArtifacts: Set<Path>
        ) = supervisorScope {
            val cacheDirs = glob(piecewiseCaches)

            log.info("Saving ${cacheDirs.size} piecewise caches: ${cacheDirs.joinToString(", ")}")

            val cacheDirString = cacheDirs.joinToString("\n")
            CacheClient.write(fullIndexKey(fullKey), cacheDirString, fullIndexVersion)

            cacheDirs.map { PiecewiseSharedCache(it, fullKey, artifactKeyPrefix) }
                .map {
                    launch { it.save(restoredArtifacts) }
                }.joinAll()
        }

        suspend fun restorePiecewiseCaches(
            fullKey: String,
            artifactKeyPrefix: String,
            restoreKeys: List<String>
        ): Set<Path> =
            supervisorScope {
                log.info("Reading index of piecewise caches")
                val keys = (listOf(fullKey) + restoreKeys).map { fullIndexKey(it) }
                val cacheDirs =
                    CacheClient.read(keys, fullIndexVersion)
                        ?.split("\n")

                if (cacheDirs == null) {
                    log.info("Cache miss for index of piecewise caches for keys ${keys.joinToString(", ")}")
                    return@supervisorScope emptySet()
                }

                log.info("Restoring ${cacheDirs.size} piecewise caches: ${cacheDirs.joinToString(", ")}")

                cacheDirs.map { PiecewiseSharedCache(Path(it), fullKey, artifactKeyPrefix) }
                    .map { async { it.restore(restoreKeys) } }
                    .awaitAll()
                    .flatten()
                    .toSet()
            }
    }

    private val indexVersion = "index-$prefix"
    private fun indexKey(key: String) = "$indexVersion-$key"

    private fun artifactKey(file: Path): String {
        val relative = file.relativePath(directory)
        return "$piecewiseKey-$prefix-$relative"
    }

    /**
     * @return all restored files
     */
    suspend fun restore(restoreKeys: List<String>): List<Path> = coroutineScope {
        log.info("Reading index of $prefix")
        val keys = (listOf(fullKey) + restoreKeys).map { indexKey(it) }
        val files = CacheClient.read(keys, indexVersion)?.split("\n")

        if (files == null) {
            log.info("Cache miss for piecewise cache index $prefix for keys ${keys.joinToString(", ")}")
            return@coroutineScope emptyList()
        }

        log.info("Trying to restore ${files.size} files of $prefix")
        val restored = files.map {
            val path = directory / it
            val key = artifactKey(path)
            async {
                if (CacheClient.download(path, listOf(key), key))
                    path
                else
                    null
            }
        }.awaitAll().filterNotNull()
        log.info("Restored ${restored.size} files of $prefix")

        return@coroutineScope restored
    }

    suspend fun save(matched: Set<Path>): Unit = coroutineScope {
        if (!directory.isDir || directory.children.isEmpty())
            return@coroutineScope

        val files = globFlow("${directory.path.trimEnd('/')}/**")
            .filterNot { it.name.endsWith(".lock") || it.name == "gc.properties" }
            .toList()

        log.info("Saving index of $prefix")
        val index = files.joinToString("\n") { it.relativePath(directory) }
        CacheClient.write(indexKey(fullKey), index, indexVersion)

        log.info("Saving ${files.size} files of $prefix")
        files.mapNotNull {
            if (it !in matched) {
                val key = artifactKey(it)
                launch {
                    CacheClient.storeIfNew(it, key, key)
                }
            } else null
        }.joinAll()
    }

}