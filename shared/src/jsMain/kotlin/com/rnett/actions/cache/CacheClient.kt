package com.rnett.actions.cache

import com.rnett.action.Path
import com.rnett.action.cache.internal.CacheClient
import kotlin.js.Date
import kotlin.random.Random

object CacheClient {
    val cacheClient = CacheClient("Github Actions gradle cache")
    private val random = Random(Date.now().toLong())

    private fun salt(): String = ""

    suspend fun storeIfNew(file: Path, key: String, version: String): Boolean {
        require(file.isFile) { "Can't store a non-file" }
        if (!cacheClient.hasCache(listOf(key), version)) {
            val salted = key + salt()
            val id = cacheClient.reserveCache(salted, version) ?: error("Could not reserve cache")
            cacheClient.saveFile(id, file)
            return true
        }
        return false
    }

    suspend fun storeForce(file: Path, key: String, version: String) {
        require(file.isFile) { "Can't store a non-file" }
        val salted = key + salt()
        val id = cacheClient.reserveCache(salted, version) ?: error("Could not reserve cache")
        cacheClient.saveFile(id, file)
    }

    suspend fun download(to: Path, keys: List<String>, version: String): Boolean =
        cacheClient.downloadCacheEntry(to, keys, version)

    suspend fun read(keys: List<String>, version: String): String? = cacheClient.readCacheEntry(keys, version)

    suspend fun write(key: String, data: String, version: String) {
        val salted = key + salt()
        val id = cacheClient.reserveCache(salted, version) ?: error("Could not reserve cache")
        cacheClient.saveText(id, data)
    }
}