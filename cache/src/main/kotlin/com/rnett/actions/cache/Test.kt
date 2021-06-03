package com.rnett.actions.cache

import org.gradle.internal.impldep.com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.internal.impldep.com.google.api.client.json.Json
import org.gradle.internal.impldep.org.apache.http.HttpEntity
import org.gradle.internal.impldep.org.apache.http.HttpHost
import org.gradle.internal.impldep.org.apache.http.HttpResponse
import org.gradle.internal.impldep.org.apache.http.client.HttpClient
import org.gradle.internal.impldep.org.apache.http.client.methods.*
import org.gradle.internal.impldep.org.apache.http.entity.ByteArrayEntity
import org.gradle.internal.impldep.org.apache.http.entity.ContentType
import org.gradle.internal.impldep.org.apache.http.entity.StringEntity
import org.gradle.internal.impldep.org.apache.http.impl.client.CloseableHttpClient
import org.gradle.internal.impldep.org.apache.http.impl.client.HttpClients
import java.io.File
import java.nio.file.Path

data class CacheEntry(val cacheKey: String?, val scope: String?, val creationTime: String?, val archiveLocation: String?)

data class ReserveRequest(val key: String, val version: String = key)

data class ReserveCacheResponse(val cacheId: Int)

data class CommitCacheRequest(val size: Long)

fun HttpResponse.isSuccess() = statusLine.statusCode in 200 until 300

class CacheClient(val baseUrl: String, val token: String, val json: ObjectMapper = ObjectMapper()): AutoCloseable{
    val client: CloseableHttpClient = HttpClients.createMinimal()

    fun url(resource: String) = "$baseUrl$resource"

    fun HttpUriRequest.setup() = apply {
        addHeader("User-Agent", "Gradle Actions Cache")
        addHeader("Authorization", "Bearer $token")
    }

    fun makeRequest(request: HttpUriRequest) = client.execute(request.setup())

    inline fun requestResource(resource: String, request: (String) -> HttpUriRequest) = makeRequest(request(url(resource)))

    fun getEntry(key: String): CacheEntry?{
        requestResource("cache?keys=$key&version=$key", ::HttpGet).use {
            if(it.statusLine.statusCode == 204)
                return null
            if(!it.isSuccess())
                error("Error response: ${it.statusLine}")

            val result = json.readValue(it.entity.content.readAllBytes(), CacheEntry::class.java)
//            val downloadUrl = result?.archiveLocation ?: error("Cache not found")
            //TODO set secret?
            return result
        }
    }

    fun reserveCache(key: String): Int? =
        requestResource("caches"){
            HttpPost(it).apply {
                entity = StringEntity(json.writeValueAsString(ReserveRequest(key)), ContentType.APPLICATION_JSON)
            }
        }.use {
            json.readValue(it.entity.content.readAllBytes(), ReserveCacheResponse::class.java)?.cacheId
        }

    fun upload(id: Int, data: String){
        val bytes = data.encodeToByteArray()
        requestResource("caches/$id"){
            HttpPatch(it).apply {
                addHeader("Content-Range", "bytes 0-${bytes.size-1}/*")
                entity = ByteArrayEntity(bytes, ContentType.APPLICATION_OCTET_STREAM)
            }
        }.use {
            if(!it.isSuccess())
                error("Error during upload: ${it.statusLine}")
        }
    }

    fun commit(id: Int, size: Long){
        requestResource("caches/$id"){
            HttpPost(it).apply {
                entity = StringEntity(json.writeValueAsString(CommitCacheRequest(size)), ContentType.APPLICATION_JSON)
            }
        }.use {
            if(!it.isSuccess())
                error("Error commiting cache: ${it.statusLine}")
        }
    }

    override fun close() {
        client.close()
    }
}

fun main() {
    val baseUrl = System.getenv(EnviromentVariables.baseUrl)!!
    val token = System.getenv(EnviromentVariables.token)!!

    val client = CacheClient(baseUrl, token)

    val key = "testKey"
    val data = "testCache"

    val id = client.reserveCache(key) ?: error("Error reserving cache")
    client.upload(id, data)
    client.commit(id, data.length.toLong())

    println("Entry: ${client.getEntry(key)}")

}