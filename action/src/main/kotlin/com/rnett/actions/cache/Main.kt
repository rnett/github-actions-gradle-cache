package com.rnett.actions.cache

import com.rnett.action.Path
import com.rnett.action.core.env

fun main() {
    println("Test")
    val baseUrl = (
            env["ACTIONS_CACHE_URL"] ?: env["ACTIONS_RUNTIME_URL"] ?: error("Cache Service Url not found"))
        .replace("pipelines", "artifactcache") + "_apis/artifactcache/"

    println("BaseURL: $baseUrl")
    Path("~/.cache-baseurl").write(baseUrl)
}