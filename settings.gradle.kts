pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "QuickLogWidget"
include(":app")
include(":wear")
include(":wear-protocol")
include(":relaylab-common")
project(":relaylab-common").projectDir = file("common/relaylab-common")
