package com.rnett.actions.cache

import com.rnett.action.Path
import com.rnett.action.cache.cache
import com.rnett.action.core.log
import com.rnett.action.core.runOrFail
import com.rnett.action.exec.exec
import com.rnett.action.glob.globFlow
import kotlinx.coroutines.flow.collect

suspend fun main() = runOrFail {
    if (SharedState.didCache.toBoolean()) {
        val paths = SharedState.cachePaths
        val primaryKey = SharedState.primaryKey
        val exact = SharedState.exactMatch.toBoolean()

        exec.execCommand("./gradlew --stop")

        globFlow("~/.gradle/caches/*/*.lock", " ~/.gradle/caches/*/gc.properties").collect {
            if (it.isFile)
                it.delete()
        }

        Path("~/.konan/cache/.lock").delete()

        if (exact) {
            log.info("Exact cache hit occurred, not saving cache")
            return@runOrFail
        } else {
            log.info("Saving cache to $primaryKey")
            cache.saveCache(paths, primaryKey)
        }
    }
}