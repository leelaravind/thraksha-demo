plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * VillainCaller — controlled demo sample with a deliberately inappropriate profile.
 *
 * Same caller-ID shape as GoodCaller, but its manifest declares capabilities no
 * caller-ID app needs. The mismatch is entirely STATIC and entirely in the manifest:
 * this app performs no malicious behaviour, reads no user data, and sends nothing
 * anywhere. What Thraksha detects is the declared capability, not an action.
 */
android {
    namespace = "com.thraksha.demo.villaincaller"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.thraksha.demo.villaincaller"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // VillainCaller signs with its own committed demo keystore rather than the shared
    // debug key. Both decoys carrying the identical debug certificate would make a
    // SIGNING_CERT_SHA256 threat indicator match GoodCaller too; a distinct signer is
    // what makes certificate identity a usable, controlled test signal (Phase 6).
    // This keystore is the *sample's* identity — it is NOT part of Guardian's trust
    // model and protects nothing, which is why committing it is acceptable. The
    // rulepack/threatpack signing keys remain gitignored under keys/.
    signingConfigs {
        create("villain") {
            storeFile = file("villain-demo.keystore")
            storePassword = "villainpass"
            keyAlias = "villain"
            keyPassword = "villainpass"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("villain")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("villain")
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
