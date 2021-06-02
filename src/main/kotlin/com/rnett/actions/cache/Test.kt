package com.rnett.actions.cache

import org.gradle.internal.impldep.org.apache.http.client.HttpClient
import org.gradle.internal.impldep.org.apache.http.client.methods.HttpGet
import org.gradle.internal.impldep.org.apache.http.client.methods.HttpUriRequest
import org.gradle.internal.impldep.org.apache.http.impl.client.HttpClients


fun main() {
    HttpClients.createMinimal().use { client ->

        val key = "testKey"

        val resource = "cache?keys=$key&version=$key"

        println("Env: ${System.getenv()}")

        val baseUrl = (System.getenv("ACTIONS_CACHE_URL")?.ifBlank { null }
            ?: System.getenv("ACTIONS_RUNTIME_URL")?.ifBlank { null }
                )?.replace("pipelines", "artifactcache")
            ?: error("Could not find cache URL")

        val url = "${baseUrl}_apis/artifactcache/$resource"

        val result = client.execute(HttpGet(url)).use { response ->
            println("Status code: ${response.statusLine.statusCode}")
            response.entity.content.readAllBytes().decodeToString()

        }
        println("Body: $result")
    }

}