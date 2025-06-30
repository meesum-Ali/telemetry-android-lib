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
        id("org.jetbrains.dokka")                     version "1.9.20" // Added for KDoc/Javadoc generation
        id("io.github.gradle-nexus.publish-plugin")   version "2.0.0" // Added for Sonatype publishing
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
