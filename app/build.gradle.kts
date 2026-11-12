import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val geminiApiKey: String = localProperties.getProperty("gemini.api.key", "")

android {
    namespace = "com.voiceassistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.voiceassistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Keep secret out of VCS — set gemini.api.key in local.properties
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")

        // ARM for devices; x86_64 for Android Studio emulators
        ndk {
            abiFilters.addAll(setOf("armeabi-v7a", "arm64-v8a", "x86_64"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    packaging {
        resources {
            excludes.addAll(setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/depends.txt"))
        }
        jniLibs {
            // Helps onnxruntime / ffmpeg-kit load correctly on x86_64 emulators
            useLegacyPackaging = true
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                // Jar is synced into ~/.m2 by syncRobolectricAndroidAll; also point dependency dir
                // so LocalDependencyResolver can resolve without hitting blocked Maven Central.
                val robolectricDepsDir = layout.buildDirectory.dir("robolectric-deps").get().asFile
                it.systemProperties["robolectric.offline"] = "true"
                it.systemProperties["robolectric.dependency.dir"] = robolectricDepsDir.absolutePath
                it.systemProperties["robolectric.dependency.repo.url"] =
                    "https://maven-central.storage-download.googleapis.com/maven2"
                it.systemProperties["robolectric.dependency.repo.id"] = "google-maven-central"
            }
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
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ONNX Runtime Mobile — Piper / Voice Conversion offline
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.0")

    // FFmpeg audio-only (not full — avoids +40MB video codecs)
    implementation("com.arthenica:ffmpeg-kit-audio:6.0-2")

    // ML Kit Text Recognition (OCR)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // ML Kit Translation (offline)
    implementation("com.google.mlkit:translate:17.0.3")
    // ML Kit Language Identification (auto-detect source)
    implementation("com.google.mlkit:language-id:17.0.6")
    // Gemini Android SDK (online AI translation)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // CameraX — OCR scanner
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // Audio routing / Cast discovery
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Media3 — background TTS playback + lock-screen notification
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("com.google.guava:guava:33.0.0-android")

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    // Pre-resolve Android SDK jars via Gradle repos (avoids Robolectric's direct Maven Central fetch)
    testImplementation("org.robolectric:android-all-instrumented:13-robolectric-9030017-i6")
    testImplementation("org.robolectric:android-all-instrumented:14-robolectric-10818077-i6")
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

kapt {
    correctErrorTypes = true
}

/**
 * Copy Robolectric android-all jars into build/robolectric-deps (flat) and ~/.m2
 * so offline / proxied environments can run Compose UI tests.
 */
tasks.register("syncRobolectricAndroidAll") {
    doLast {
        val config = configurations.getByName("debugUnitTestRuntimeClasspath")
        val jars = config.files.filter {
            it.name.startsWith("android-all-instrumented") && it.extension == "jar"
        }
        val flatDir = layout.buildDirectory.dir("robolectric-deps").get().asFile
        flatDir.mkdirs()
        jars.forEach { jar ->
            val flatName = jar.name
            val version = flatName
                .removePrefix("android-all-instrumented-")
                .removeSuffix(".jar")
            copy {
                from(jar)
                into(flatDir)
                rename { flatName }
            }
            val m2Dir = file(
                "${System.getProperty("user.home")}/.m2/repository/org/robolectric/android-all-instrumented/$version"
            )
            m2Dir.mkdirs()
            copy {
                from(jar)
                into(m2Dir)
                rename { flatName }
            }
            file("$m2Dir/android-all-instrumented-$version.pom").writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.robolectric</groupId>
                  <artifactId>android-all-instrumented</artifactId>
                  <version>$version</version>
                </project>
                """.trimIndent()
            )
        }
    }
}

tasks.withType<Test>().configureEach {
    dependsOn("syncRobolectricAndroidAll")
}
