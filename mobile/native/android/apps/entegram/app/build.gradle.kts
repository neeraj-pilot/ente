plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    val apiBaseUrl = providers.gradleProperty("entegramApiBaseUrl")
    val debugApiBaseUrl = providers
        .gradleProperty("entegramDebugApiBaseUrl")
        .orElse(apiBaseUrl)
        .getOrElse("http://10.0.2.2:8080")
    val releaseApiBaseUrl = providers
        .gradleProperty("entegramReleaseApiBaseUrl")
        .orElse(apiBaseUrl)
        .getOrElse("http://127.0.0.1:8080")
    val usesCleartextTraffic = providers
        .gradleProperty("entegramUsesCleartextTraffic")
        .getOrElse("false")
    val configuredAbiFilters = providers
        .gradleProperty("entegramAbiFilters")
        .map { value ->
            value.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        .getOrElse(listOf("arm64-v8a", "armeabi-v7a"))
    val universalApk = providers
        .gradleProperty("entegramUniversalApk")
        .map { it.toBoolean() }
        .getOrElse(true)

    namespace = "io.ente.entegram"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "io.ente.entegram"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "ENTEGRAM_API_BASE_URL", "\"$releaseApiBaseUrl\"")
        manifestPlaceholders["usesCleartextTraffic"] = "false"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include(*configuredAbiFilters.toTypedArray())
            isUniversalApk = universalApk
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = project.findProperty("ENTEGRAM_RELEASE_STORE_FILE") as? String
            if (storeFilePath != null && file(storeFilePath).exists()) {
                storeFile = file(storeFilePath)
                storePassword = project.findProperty("ENTEGRAM_RELEASE_STORE_PASSWORD") as? String
                keyAlias = project.findProperty("ENTEGRAM_RELEASE_KEY_ALIAS") as? String
                keyPassword = project.findProperty("ENTEGRAM_RELEASE_KEY_PASSWORD") as? String
            }
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "ENTEGRAM_API_BASE_URL", "\"$debugApiBaseUrl\"")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            resValue("string", "app_name", "enteGram Debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "ENTEGRAM_API_BASE_URL", "\"$releaseApiBaseUrl\"")
            manifestPlaceholders["usesCleartextTraffic"] = usesCleartextTraffic
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseConfig = signingConfigs.findByName("release")
            if (releaseConfig?.storeFile != null) {
                signingConfig = releaseConfig
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main").jniLibs.srcDirs("src/main/jniLibs")
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    // AndroidX
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.exifinterface)

    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // OkHttp
    implementation(libs.okhttp)
    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Security
    implementation(libs.security.crypto)

    // Coroutines
    implementation(libs.coroutines.android)

    // Testing
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
