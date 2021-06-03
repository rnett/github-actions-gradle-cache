package com.rnett.actions.cache

import com.rnett.actions.cache.internal.GHActionsBuildCacheServiceFactory
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.slf4j.LoggerFactory

class GHActionsBuildCachePlugin : Plugin<Settings> {
    private val logger = LoggerFactory.getLogger(GHActionsBuildCachePlugin::class.java)
    override fun apply(target: Settings) {
        logger.info("Registering GitHub Actions build cache")
        target.buildCache.registerBuildCacheService(
            GHActionsBuildCache::class.java,
            GHActionsBuildCacheServiceFactory::class.java
        )
    }
}