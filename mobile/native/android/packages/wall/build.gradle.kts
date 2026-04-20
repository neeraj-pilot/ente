plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "io.ente.entegram"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        buildConfigField("String", "ENTEGRAM_API_BASE_URL", "\"\"")
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets["main"].jniLibs.srcDir("src/main/jniLibs")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api("net.java.dev.jna:jna:5.14.0@aar")

    implementation("androidx.annotation:annotation:1.7.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.dagger:hilt-android:2.53.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    kapt("androidx.room:room-compiler:2.6.1")
}
