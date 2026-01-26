plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    kotlin("plugin.noarg") version "1.8.0"
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.am24.am24"
    compileSdk = 35
    ndkVersion = "28.0.12433536"

    defaultConfig {
        applicationId = "com.am24.am24"
        minSdk = 26
        targetSdk = 35
        versionCode = 323
        versionName = "1.1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    //

    // ▼ This is the correct place
    bundle {
        language {
            // Pack every strings.xml into every install (no split APKs)
            enableSplit = false      // Kotlin-DSL: property assignment is fine
            // Or: enableSplit.set(false)
        }
    }


    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        buildConfig = true      // should be here
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.7.4"
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.firebase.analytics)
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.firebase:firebase-auth-ktx:23.2.1")
    implementation(libs.firebase.storage)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.coil.compose)
    implementation(libs.androidx.activity.compose.v180)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.coil.compose.v230)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)
    implementation("com.google.maps.android:android-maps-utils:2.3.0")
    implementation("com.android.billingclient:billing-ktx:8.0.0")
    implementation(libs.places)
    implementation(libs.firebase.functions)
    implementation("com.android.installreferrer:installreferrer:2.2")
    // For Coil image loading

    implementation(libs.coil.compose.v222)
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-video:2.2.2")
    implementation("io.coil-kt:coil:2.2.2")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.media3:media3-transformer:1.8.0")
    implementation("com.google.android.gms:play-services-ads:24.9.0")
    //geofire
    implementation(libs.firebase.geofire.android) // Check for the latest version
    implementation(libs.play.services.location)

    // For Jetpack Compose Navigation
    implementation(libs.androidx.navigation.compose)

    // For Coroutines and Firebase
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.firebase.database.ktx.v2022)
    implementation(libs.coil.compose.v200)
    implementation(libs.material.v150)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp) // Or your preferred OkHttp version
    implementation(libs.gson)
    implementation(libs.okhttp.v4100)

    // Core Android Libraries
    implementation(libs.androidx.core.ktx.v180)
    implementation (libs.gson)
    implementation("com.razorpay:checkout:1.6.41")
    implementation("androidx.webkit:webkit:1.10.0")    // optional helper for modern WebView
    implementation("com.facebook.android:facebook-login:18.0.3")
    implementation("com.facebook.android:facebook-android-sdk:18.0.3")
    implementation("com.github.yalantis:ucrop:2.2.11-native")

//    implementation(libs.core) // ARCore
    implementation(libs.picasso)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.compose.material)
    implementation("com.google.accompanist:accompanist-placeholder-material:0.34.0")

    implementation("com.google.firebase:firebase-appcheck-debug:18.0.0")
    implementation("com.google.firebase:firebase-appcheck-playintegrity:18.0.0")
    //swipable
    implementation(libs.accompanist.swiperefresh)
    implementation (libs.androidx.foundation.v100)
    implementation (libs.androidx.material.v100)
    implementation (libs.ui)
    implementation(libs.androidx.constraintlayout.compose.android)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.firebase.auth)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// ↓ configure the noArg extension at the bottom of the file:
noArg {
    // generate a zero-arg constructor for any class annotated with @IgnoreExtraProperties
    annotation("com.google.firebase.database.IgnoreExtraProperties")
}