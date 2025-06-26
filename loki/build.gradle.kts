@file:OptIn(ExperimentalWasmDsl::class, ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "io.github.remmerw"
version = "0.2.2"

kotlin {


    androidTarget {
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }


    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    // linuxArm64()
    // linuxX64()
    // linuxArm64()
    // wasmJs()
    // wasmWasi()
    // js()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.io.core)
                implementation(libs.uri.kmp)
                implementation(libs.atomicfu) // todo remove
                implementation(libs.ktor.network)
                implementation("com.ditchoom:buffer:1.4.2") // todo remove future
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.kotlin.test)
            implementation("androidx.test:runner:1.6.2")
        }
    }
}


android {
    namespace = "io.github.remmerw.loki"
    compileSdk = 36
    defaultConfig {
        minSdk = 27
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    packaging {
        resources.excludes.add("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}


mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "loki", version.toString())

    pom {
        name = "loki"
        description = "A library for downloading magnet-Uris"
        inceptionYear = "2025"
        url = "https://github.com/remmerw/loki/"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "remmerw"
                name = "Remmer Wilts"
                url = "https://github.com/remmerw/"
            }
        }
        scm {
            url = "https://github.com/remmerw/loki/"
            connection = "scm:git:git://github.com/remmerw/loki.git"
            developerConnection = "scm:git:ssh://git@github.com/remmerw/loki.git"
        }
    }
}
