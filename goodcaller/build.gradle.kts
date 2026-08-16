plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * GoodCaller — controlled demo sample.
 *
 * A caller-ID-shaped app whose observable security profile stays inside the CALLER_ID
 * baseline. It exists to prove Thraksha does NOT flag everything. Deliberately tiny:
 * no Compose, no Room, no network stack — the less it does, the less ambiguity there is
 * about why it is clean.
 */
android {
    namespace = "com.thraksha.demo.goodcaller"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.thraksha.demo.goodcaller"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}
