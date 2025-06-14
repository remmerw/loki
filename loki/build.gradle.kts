
import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "io.github.remmerw"
version = "0.1.0"

kotlin {

    jvm()
    androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()


    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.io.core)
                implementation(libs.uri.kmp)
                implementation(libs.atomicfu)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.sha1)
                implementation(libs.crypto.rand)
                implementation(libs.ktor.network)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        jvmMain {
            dependencies {
            }
        }

        androidMain {
            dependencies {
            }
        }

        iosMain {
            dependencies {
            }
        }
    }
}


android {
    namespace = "io.github.remmerw.loki"
    compileSdk = 36
    defaultConfig {
        minSdk = 27
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}


mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

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
