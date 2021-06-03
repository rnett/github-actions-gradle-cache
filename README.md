# GitHub Actions Gradle build cache

[![Maven Central](https://img.shields.io/maven-central/v/com.github.rnett.github-actions-gradle-cache/cache)](https://search.maven.org/artifact/com.github.rnett.github-actions-gradle-cache/cache)
[![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/com.github.rnett.github-actions-gradle-cache/cache?server=https%3A%2F%2Foss.sonatype.org)](https://oss.sonatype.org/content/repositories/snapshots/com/github/rnett/github-actions-gradle-cache/)

A gradle remote build cache backed by GitHub action's cache.

Built based on `@actions/cache` version `1.0.7`, if [the version](https://www.npmjs.com/package/@actions/cache) has
changed significantly since then this may not work.

Built using gradle `7.0.2`, using it with earlier versions may not work.

Comes in two parts: the cache implementation itself, and a GitHub Action to set it up. The cache implementation
is `com.github.rnett.github-actions-gradle-cach:cache`. You could install it manually, but it requires a `baseUrl`
and `token`, and the only way to get those is from the environment of a GitHub Action (note that they are not propagated
to `run`'s environment).

To get around this, use the github action in this repo. You can include it
as `rnett/github-actions-gradle-cache@$version`. It has three parameters, all optional:

* `version` - the version of the cache implementation to use. By default is the same as the action.
* `is-push` - whether to push to the remote build cache. True by default.
* `enable` - whether to add `org.gradle.caching=true` to `~/.gradle/gradle.properties`. True by default.

It works by creating an init script (`~/.gradle/init.d/gh_actions_cache.init.gradle.kts`) that adds a dependency
on `cache` and configures it. If `enable` is also true, it adds `org.gradle.caching=true`
to `~/.gradle/gradle.properties` to ensure the build cache is used.