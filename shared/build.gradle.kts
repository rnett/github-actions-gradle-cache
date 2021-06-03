plugins{
    kotlin("multiplatform")
}

kotlin{
    jvm{
        compilations.all { kotlinOptions.jvmTarget = "1.8" }
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
        }
    }
    js(IR){
        nodejs()
    }
}