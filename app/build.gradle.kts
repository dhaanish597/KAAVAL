// :app — the Android application.
//
// Version pins come from handoff/versions_from_scratch.txt (a build that already
// succeeded on this machine). Do not bump anything mid-event.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.vaakku"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.vaakku"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-P0"

        // arm64-v8a only. The Qualcomm Hexagon NPU runtime and the sherpa-onnx
        // .so files are arm64; shipping 32-bit or x86 adds APK size and nothing
        // else. The iQOO 15 is arm64-v8a.
        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // NO applicationIdSuffix. The package must stay app.vaakku in every
            // build type, because the ASR models live in the app's external files
            // dir (/sdcard/Android/data/app.vaakku/files/models/) and a suffixed
            // debug package would look in a different, empty folder. §6.1.
            isMinifyEnabled = false
        }
        release {
            // R8 stays off: it can break the JNI/reflection paths in
            // sherpa-onnx and LiteRT, and there is no time in-window to write
            // and debug keep-rules (§6.1). Signed with the debug key for the
            // demo.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        compose = true
        // Needed for BuildConfig.DEBUG, which gates the whole Dev menu and the
        // Rung-0 probe so they cannot appear in a release build (§6.6).
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Required for the Qualcomm NPU runtime and sherpa .so files: they
            // must be extracted to the filesystem rather than loaded straight
            // from the APK (§6.1).
            useLegacyPackaging = true
        }
        resources {
            // Third-party SDKs (ML Kit in particular) ship overlapping licence
            // and metadata files; without these the merge fails.
            excludes += setOf(
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // JUnit 5, same engine :domain uses. The ASR subsystem has exactly one
        // part that is pure arithmetic — SegmentQuality — and it feeds straight
        // into the reconciler's confidence thresholds, so it is worth being able
        // to test without a phone attached. Everything else in asr/ needs real
        // AudioRecord/JNI and is verified on-device instead.
        unitTests.all { it.useJUnitPlatform() }
    }

    // The §11.1 clips are copied in from testdata/testaudio/ at build time rather
    // than committed a second time under app/src/. They are already tracked at
    // their canonical path; a duplicate copy in the source tree is 3.4 MB of
    // binary that can silently drift out of step with the labels that score it.
    sourceSets {
        getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/rehearsalAudio"))
        getByName("debug").assets.srcDir(layout.buildDirectory.dir("generated/bakeoffAudio"))
    }
}

/**
 * The rehearsal clip, in every build type.
 *
 * This one ships in release because §13's own fallback depends on it: if no
 * engine clears the ASR accuracy threshold, "the demo uses the rehearsal WAV as
 * primary and live mic as a 'try it' moment". A fallback that only exists in a
 * debug build is not a fallback.
 */
val syncRehearsalAudio by tasks.registering(Sync::class) {
    description = "Copies the rehearsal pitch clip into :app main assets (§11.1, §13)."
    from(rootProject.layout.projectDirectory.dir("testdata/testaudio")) {
        include("R01_demo_pitch.wav")
    }
    into(layout.buildDirectory.dir("generated/rehearsalAudio/testaudio"))
}

/**
 * T01–T14 and their labels, debug only.
 *
 * These exist to feed the bake-off screen, which is behind `BuildConfig.DEBUG`.
 * Shipping the scoring labels in a release APK would put the answer key next to
 * the exam for no benefit.
 */
val syncBakeoffAudio by tasks.registering(Sync::class) {
    description = "Copies the §11.1 test clips and labels.json into :app debug assets."
    from(rootProject.layout.projectDirectory.dir("testdata/testaudio")) {
        include("T*.wav")
        include("labels.json")
    }
    into(layout.buildDirectory.dir("generated/bakeoffAudio/testaudio"))
}

// preBuild is the one task every variant runs first, so both syncs land before
// asset merging regardless of which variant is being assembled.
tasks.named("preBuild") {
    dependsOn(syncRehearsalAudio, syncBakeoffAudio)
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // CameraX — all four on one version (§6.1). Not used until P3 (OCR), but
    // declared now so the dependency set is proven to resolve in P0 rather than
    // discovering a resolution problem in the middle of the OCR phase.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Bundled Latin OCR recognizer. Pulled in at P0 on purpose: it is the one
    // dependency that smuggles INTERNET into the merged manifest, so having it
    // here is what makes scripts/check_manifest.sh a real test instead of a
    // trivially-green one (§6.2).
    implementation(libs.mlkit.text.recognition)

    implementation(libs.litert)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // sherpa-onnx, as a local AAR (§6.1). Not on Maven at this pin, and the file
    // is gitignored because it is 50 MB — see models/MANIFEST.md for where it
    // came from. The AAR carries both the Kotlin API and the arm64-v8a .so set
    // (libsherpa-onnx-jni, libsherpa-onnx-c-api, libsherpa-onnx-cxx-api,
    // libonnxruntime); nothing else needs to be declared for the JNI to load.
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
