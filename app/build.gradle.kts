import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// ---------------------------------------------------------------------------------------
// PRIVATE-ALPHA RELEASE SIGNING — Phase 12.1 Task B.
//
// Credentials are read from an UNTRACKED `keystore.properties` at the repository root, or
// from environment variables, in that order. Nothing here contains a secret; the file that
// does is git-ignored (`.gitignore` line 30) and the keystore itself must live OUTSIDE the
// repository entirely (see keystore.properties.template).
//
// There is deliberately NO fallback to the Android debug key. If credentials are missing,
// a release build fails loudly rather than quietly producing an APK signed with a key whose
// private half ships on every developer machine on earth. That silent fallback is what RC1
// did, and it is why RC1 cannot be updated in place.
// ---------------------------------------------------------------------------------------
val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingCredential(propertyKey: String, environmentKey: String): String? =
    (keystoreProperties.getProperty(propertyKey) ?: System.getenv(environmentKey))
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

val releaseStorePath = signingCredential("storeFile", "THRAKSHA_KEYSTORE_FILE")
val releaseStorePassword = signingCredential("storePassword", "THRAKSHA_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingCredential("keyAlias", "THRAKSHA_KEY_ALIAS")
val releaseKeyPassword = signingCredential("keyPassword", "THRAKSHA_KEY_PASSWORD")

val releaseKeystore: File? = releaseStorePath?.let { File(it) }?.takeIf { it.isFile }

/** Every credential present AND the keystore file actually readable at the given path. */
val releaseSigningReady: Boolean = releaseKeystore != null &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

/**
 * Guide §13: the keystore must not live inside the repository. Enforced, not merely
 * documented — a keystore under the project tree is one `git add -f` away from being
 * published forever.
 */
if (releaseKeystore != null &&
    releaseKeystore.canonicalPath.startsWith(rootProject.rootDir.canonicalPath + File.separator)
) {
    throw GradleException(
        "Release keystore is inside the repository at ${releaseKeystore.canonicalPath}. " +
            "Move it to a private directory outside ${rootProject.rootDir.canonicalPath} " +
            "and update keystore.properties.",
    )
}

android {
    namespace = "com.thraksha.guardian"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.thraksha.guardian"
        minSdk = 26
        targetSdk = 36
        // Phase 12.1 §20 — RC2 versioning.
        //
        // versionCode is the OS's update ordinal and must increase for a later build to
        // install over an earlier one. RC1 shipped 1; RC2 is 2. Every subsequent
        // private-alpha build increments it, whatever the marketing name says.
        //
        // versionName is human-facing only (Settings → About shows
        // "Version <name> (<code>)"). It carries the full candidate identity so a phone
        // in the field can be identified from its About screen alone, and it matches the
        // distributed filename thraksha-guardian-1.0-privatealpha-rc2.apk exactly.
        versionCode = 2
        versionName = "1.0-privatealpha-rc2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Phase 10A: LiteRT-LM ships liblitertlm_jni.so for arm64-v8a and x86_64 ONLY.
        // Restricting the ABI set makes that explicit rather than shipping a 32-bit APK
        // that installs fine and then fails at model load. Both demo targets are covered:
        // S20 FE = arm64-v8a, thraksha_do AVD = x86_64.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("privateAlpha") {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // minSdk 26 only needs v2, but v3 costs nothing and enables future key
                // rotation without changing the app's identity to already-installed users.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // R8 deliberately left OFF for the private-alpha candidate (Phase 12A §5).
            // The build ships four reflective/JNI surfaces that R8 would need bespoke,
            // individually re-verified keep rules for: LiteRT-LM's native engine,
            // SQLCipher's JNI layer, Room's KSP-generated DAOs and kotlinx.serialization's
            // generated serializers. Enabling shrinking here would invalidate the frozen
            // Phase 11B verification for a benefit — code obfuscation — that is not a
            // security control for an app the owner installs on their own device.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // PERMANENT PRIVATE-ALPHA IDENTITY — Phase 12.1 Task B.
            //
            // Replaces RC1's temporary debug-key signing. The config exists only when the
            // owner's credentials resolve (see the block above `android {}`); when they do
            // not, this stays null and the gate below fails the build. No debug fallback.
            signingConfig = if (releaseSigningReady) {
                signingConfigs.getByName("privateAlpha")
            } else {
                null
            }
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
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

/**
 * Hard gate: a release APK/AAB must never be produced unsigned or debug-signed.
 *
 * Without this, AGP would silently emit `app-release-unsigned.apk` when `signingConfig` is
 * null — which looks like success in a log and is not. The check runs at execution time so
 * that debug builds, unit tests and IDE sync stay unaffected when no credentials are set up.
 */
// Wrapped in `run {}` so the task action closes over plain local values rather than script
// object references, which the Gradle configuration cache cannot serialize.
run {
    val signingReady = releaseSigningReady
    val missing = buildList {
        if (releaseStorePath == null) add("storeFile / THRAKSHA_KEYSTORE_FILE")
        else if (releaseKeystore == null) add("storeFile points at a non-existent file")
        if (releaseStorePassword == null) add("storePassword / THRAKSHA_KEYSTORE_PASSWORD")
        if (releaseKeyAlias == null) add("keyAlias / THRAKSHA_KEY_ALIAS")
        if (releaseKeyPassword == null) add("keyPassword / THRAKSHA_KEY_PASSWORD")
    }
    val failureMessage = """
        Release signing credentials are not configured — refusing to build.

        Missing: ${missing.joinToString(", ")}

        Provide them in an untracked keystore.properties at the repository root
        (see keystore.properties.template) or via environment variables.

        There is NO debug-key fallback by design: a debug-signed release cannot be
        updated by a properly-signed build, and the debug private key is not secret.
        See temp/phase12_1/PHASE12_1_SIGNING.md.
    """.trimIndent()

    tasks.matching { it.name in setOf("packageRelease", "assembleRelease", "bundleRelease") }
        .configureEach {
            doFirst {
                if (!signingReady) throw GradleException(failureMessage)
            }
        }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")

    // Android core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Compose
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Room (KSP-first; room-ktx merged into room-runtime as of 2.7)
    implementation("androidx.room:room-runtime:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // androidx.sqlite — pinned to match Room's transitive pin AND SQLCipher's SupportSQLiteOpenHelper API
    implementation("androidx.sqlite:sqlite:2.6.2")
    implementation("androidx.sqlite:sqlite-framework:2.6.2")

    // SQLCipher — encrypted DB; native .so bundled in the AAR (arm64/armv7/x86/x86_64)
    implementation("net.zetetic:sqlcipher-android:4.17.0@aar")

    // JSON — signed rulepack schema (Kotlin-2.2-built runtime)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Phase 10 — on-device LLM runtime. Official Google Maven artifact, version PINNED
    // (never `latest.release`: the demo baseline must not move under us). Apache-2.0.
    // Consumes the developer-provisioned .litertlm bundle; see
    // temp/automation injection docs/10a_runtime/PHASE10A_RUNTIME_BRINGUP.md
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.0")

    // Unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // Instrumented tests
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}