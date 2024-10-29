plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.am24.am24"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.am24.am24"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
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
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.libphonenumber)
    implementation(libs.coil.compose)
    implementation(libs.androidx.activity.compose.v180)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.glide)
    implementation(libs.ccp)
    implementation(libs.androidx.material.icons.extended)

    // For Coil image loading
    implementation(libs.coil.compose.v222)
    implementation(libs.gpuimage)

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


    // Core Android Libraries
    implementation(libs.androidx.core.ktx.v180)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation (libs.gson)

    implementation(libs.core) // ARCore
    implementation(libs.picasso)
    implementation(libs.material)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.compose.material)

    //swipable
    implementation(libs.accompanist.swiperefresh)
    implementation (libs.androidx.foundation.v100)
    implementation (libs.androidx.material.v100)
    implementation (libs.ui)
    implementation (libs.androidx.runtime.livedata)
    implementation(libs.androidx.constraintlayout.compose.android)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}