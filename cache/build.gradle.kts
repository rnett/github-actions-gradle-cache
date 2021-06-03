plugins{
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-gradle-plugin`
    application
}

dependencies{
    implementation(project(":shared"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")
}

kotlin{
    target{
        compilations.all { kotlinOptions.jvmTarget = "1.8" }
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
        }
    }
}


application{
    mainClass.set("com.rnett.actions.cache.TestKt")
}

tasks.test {
    useJUnit()
}