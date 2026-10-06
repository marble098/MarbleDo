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

rootProject.name = "MarbleDo"
include(":app")
include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":feature:tasks")
include(":feature:calendar")
include(":feature:countdown")
include(":baselineprofile")
