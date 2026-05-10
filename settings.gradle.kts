pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "aetheris"

// App module (single-activity entry point; sets applicationId = dev.aetheris.planner per design §2.1)
include(":app")

// :core:* modules
include(":core:common")
include(":core:domain")
include(":core:data")
include(":core:scheduling")
include(":core:notifications")

// :feature:* modules
include(":feature:timeline")
include(":feature:tasks")
include(":feature:chat")
include(":feature:quickcapture")
include(":feature:settings")
include(":feature:habits")
include(":feature:focus")
include(":feature:rituals")

// :ai:* modules
include(":ai:router")
include(":ai:nlparser")
include(":ai:entity-resolver")
include(":ai:llm")
include(":ai:speech")

// :distribution:* modules — INTERNET-bearing module lives here (see design §2.4)
include(":distribution:model-downloader")

// :buildlogic:* modules — build-time-only checks that ship as Lint rule JARs
// bundled into every Android module via the `lintChecks` configuration.
// Task 2.3 introduces `:buildlogic:manifest-lint-rules` for the custom
// Lint detector that enforces the INTERNET / ACCESS_NETWORK_STATE /
// ACCESS_WIFI_STATE invariant at the post-merge manifest layer (design §12.3).
include(":buildlogic:manifest-lint-rules")
