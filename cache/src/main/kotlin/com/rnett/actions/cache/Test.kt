package com.rnett.actions.cache

import org.gradle.internal.impldep.org.apache.http.client.HttpClient
import org.gradle.internal.impldep.org.apache.http.client.methods.HttpGet
import org.gradle.internal.impldep.org.apache.http.client.methods.HttpUriRequest
import org.gradle.internal.impldep.org.apache.http.impl.client.HttpClients
import java.io.File
import java.nio.file.Path


fun main() {
    val baseUrl = System.getenv(baseUrlEnviromentVariable)!!

    HttpClients.createMinimal().use { client ->

        val key = "testKey"

        val resource = "cache?keys=$key&version=$key"

        val url = "$baseUrl$resource"

        val result = client.execute(HttpGet(url)).use { response ->
            println("Status code: ${response.statusLine.statusCode}")
            response.entity.content.readAllBytes().decodeToString()

        }
        println("Body: $result")
    }

}