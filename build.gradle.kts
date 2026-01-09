/*
 * Copyright (c) Runtime Inc., 2017-2018
 * Copyright (c) Intellinium SAS, 2014-2021
 * Copyright (c) Nordic Semiconductor ASA, 2021-present
 *
 * SPDX-License-Identifier: Apache-2.0
 */

// Load fork configuration
val forkConfigFile = file("fork.config")
val forkConfig = java.util.Properties()
if (forkConfigFile.exists()) {
    forkConfig.load(java.io.FileInputStream(forkConfigFile))
}
val githubUser = forkConfig["GITHUB_USER"]?.toString() ?: "jarrodmoldrich"
val mavenGroupId = forkConfig["MAVEN_GROUP_ID"]?.toString() ?: "io.github.jarrodmoldrich"
val libraryVersion = forkConfig["VERSION"]?.toString() ?: "1.0.0"

// Load publishing credentials from publishing.properties (local, not committed)
val publishingPropsFile = file("publishing.properties")
if (publishingPropsFile.exists()) {
    val publishingProps = java.util.Properties()
    publishingProps.load(java.io.FileInputStream(publishingPropsFile))
    publishingProps.forEach { key, value ->
        ext.set(key.toString(), value)
    }
}

// Make fork config available to subprojects
ext.set("githubUser", githubUser)
ext.set("mavenGroupId", mavenGroupId)

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false

    // This plugin is used to generate Dokka documentation.
    alias(libs.plugins.kotlin.dokka) apply false
    // This applies Nordic look & feel to generated Dokka documentation.
    // https://github.com/NordicSemiconductor/Android-Gradle-Plugins/blob/main/plugins/src/main/kotlin/NordicDokkaPlugin.kt
    alias(libs.plugins.nordic.dokka) apply true

    // Nordic Gradle Plugins
    // https://github.com/NordicSemiconductor/Android-Gradle-Plugins
    alias(libs.plugins.nordic.application) apply false
    alias(libs.plugins.nordic.library) apply false
    alias(libs.plugins.nordic.hilt) apply false
    alias(libs.plugins.nordic.kotlin.android) apply false
    alias(libs.plugins.nordic.nexus.android) apply false
}

// Apply version to all library modules
subprojects {
    version = libraryVersion
}

// Configure main Dokka page
dokka {
    pluginsConfiguration.html {
        homepageLink.set("https://github.com/$githubUser/Android-nRF-Connect-Device-Manager")
    }
}
