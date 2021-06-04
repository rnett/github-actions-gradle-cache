package com.rnett.actions.cache

import com.rnett.action.core.state

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
}