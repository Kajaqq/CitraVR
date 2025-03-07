// Copyright 2023 Citra Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

import android.databinding.tool.ext.capitalizeUS
import de.undercouch.gradle.tasks.download.Download

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("de.undercouch.download") version "5.5.0"
    id("kotlin-parcelize")
    kotlin("plugin.serialization") version "1.9.22"
    id("androidx.navigation.safeargs.kotlin")
}

/**
 * Use the number of seconds/10 since Jan 1 2016 as the versionCode.
 * This lets us upload a new build at most every 10 seconds for the
 * next 680 years.
 */
val autoVersion = (((System.currentTimeMillis() / 1000) - 1451606400) / 10).toInt()
val abiFilter = listOf("arm64-v8a")

val downloadedJniLibsPath = "${buildDir}/downloadedJniLibs"

@Suppress("UnstableApiUsage")
android {
    namespace = "org.citra.citra_emu"

    compileSdkVersion = "android-34"
    ndkVersion = "26.1.10909125"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        // This is necessary for libadrenotools custom driver loading
        jniLibs.useLegacyPackaging = true
    }

    buildFeatures {
        viewBinding = true
    }

    lint {
        // This is important as it will run lint but not abort on error
        // Lint has some overly obnoxious "errors" that should really be warnings
        abortOnError = false
    }

    defaultConfig {
        // TODO If this is ever modified, change application_id in strings.xml
        applicationId = "org.citra.citra_emu"
        minSdk = 28
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 30
        versionCode = autoVersion
        versionName = getGitVersion()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += abiFilter
        }

        externalNativeBuild {
            cmake {
                arguments(
                    "-DENABLE_QT=0", // Don't use QT
                    "-DENABLE_SDL2=0", // Don't use SDL
                    "-DANDROID_ARM_NEON=true" // cryptopp requires Neon to work
                )
            }
        }

        buildConfigField("String", "GIT_HASH", "\"${getGitHash()}\"")
        buildConfigField("String", "BRANCH", "\"${getBranch()}\"")
    }

    val keystoreFile = System.getenv("ANDROID_KEYSTORE_FILE")
    if (keystoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASS")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEYSTORE_PASS")
            }
        }
    }

    // Define build types, which are orthogonal to product flavors.
    buildTypes {
        // Signed by release key, allowing for upload to Play Store.
        release {
            signingConfig = if (keystoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }

        // builds a release build that doesn't need signing
        // Attaches 'debug' suffix to version and package name, allowing installation alongside the release build.
        register("relWithDebInfo") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = true
            isJniDebuggable = true
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
            isDefault = true
        }

        // Signed by debug key disallowing distribution on Play Store.
        // Attaches 'debug' suffix to version and package name, allowing installation alongside the release build.
        debug {
            // TODO If this is ever modified, change application_id in debug/strings.xml
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            isJniDebuggable = true
        }
    }

    flavorDimensions.add("version")
    productFlavors {
        create("canary") {
            dimension = "version"
            applicationIdSuffix = ".playtest"
        }

        create("nightly") {
            dimension = "version"
        }
    }

    externalNativeBuild {
        cmake {
            version = "3.22.1"
            path = file("../../../CMakeLists.txt")
        }
    }

    sourceSets {
        named("main") {
            // Set up path for downloaded native libraries
            jniLibs.srcDir(downloadedJniLibsPath)
        }
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.slidingpanelayout:slidingpanelayout:1.2.0")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.work:work-runtime:2.9.0")
    implementation("org.ini4j:ini4j:0.5.4")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
    implementation("info.debatty:java-string-similarity:2.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("io.coil-kt:coil:2.5.0")

    androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")
    androidTestImplementation("junit:junit:4.12")

    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.0")
}

// Specify the destination file path and check path
val downloadedVulkanLayersZip = file("${buildDir}/tmp/Vulkan-ValidationLayers.zip")
val validationLayersExtractedPath = file(downloadedJniLibsPath)

// Download Vulkan Validation Layers only if not already downloaded or extracted
val downloadVulkanValidationLayers = tasks.register<Download>("downloadVulkanValidationLayers") {
    src("https://github.com/KhronosGroup/Vulkan-ValidationLayers/releases/download/sdk-1.3.261.1/android-binaries-sdk-1.3.261.1-android.zip")
    dest(downloadedVulkanLayersZip)
    onlyIf {
        !validationLayersExtractedPath.exists() // Skip download if extracted files are already present
    }
    onlyIfModified(true) // Check modification based on the existing ZIP
}

// Extract Vulkan Validation Layers only if not already extracted
val unzipVulkanValidationLayers = tasks.register<Copy>("unzipVulkanValidationLayers") {
    dependsOn(downloadVulkanValidationLayers)
    from(zipTree(downloadVulkanValidationLayers.get().dest)) {
        eachFile {
            // Exclude the top-level directory in the zip
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
        }
        includeEmptyDirs = false
    }
    into(validationLayersExtractedPath)
    onlyIf {
        !validationLayersExtractedPath.exists() // Skip extraction if already done
    }
}

// Ensure that the preBuild task depends on the extraction task
tasks.named("preBuild") {
    dependsOn(unzipVulkanValidationLayers)
}

fun getGitVersion(): String {
    var versionName = "0.0"

    try {
        // First, try to get the most recent tag associated with the current commit
        versionName = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
            .directory(project.rootDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start().inputStream.bufferedReader().use { it.readText() }
            .trim()

        // If no tags are found, use commit hash
        if (versionName.isEmpty()) {
            versionName = ProcessBuilder("git", "describe", "--always", "--long")
                .directory(project.rootDir)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start().inputStream.bufferedReader().use { it.readText() }
                .trim()
                .replace(Regex("(-0)?-[^-]+$"), "")
        }
    } catch (e: Exception) {
        logger.error("Cannot find git, defaulting to dummy version number")
    }
    return versionName
}


fun getGitHash(): String =
    runGitCommand(ProcessBuilder("git", "rev-parse", "--short", "HEAD")) ?: "dummy-hash"

fun getBranch(): String =
    runGitCommand(ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")) ?: "dummy-branch"

fun runGitCommand(command: ProcessBuilder) : String? {
    try {
        command.directory(project.rootDir)
        val process = command.start()
        val inputStream = process.inputStream
        val errorStream = process.errorStream
        process.waitFor()

        return if (process.exitValue() == 0) {
            inputStream.bufferedReader()
                .use { it.readText().trim() } // return the value of gitHash
        } else {
            val errorMessage = errorStream.bufferedReader().use { it.readText().trim() }
            logger.error("Error running git command: $errorMessage")
            return null
        }
    } catch (e: Exception) {
        logger.error("$e: Cannot find git")
        return null
    }
}

android.applicationVariants.configureEach {
    val variant = this
    val capitalizedName = variant.name.capitalizeUS()

    val copyTask = tasks.register("copyBundle${capitalizedName}") {
        doLast {
            project.copy {
                from(variant.outputs.first().outputFile.parentFile)
                include("*.apk")
                into(layout.buildDirectory.dir("bundle"))
            }
            project.copy {
                from(layout.buildDirectory.dir("outputs/bundle/${variant.name}"))
                include("*.aab")
                into(layout.buildDirectory.dir("bundle"))
            }
        }
    }
    tasks.named("bundle${capitalizedName}").configure { finalizedBy(copyTask) }
}
