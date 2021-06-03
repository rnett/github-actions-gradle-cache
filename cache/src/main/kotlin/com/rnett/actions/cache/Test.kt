package com.rnett.actions.cache

import org.gradle.internal.impldep.com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.internal.impldep.com.google.api.client.json.Json
import org.gradle.internal.impldep.org.apache.http.HttpHost
import org.gradle.internal.impldep.org.apache.http.client.HttpClient
import org.gradle.internal.impldep.org.apache.http.client.methods.HttpGet
import org.gradle.internal.impldep.org.apache.http.client.methods.HttpRequestBase
import org.gradle.internal.impldep.org.apache.http.client.methods.HttpUriRequest
import org.gradle.internal.impldep.org.apache.http.impl.client.CloseableHttpClient
import org.gradle.internal.impldep.org.apache.http.impl.client.HttpClients
import java.io.File
import java.nio.file.Path

data class CacheEntry(val cacheKey: String?, val scope: String?, val creationTime: String?, val archiveLocation: String?)

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
            if(it.statusLine.statusCode !in 200 until 300)
                error("Error response: ${it.statusLine}")

            val result = json.readValue(it.entity.content.readAllBytes(), CacheEntry::class.java)
            val downloadUrl = result.archiveLocation ?: error("Cache not found")
            //TODO set secret?
            return result
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
    println("Entry: ${client.getEntry(key)}")

}