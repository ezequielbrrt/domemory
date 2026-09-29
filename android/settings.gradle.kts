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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DoMemory"
include(":app")

// WhatsNewKit-Android is a private repository, so it cannot be served through JitPack the
// way its README describes. It is vendored as a git submodule at android/WhatsNewKit-Android
// (pinned to a release tag) and only its library module is included here — never :demo. It
// builds against this build's version catalog, which is why libs.versions.toml carries the
// android-library plugin and compose-foundation aliases it references. A fresh clone needs
// `git submodule update --init` before Gradle can configure this project.
include(":whatsnewkit")
project(":whatsnewkit").projectDir = file("WhatsNewKit-Android/whatsnewkit")

// Same arrangement for ReviewFlow-Android (android/ReviewFlow-Android, also private, also
// pinned to a release tag): only its library module, built against this catalog — which is
// why libs.versions.toml carries play-review-ktx, kotlinx-coroutines-core and
// compose-material-icons-core for it.
include(":reviewflow")
project(":reviewflow").projectDir = file("ReviewFlow-Android/reviewflow")

// And for NotificationPermissionKit-Android (android/NotificationPermissionKit-Android): the
// notification permission primer. Everything it references already exists in the catalog.
include(":notificationpermissionkit")
project(":notificationpermissionkit").projectDir = file("NotificationPermissionKit-Android/notificationpermissionkit")
