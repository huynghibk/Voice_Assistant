pluginManagement {
    repositories {
        google()
        // Corporate proxy blocks repo1.maven.org / plugins.gradle.org (407).
        // Google-hosted Maven Central mirror is reachable and has Kotlin plugins.
        maven {
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
            name = "GoogleMavenCentral"
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven {
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
            name = "GoogleMavenCentral"
        }
        mavenCentral()
    }
}

rootProject.name = "VoiceAssistant"
include(":app")
