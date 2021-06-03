package com.rnett.actions.cache

import org.gradle.caching.*
import org.gradle.caching.configuration.AbstractBuildCache

abstract class GHActionsBuildCache(): AbstractBuildCache()

class GHActionsBuildCacheService(): BuildCacheService{
    override fun close() {
        TODO("Not yet implemented")
    }

    override fun load(key: BuildCacheKey, reader: BuildCacheEntryReader): Boolean {
        reader.readFrom()
        TODO("Not yet implemented")
    }

    override fun store(key: BuildCacheKey, writer: BuildCacheEntryWriter) {
        TODO("Not yet implemented")
    }
}

class GHActionsBuildCacheServiceFactory(): BuildCacheServiceFactory<GHActionsBuildCache>{
    override fun createBuildCacheService(
        configuration: GHActionsBuildCache,
        describer: BuildCacheServiceFactory.Describer
    ): BuildCacheService {
        TODO("Not yet implemented")
    }

}