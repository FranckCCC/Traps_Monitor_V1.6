plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

val versionMajor = 1
val versionMinor = 6
val versionPatch = 2
val versionBuild = 35

val bundleId = "fr.kewan.trapsmonitor"

val minimumVersion = 19 // Android 4.4
val buildVersion = 34 // Android 11

android {
    namespace = bundleId
    compileSdk = buildVersion

    defaultConfig {
        applicationId = bundleId
        minSdk = minimumVersion
        targetSdk = buildVersion
        versionCode = versionMajor * 10000 + versionMinor * 1000 + versionPatch * 100 + versionBuild
        versionName = "${versionMajor}.${versionMinor}.${versionPatch}"
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }

        debug {
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")

    implementation(libs.androidx.legacysupport)

    implementation(libs.paho.mqtt)
    implementation(libs.paho.mqtt.service)

    implementation(libs.androidAppUpdateLibrary)

    implementation("com.android.support:multidex:1.0.3")
    implementation("com.google.android.material:material:1.4.0")
    implementation("com.google.firebase:firebase-storage:19.2.0") {
        exclude(group = "com.google.firebase", module = "firebase-storage-ktx")
    }
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
