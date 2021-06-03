package com.rnett.actions.cache.internal

import com.rnett.actions.cache.*
import org.gradle.caching.*
import org.slf4j.LoggerFactory
import java.util.*

class GHActionsBuildCacheService(baseUrl: String, token: String) : BuildCacheService {
    private val logger = LoggerFactory.getLogger(GHActionsBuildCachePlugin::class.java)
    private val random = Random(System.nanoTime())
    private val client = CacheClient(baseUrl, token)
    override fun close() {
        client.close()
    }

    override fun load(key: BuildCacheKey, reader: BuildCacheEntryReader): Boolean {
        logger.info("Loading $key from GitHub Actions cache")
        try {
            val entry = repeatUntilNonNull(3) { client.getEntry(key.hashCode, key.hashCode) }
                ?: return false

            repeatUntilNoException(5) {
                client.download(entry.archiveLocation) {
                    reader.readFrom(it)
                }
            }
            return true
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }
    }

    override fun store(key: BuildCacheKey, writer: BuildCacheEntryWriter) {
        logger.info("Storing $key in GitHub Actions cache")
        val cacheKey =
            key.hashCode + "_" + System.nanoTime() + "_" + System.currentTimeMillis() + "_" + random.nextLong()
        val id = repeatUntilNonNull(5) { client.reserveCache(cacheKey, key.hashCode) }
            ?: throw BuildCacheException("Could not reserve cache space")

        repeatUntilNoException(5) {
            client.upload(id, writer)
        }
        repeatUntilNoException(5) {
            client.commit(id, writer.size)
        }
    }
}

class GHActionsBuildCacheServiceFactory() : BuildCacheServiceFactory<GHActionsBuildCache> {
    private val logger = LoggerFactory.getLogger(GHActionsBuildCacheServiceFactory::class.java)
    override fun createBuildCacheService(
        configuration: GHActionsBuildCache,
        describer: BuildCacheServiceFactory.Describer
    ): BuildCacheService {
        logger.debug("Trying to create GitHub Actions cache service w/ baseUrl \"${configuration.baseUrl}\" and token \"${configuration.token}\"")
        val baseUrl = configuration.baseUrl ?: error("Required baseUrl for Github Actions Cache not specified")
        val token = configuration.token ?: error("Required token for Github Actions Cache not specified")
        describer.type("GitHub Actions")
        return GHActionsBuildCacheService(baseUrl, token)
    }
}