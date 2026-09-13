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
        // The §6.5 segmentation model and the Qualcomm NPU runtime, both copied
        // in from handoff/ at build time for the same reason as the audio above:
        // 126 MB of binaries that are already on disk once.
        getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/npuModel"))
        getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/npuRuntime"))
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

/**
 * The §6.5 person-segmentation model.
 *
 * Copied out of `handoff/` (gitignored, 16 MB) rather than committed, like the
 * sherpa AAR and the ASR models. Renamed on the way in: the file downloaded
 * from HuggingFace is `selfie_multiclass.tflite`, and `PersonMasker.MODEL_ASSET`
 * names it `selfie_multiclass_256x256.tflite` after the build plan, because the
 * input size is the one property of it a reader needs and the model's own
 * naming has drifted between Google's sources.
 */
val syncNpuModel by tasks.registering(Sync::class) {
    description = "Copies the §6.5 segmentation model into :app main assets."
    from(rootProject.layout.projectDirectory.file("handoff/npu/selfie_multiclass.tflite")) {
        rename { "selfie_multiclass_256x256.tflite" }
    }
    into(layout.buildDirectory.dir("generated/npuModel/npu"))
}

/**
 * The Qualcomm NPU runtime for the iQOO 15, as plain `jniLibs`.
 *
 * **Why not dynamic features.** Google's sample ships five vendor runtimes as
 * `com.android.dynamic-feature` modules behind an AAB and device-group
 * targeting, because on Play it would otherwise send 110 MB of Qualcomm
 * libraries to a Samsung phone. We install one APK on one known phone over adb,
 * so that machinery buys nothing and costs the `installDebug` path, bundletool,
 * and a silent failure mode where a device group does not match and the module
 * is simply absent.
 *
 * What makes the simple route work is where LiteRT looks:
 * `BuiltinNpuAcceleratorProvider.getLibraryDir()` returns
 * `context.applicationInfo.nativeLibraryDir` — the app's own native library
 * directory, which is exactly where `jniLibs` land. A dynamic feature's
 * libraries end up in the same directory; the module boundary only decides
 * *when* they are delivered. `useLegacyPackaging = true` (set above) is the
 * part that is not optional: these are `dlopen`ed by path, so they have to be
 * extracted to disk at install time rather than mapped out of the APK.
 *
 * v81 only: `libQnnHtpV81Skel.so` is the Hexagon v81 skeleton, which is the
 * SM8850's. Shipping v69/v73/v75/v79 as well would add roughly 400 MB for
 * hardware that is not in the room.
 */
val syncNpuRuntime by tasks.registering(Sync::class) {
    description = "Copies the Qualcomm v81 (SM8850) NPU runtime .so files into :app jniLibs."
    from(
        rootProject.layout.projectDirectory.dir(
            "handoff/litert-samples/litert-samples-main/samples/litert/image_segmentation/" +
                "kotlin_npu/android_jit/litert_npu_runtime_libraries/qualcomm_runtime_v81/" +
                "src/main/jni/arm64-v8a",
        ),
    ) {
        include("*.so")
    }
    into(layout.buildDirectory.dir("generated/npuRuntime/arm64-v8a"))
}

/**
 * Fails the build if the NPU inputs are missing, instead of shipping an APK
 * that silently cannot reach the NPU.
 *
 * `handoff/` is gitignored, so a fresh clone has none of this. The failure that
 * matters is not the missing file — it is an APK that builds, installs, runs,
 * falls back to GPU and looks completely normal, with the reason three
 * directories away on a laptop nobody is looking at. CLAUDE.md #8 is about not
 * claiming an NPU we do not have; this is the build-time half of it.
 */
val checkNpuInputs by tasks.registering {
    description = "Verifies the §6.5 model and the Qualcomm v81 runtime are present in handoff/."
    val model = rootProject.layout.projectDirectory
        .file("handoff/npu/selfie_multiclass.tflite").asFile
    val runtime = rootProject.layout.projectDirectory.dir(
        "handoff/litert-samples/litert-samples-main/samples/litert/image_segmentation/" +
            "kotlin_npu/android_jit/litert_npu_runtime_libraries/qualcomm_runtime_v81/" +
            "src/main/jni/arm64-v8a",
    ).asFile
    // Deliberately declares no inputs or outputs, so it runs on every build.
    // It is two existence checks; making it skippable would mean it could be
    // skipped on the one build where something had moved.
    doLast {
        val problems = buildList {
            if (!model.isFile) add("missing model: $model")
            val skeleton = runtime.resolve("libQnnHtpV81Skel.so")
            if (!skeleton.isFile) add("missing Hexagon v81 skeleton: $skeleton")
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "P4 NPU inputs are not in place:\n  " + problems.joinToString("\n  ") +
                    "\n\nSee handoff/notes_for_claude.txt. Without these the app builds and runs, " +
                    "but the privacy masker can never reach the NPU — it would fall back to GPU " +
                    "with nothing on screen to say why.",
            )
        }
    }
}

// preBuild is the one task every variant runs first, so the syncs land before
// asset and jniLibs merging regardless of which variant is being assembled.
tasks.named("preBuild") {
    dependsOn(syncRehearsalAudio, syncBakeoffAudio, checkNpuInputs, syncNpuModel, syncNpuRuntime)
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
