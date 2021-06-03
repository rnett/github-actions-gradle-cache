package com.rnett.actions.cache

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.caching.BuildCacheEntryWriter
import org.gradle.caching.BuildCacheException
import org.gradle.internal.impldep.org.apache.http.HttpResponse
import org.gradle.internal.impldep.org.apache.http.client.methods.*
import org.gradle.internal.impldep.org.apache.http.entity.AbstractHttpEntity
import org.gradle.internal.impldep.org.apache.http.entity.ContentType
import org.gradle.internal.impldep.org.apache.http.entity.InputStreamEntity
import org.gradle.internal.impldep.org.apache.http.entity.StringEntity
import org.gradle.internal.impldep.org.apache.http.impl.client.CloseableHttpClient
import org.gradle.internal.impldep.org.apache.http.impl.client.HttpClients
import org.gradle.internal.impldep.org.apache.http.message.BasicNameValuePair
import org.slf4j.LoggerFactory
import java.io.*
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


@Serializable
data class CacheEntry(
    val cacheKey: String,
    val scope: String,
    val creationTime: String,
    val archiveLocation: String,
    val cacheVersion: String
)

@Serializable
data class ReserveRequest(val key: String, val version: String)

@Serializable
data class ReserveCacheResponse(val cacheId: Int)

@Serializable
data class CommitCacheRequest(val size: Long)

fun HttpResponse.isSuccess() = statusLine.statusCode in 200 until 300

inline fun <R> repeatUntilNonNull(maxReps: Int, block: () -> R?): R? {
    require(maxReps > 0){ "maxReps must be > 0" }
    var i = 0
    while(i < maxReps){
        block()?.let { return it }
        i++
    }
    return null
}

inline fun repeatUntilNoException(maxReps: Int, block: () -> Unit) {
    require(maxReps > 0){ "maxReps must be > 0" }
    var i = 0
    var lastException: Throwable? = null
    while(i < maxReps){
        try {
            block()
            return
        } catch (e: Throwable){
            lastException = e
        }
        i++
    }
    throw lastException!!
}

class CacheClient(
    val baseUrl: String,
    val token: String,
    val json: Json = Json { ignoreUnknownKeys = true }
) : AutoCloseable {

    companion object {
        const val userAgent = "Gradle Actions Cache"
    }

    val client: CloseableHttpClient = HttpClients.createMinimal()

    val logger = LoggerFactory.getLogger(CacheClient::class.java)

    fun url(resource: String) = "$baseUrl$resource"

    fun HttpUriRequest.setup() = apply {
        addHeader("User-Agent", userAgent)
        addHeader("Authorization", "Bearer $token")
        addHeader(
            "Accept",
            ContentType.APPLICATION_JSON.withParameters(BasicNameValuePair("api-version", "6.0-preview.1")).toString()
        )
    }

    fun makeRequest(request: HttpUriRequest) = client.execute(request.setup())

    inline fun requestResource(resource: String, request: (String) -> HttpUriRequest): CloseableHttpResponse {
        val toSend = request(url(resource))
        return makeRequest(toSend).apply {
            println("Request ${toSend.requestLine}, response $statusLine")
        }
    }

    fun getEntry(key: String, version: String): CacheEntry? {
        requestResource("cache?keys=$key&version=$version", ::HttpGet).use {
            if (it.statusLine.statusCode == 204)
                return null

            val response = it.entity?.content?.readAllBytes()?.decodeToString()

            if (!it.isSuccess())
                throw BuildCacheException("Error getting cache entry: ${it.statusLine}: $response")

            val result = json.decodeFromString<CacheEntry>(response ?: error("No response"))

            //TODO mark archive url as secret

            return result
        }
    }

    fun reserveCache(key: String, version: String): Int? =
        requestResource("caches") {
            HttpPost(it).apply {
                entity = StringEntity(json.encodeToString(ReserveRequest(key, version)), ContentType.APPLICATION_JSON)
            }
        }.use {
            if(it.statusLine.statusCode == 409)
                return null

            val response = it.entity?.content?.readAllBytes()?.decodeToString()
            if (!it.isSuccess())
                throw BuildCacheException("Could not reserve cache key: ${it.statusLine}: $response")

            json.decodeFromString<ReserveCacheResponse>(response ?: error("No response")).cacheId.takeIf { it != -1 }
            //TODO mark id as secret
        }

    fun upload(id: Int, data: ByteArrayInputStream, start: Long = 0, end: Long = data.available().toLong()) {
        requestResource("caches/$id") {
            HttpPatch(it).apply {
                addHeader("Content-Range", "bytes $start-${end - 1}/*")
                entity = InputStreamEntity(data, end - start, ContentType.APPLICATION_OCTET_STREAM)
            }
        }.use {
            val response = it.entity?.content?.readAllBytes()?.decodeToString()
            if (!it.isSuccess())
                throw BuildCacheException("Error during upload: ${it.statusLine}: $response")
        }
    }

    class CacheWriterHttpEntity(val writer: BuildCacheEntryWriter): AbstractHttpEntity() {
        init {
            setContentType(ContentType.APPLICATION_OCTET_STREAM.toString())
        }
        override fun isRepeatable(): Boolean = false

        override fun getContentLength(): Long = writer.size

        override fun getContent(): InputStream {
            val input = PipedInputStream()
            val out = PipedOutputStream(input)
            Thread {
                try {
                    writer.writeTo(out)
                } finally {
                    if (out != null) {
                        out.close()
                    }
                }
            }.start()
            return input
        }

        override fun writeTo(p0: OutputStream) {
            writer.writeTo(p0)
        }

        override fun isStreaming(): Boolean = true

    }

    fun upload(id: Int, data: BuildCacheEntryWriter) {
        requestResource("caches/$id") {
            HttpPatch(it).apply {
                addHeader("Content-Range", "bytes 0-${data.size - 1}/*")
                entity = CacheWriterHttpEntity(data)
            }
        }.use {
            val response = it.entity?.content?.readAllBytes()?.decodeToString()
            if (!it.isSuccess())
                throw BuildCacheException("Error during upload: ${it.statusLine}: $response")
        }
    }

    fun commit(id: Int, size: Long) {
        requestResource("caches/$id") {
            HttpPost(it).apply {
                entity = StringEntity(json.encodeToString(CommitCacheRequest(size)), ContentType.APPLICATION_JSON)
            }
        }.use {
            val response = it.entity?.content?.readAllBytes()?.decodeToString()
            if (!it.isSuccess())
                throw BuildCacheException("Error committing cache: ${it.statusLine}: $response")
        }
    }

    inline fun <R> download(archiveLocation: String, use: (InputStream) -> R): R {
        contract {
            callsInPlace(use, InvocationKind.EXACTLY_ONCE)
        }
        client.execute(HttpGet(archiveLocation).apply {
            addHeader("User-Agent", userAgent)
        }).use {
            if (!it.isSuccess())
                throw BuildCacheException(
                    "Error downloading cache: ${it.statusLine}: ${
                        it.entity?.content?.readAllBytes()?.decodeToString()
                    }"
                )

            return use(it.entity.content)
        }
    }

    override fun close() {
        client.close()
    }
}

//TODO implement the cache
fun main() {
    val baseUrl = System.getenv(EnviromentVariables.baseUrl)!!
    val token = System.getenv(EnviromentVariables.token)!!

    val client = CacheClient(baseUrl, token)

    val key = "testKey3"
    val version = key
    val data = "testCache"

    //TODO can't overwrite

    val id = client.reserveCache(key, version)
    if(id != null) {
        println("Saving cache")
        client.upload(id, ByteArrayInputStream(data.encodeToByteArray()))
        client.commit(id, data.encodeToByteArray().size.toLong())
    } else {
        println("Using pre-saved cache")
    }

    val entry = client.getEntry(key, version) ?: error("No entry found")
    println("Entry: $entry")
    val value = client.download(entry.archiveLocation){ it.readAllBytes().decodeToString() }
    println("Value: $value")

}