/*
 * Copyright (c) Runtime Inc., 2017-2018
 * Copyright (c) Intellinium SAS, 2014-2021
 * Copyright (c) Nordic Semiconductor ASA, 2021-present
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.nordic.library)
    alias(libs.plugins.nordic.kotlin.android)
    `maven-publish`
    signing
}

// Get fork configuration from root project
val githubUser: String by rootProject.extra
val mavenGroupId: String by rootProject.extra

group = mavenGroupId

android {
    namespace = "io.runtime.mcumgr"

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    compileOptions {
        // for now and foreseeable future we intentionally set the build system to emit bytecode that is compatible with
        // java11 so as to ensure that we don't break the "classic xamarin (mono)" toolchain for C# android-java-bindings
        // which employs an outdated version of r8 that can only handle java11 bytecode
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            // for now and foreseeable future we intentionally set the build system to emit bytecode that is compatible with
            // java11 so as to ensure that we don't break the "classic xamarin (mono)" toolchain for C# android-java-bindings
            // which employs an outdated version of r8 that can only handle java11 bytecode
            jvmTarget = JvmTarget.JVM_11
        }
    }
}

dependencies {
    // Annotations
    implementation(libs.annotations)

    // Logging using SLF4J. Specify binding in the application.
    implementation(libs.slf4j)

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Import CBOR parser - version 2.14+ requires Android 8
    // See: https://github.com/NordicSemiconductor/Android-nRF-Connect-Device-Manager/issues/135
    //noinspection NewerVersionAvailable
    implementation(libs.fasterxml.cbor)
    //noinspection NewerVersionAvailable
    implementation(libs.fasterxml.core)
    //noinspection NewerVersionAvailable
    implementation(libs.fasterxml.databind)

    // Test
    testImplementation(libs.kotlin.test)
}
// Maven Central Publishing Configuration
val mavenCentralUsername: String? by project
val mavenCentralPassword: String? by project

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = project.group.toString()
                artifactId = "mcumgr-core"
                version = project.version.toString()

                pom {
                    name.set("McuManager Core")
                    description.set("A mobile management library for devices running nRF Connect SDK, Zephyr or Apache Mynewt (DFU, file system, logs, stats, config, etc.).")
                    url.set("https://github.com/$githubUser/Android-nRF-Connect-Device-Manager")

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set(githubUser)
                            name.set(githubUser)
                            url.set("https://github.com/$githubUser")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/$githubUser/Android-nRF-Connect-Device-Manager.git")
                        developerConnection.set("scm:git:ssh://github.com/$githubUser/Android-nRF-Connect-Device-Manager.git")
                        url.set("https://github.com/$githubUser/Android-nRF-Connect-Device-Manager")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "MavenCentral"
                // Use OSSRH staging which works with Central Portal accounts
                url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                credentials {
                    username = mavenCentralUsername
                    password = mavenCentralPassword
                }
            }
        }
    }

    signing {
        // Use GPG command (works with GPG agent)
        useGpgCmd()
        sign(publishing.publications["release"])
    }
}
