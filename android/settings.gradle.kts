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
