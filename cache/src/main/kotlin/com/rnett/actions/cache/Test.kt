package com.rnett.actions.cache

import org.gradle.internal.impldep.org.apache.http.client.HttpClient
import org.gradle.internal.impldep.org.apache.http.client.methods.HttpGet
import org.gradle.internal.impldep.org.apache.http.client.methods.HttpUriRequest
import org.gradle.internal.impldep.org.apache.http.impl.client.HttpClients
import java.io.File
import java.nio.file.Path


fun main() {

    val baseUrlFile = Path.of("~/.cache-baseurl")
    println("Base url file: ${baseUrlFile.toAbsolutePath()}")
    val baseUrl = baseUrlFile.toFile().readText()

    HttpClients.createMinimal().use { client ->

        val key = "testKey"

        val resource = "cache?keys=$key&version=$key"

        println("Env: ${System.getenv()}")

        val url = "baseUrl$resource"

        val result = client.execute(HttpGet(url)).use { response ->
            println("Status code: ${response.statusLine.statusCode}")
            response.entity.content.readAllBytes().decodeToString()

        }
        println("Body: $result")
    }

}