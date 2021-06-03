package com.rnett.actions.cache

import org.gradle.internal.impldep.org.apache.http.HttpHost
import org.gradle.internal.impldep.org.apache.http.client.HttpClient
import org.gradle.internal.impldep.org.apache.http.client.methods.HttpGet
import org.gradle.internal.impldep.org.apache.http.client.methods.HttpUriRequest
import org.gradle.internal.impldep.org.apache.http.impl.client.HttpClients
import java.io.File
import java.nio.file.Path


fun main() {
    val baseUrl = System.getenv(EnviromentVariables.baseUrl)!!
    val token = System.getenv(EnviromentVariables.token)!!

    HttpClients.createDefault().use { client ->

        val key = "testKey"

        val resource = "cache?keys=$key&version=$key"

        val url = "$baseUrl$resource"

        val request = HttpGet(url).apply {
            addHeader("User-Agent", "Gradle Actions Cache")
            addHeader("Authorization", "Bearer $token")
        }

        val result = client.execute(request).use { response ->
            println("Status code: ${response.statusLine.statusCode}")
            response.entity.content.readAllBytes().decodeToString()

        }
        println("Body: $result")
    }

}