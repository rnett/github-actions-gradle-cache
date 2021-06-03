package com.rnett.actions.cache

import org.gradle.caching.*
import org.gradle.caching.configuration.AbstractBuildCache
import java.util.*

abstract class GHActionsBuildCache(): AbstractBuildCache(){
    var baseUrl: String? = null
    var token: String? = null
}

class GHActionsBuildCacheService(baseUrl: String, token: String): BuildCacheService{
    private val random = Random(System.nanoTime())
    private val client = CacheClient(baseUrl, token)
    override fun close() {
        client.close()
    }

    override fun load(key: BuildCacheKey, reader: BuildCacheEntryReader): Boolean {
        val entry = repeatUntilNonNull(3){ client.getEntry(key.hashCode, key.hashCode) }
            ?: return false

        repeatUntilNoException(5) {
            client.download(entry.archiveLocation){
                reader.readFrom(it)
            }
        }
        return true
    }

    override fun store(key: BuildCacheKey, writer: BuildCacheEntryWriter) {
        val cacheKey = key.hashCode + "_" + System.nanoTime() + "_" + System.currentTimeMillis() + "_" + random.nextLong()
        val id = repeatUntilNonNull(5){ client.reserveCache(cacheKey, key.hashCode) }
            ?: throw BuildCacheException("Could not reserve cache space")

        repeatUntilNoException(5){
            client.upload(id, writer)
        }
        repeatUntilNoException(5){
            client.commit(id, writer.size)
        }
    }
}

class GHActionsBuildCacheServiceFactory(): BuildCacheServiceFactory<GHActionsBuildCache>{
    override fun createBuildCacheService(
        configuration: GHActionsBuildCache,
        describer: BuildCacheServiceFactory.Describer
    ): BuildCacheService {
        val baseUrl = configuration.baseUrl ?: error("Required baseUrl for Github Actions Cache not specified")
        val token = configuration.token ?: error("Required token for Github Actions Cache not specified")
        describer.type("GitHub Actions")
        return GHActionsBuildCacheService(baseUrl, token)
    }
}