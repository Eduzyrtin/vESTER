// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 ya compila Kotlin por sí mismo; esto solo fija la versión del compilador de Kotlin
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
