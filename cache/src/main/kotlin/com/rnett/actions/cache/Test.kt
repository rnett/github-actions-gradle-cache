package com.rnett.actions.cache

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.internal.impldep.com.fasterxml.jackson.annotation.JsonAutoDetect
import org.gradle.internal.impldep.com.fasterxml.jackson.annotation.JsonCreator
import org.gradle.internal.impldep.com.fasterxml.jackson.annotation.JsonProperty
import org.gradle.internal.impldep.com.fasterxml.jackson.annotation.PropertyAccessor
import org.gradle.internal.impldep.com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.internal.impldep.org.apache.http.HttpResponse
import org.gradle.internal.impldep.org.apache.http.client.methods.HttpGet
import org.gradle.internal.impldep.org.apache.http.client.methods.HttpPatch
import org.gradle.internal.impldep.org.apache.http.client.methods.HttpPost
import org.gradle.internal.impldep.org.apache.http.client.methods.HttpUriRequest
import org.gradle.internal.impldep.org.apache.http.entity.ByteArrayEntity
import org.gradle.internal.impldep.org.apache.http.entity.ContentType
import org.gradle.internal.impldep.org.apache.http.entity.StringEntity
import org.gradle.internal.impldep.org.apache.http.impl.client.CloseableHttpClient
import org.gradle.internal.impldep.org.apache.http.impl.client.HttpClients
import org.gradle.internal.impldep.org.apache.http.message.BasicNameValuePair

@Serializable
data class CacheEntry(
    val cacheKey: String?,
    val scope: String?,
    val creationTime: String?,
    val archiveLocation: String?
)

@Serializable
data class ReserveRequest(val key: String, val version: String = key)

@Serializable
data class ReserveCacheResponse(val cacheId: Int)

@Serializable
data class CommitCacheRequest(val size: Long)

fun HttpResponse.isSuccess() = statusLine.statusCode in 200 until 300

class CacheClient(
    val baseUrl: String,
    val token: String,
    val json: Json = Json {  }
) : AutoCloseable {
    val client: CloseableHttpClient = HttpClients.createMinimal()

    fun url(resource: String) = "$baseUrl$resource"

    fun HttpUriRequest.setup() = apply {
        addHeader("User-Agent", "Gradle Actions Cache")
        addHeader("Authorization", "Bearer $token")
        addHeader(
            "Accept",
            ContentType.APPLICATION_JSON.withParameters(BasicNameValuePair("api-version", "6.0-preview.1")).toString()
        )
    }

    fun makeRequest(request: HttpUriRequest) = client.execute(request.setup())

    inline fun requestResource(resource: String, request: (String) -> HttpUriRequest) =
        makeRequest(request(url(resource)))

    fun getEntry(key: String): CacheEntry? {
        requestResource("cache?keys=$key&version=$key", ::HttpGet).use {
            val response = it.entity.content.readAllBytes().decodeToString()

            if (it.statusLine.statusCode == 204)
                return null
            if (!it.isSuccess())
                error("Error getting entry: ${it.statusLine}: $response")

            println("Entry result: ${response}")

            val result = json.decodeFromString<CacheEntry?>(response)
//            val downloadUrl = result?.archiveLocation ?: error("Cache not found")
            //TODO set secret?
            return result
        }
    }

    fun reserveCache(key: String): Int? =
        requestResource("caches") {
            HttpPost(it).apply {
                entity = StringEntity(json.encodeToString(ReserveRequest(key)), ContentType.APPLICATION_JSON)
            }
        }.use {
            val response = it.entity.content.readAllBytes().decodeToString()
            if(!it.isSuccess())
                error("Could not reserve cache key: ${it.statusLine}: $response")

            json.decodeFromString<ReserveCacheResponse?>(response)?.cacheId
        }

    fun upload(id: Int, data: String) {
        val bytes = data.encodeToByteArray()
        requestResource("caches/$id") {
            HttpPatch(it).apply {
                addHeader("Content-Range", "bytes 0-${bytes.size - 1}/*")
                entity = ByteArrayEntity(bytes, ContentType.APPLICATION_OCTET_STREAM)
            }
        }.use {
            val response = it.entity.content.readAllBytes().decodeToString()
            if (!it.isSuccess())
                error("Error during upload: ${it.statusLine}: $response")
        }
    }

    fun commit(id: Int, size: Long) {
        requestResource("caches/$id") {
            HttpPost(it).apply {
                entity = StringEntity(json.encodeToString(CommitCacheRequest(size)), ContentType.APPLICATION_JSON)
            }
        }.use {
            val response = it.entity.content.readAllBytes().decodeToString()
            if (!it.isSuccess())
                error("Error committing cache: ${it.statusLine}: $response")

            println("Commit result: $response")
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

//    val id = client.reserveCache(key) ?: error("Error reserving cache")
//    client.upload(id, data)
//    client.commit(id, data.length.toLong())

    println("Entry: ${client.getEntry(key)}")

}