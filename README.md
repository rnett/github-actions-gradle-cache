# GitHub Actions Gradle build cache

[![Maven Central](https://img.shields.io/maven-central/v/com.github.rnett.github-actions-gradle-cache/cache)](https://search.maven.org/artifact/com.github.rnett.github-actions-gradle-cache/cache)
[![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/com.github.rnett.github-actions-gradle-cache/cache?server=https%3A%2F%2Foss.sonatype.org)](https://oss.sonatype.org/content/repositories/snapshots/com/github/rnett/github-actions-gradle-cache/)

A gradle remote build cache backed by GitHub action's cache.

Built based on `@actions/cache` version `1.0.7`, if [the version](https://www.npmjs.com/package/@actions/cache) has
changed significantly since then this may not work.

Built using gradle `7.0.2`, using it with earlier versions may not work.

You may run into rate limiting (https://github.com/actions/toolkit/issues/738), but I have not seen that in practice.

## Use

Comes in two parts: the cache implementation itself, and a GitHub Action to set it up. The cache implementation
is `com.github.rnett.github-actions-gradle-cach:cache`. You could install it manually, but it requires a `baseUrl`
and `token`, and the only way to get those is from the environment of a GitHub Action (note that they are not propagated
to `run`'s environment).

To get around this, use the github action in this repo. You can include it
as `rnett/github-actions-gradle-cache@$version`. It has three parameters, all optional:

* `version` - the version of the cache implementation to use. By default is the same as the action.
* `is-push` - whether to push to the remote build cache. True by default.
* `use-build-cache` - whether to add `org.gradle.caching=true` to `~/.gradle/gradle.properties`. True by default.

It works by creating an init script (`~/.gradle/init.d/gh_actions_cache.init.gradle.kts`) that adds a dependency
on `cache` and configures it. If `enable` is also true, it adds `org.gradle.caching=true`
to `~/.gradle/gradle.properties` to ensure the build cache is used.

### Full cache

The action also has the ability to wrap `actions/cache` (well, not exactly, but it is very close) with reasonable
defaults for gradle, controlled by 4 more parameters (also all optional):

* `full-cache` - whether to do this directory caching in addition to using the build cache. True by default.
* `cache-key` - the key to use for the cache. By default, will be calculated as described below.
* `cache-key-prefix` - a prefix for the key, if specified. Will be added to the default key as well.
* `cache-key-postfix` - a prefix for the key, if specified. Will be added to the default key as well. Will be dropped
  from restore keys.
* `restore-keys` - restore keys to use if `cache-key` is specified.
* `cache-paths` - More paths to cache (normal globs, not relative to `~/.gradle` or anywhere else).

It comes with defaults set for Gradle, so by default it cashes these directories in `~/.gradle`:

* `nodejs`
* `wrapper`
* `yarn`
* `jdks`
* children of `caches` expect for `build-cache*`, to avoid duplicating.

It also caches `~/konan`, for Kotlin/Native support.

Like `actions/cache`, it also has an output `cache-hit`.

Files (including parts of the defaults) can be ignored by passing them to `cache-paths` starting with a `!`, the
same `actions/cache`.

If `cache-key` is not specified, it uses `$prefix-gradle-auto-cache-$currentOS-$workflow-$jobId-$postfix-$hash`,
where `$hash` is a hash of `**/*.gradle*`, `**/buildSrc/src/**`, `**/*gradle.lockfile`, `**/gradle-wrapper.properties`,
and `**/gradle.properties` using `hashFiles`. Restore keys omitting the hash, jobId, workflow, and postfix will be used.
Neither `$prefix` or `$postfix` will be included if not specified.

Note that the cache upload task will also stop any gradle daemons and delete `.lock` and `gc.properties`, as
recommend [here](https://docs.gradle.org/current/userguide/dependency_resolution.html#sub:cache_copy).