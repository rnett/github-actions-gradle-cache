package com.rnett.actions.cache

import com.rnett.action.core.env
import com.rnett.action.core.maskSecret
import com.rnett.action.core.runOrFail

fun main() = runOrFail{
    println("Test")
    val baseUrl = (
            env["ACTIONS_CACHE_URL"] ?: env["ACTIONS_RUNTIME_URL"] ?: error("Cache Service Url not found"))
        .replace("pipelines", "artifactcache") + "_apis/artifactcache/"

    maskSecret(baseUrl)
    env.export[EnviromentVariables.baseUrl] = baseUrl

    val runtimeToken = env["ACTIONS_RUNTIME_TOKEN"] ?: error("Could not get cache access token")
    maskSecret(runtimeToken)
    env.export[EnviromentVariables.token] = runtimeToken
}