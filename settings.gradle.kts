// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

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
        // MPAndroidChart y MaterialShowcaseView solo se publican en JitPack
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\..*") }
        }
    }
}

rootProject.name = "vESTER"
include(":app")
