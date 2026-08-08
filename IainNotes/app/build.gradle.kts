// Use this instead
import com.android.sdklib.internal.avd.QuickBoot.arguments
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.iainnotes"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.example.iainnotes"
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0_main"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=none")
            }
        }
        ndk { abiFilters += listOf("arm64-v8a") }
        buildConfigField("String", "DATA_DIR", "\"IainNotes\"")
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "DATA_DIR", "\"IainNotes-dev\"")
            resValue("string", "app_name", "IainNotes dev")
        }
        release {
            resValue("string", "app_name", "IainNotes")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            // Use JvmTarget.JVM_17 instead of "17"
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        resValues = true
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.gridlayout)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.commons.compress) // tar archive support. // Deprecated by project: Remove after migration is complete.

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}