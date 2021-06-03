package com.rnett.actions.cache

import org.gradle.caching.configuration.AbstractBuildCache

abstract class GHActionsBuildCache() : AbstractBuildCache() {
    var baseUrl: String? = null
    var token: String? = null
}

