package com.rnett.actions.cache

import com.rnett.action.core.state
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


object SharedState {
    var didCache by state
    var primaryKey by state

    private var cachePathsInternal by state

    var cachePaths: List<String>
        get() = cachePathsInternal.split("|")
        set(value) {
            cachePathsInternal = value.joinToString("|")
        }
    var exactMatch by state
    private val json = Json { }

    private var cacheStateInternal by state
    var cacheState: Caching.CacheState
        get() = json.decodeFromString(cacheStateInternal)
        set(value) {
            cacheStateInternal = json.encodeToString(value)
        }

}