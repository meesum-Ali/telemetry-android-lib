// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        // centralise all plugin versions here
        id("com.android.library")                     version "8.3.1"
        kotlin("android")                             version "1.9.23"
        id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"
    }
}

dependencyResolutionManagement {
    // keep project scripts repo-free
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "telemetry_android_lib"
include(":telemetry")
