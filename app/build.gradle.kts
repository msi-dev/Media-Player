plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
}

val keystoreFile = rootProject.file("sirajul.jks")

android {
    namespace = "com.example"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aistudio.mediaplayer"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "5.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("sirajulConfig") {
            storeFile = rootProject.file("sirajul.jks")
            storePassword = "sirajul"
            keyAlias = "Sirajul"
            keyPassword = "sirajul"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("sirajulConfig")
        }
        debug {
            signingConfig = signingConfigs.getByName("sirajulConfig")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android KTX & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Jetpack Room local storage
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Media3 (ExoPlayer & MediaSession)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // Image loading
    implementation(libs.coil.compose)

    // Local Testing dependencies
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.register("generateCustomKeystore") {
    val file = rootProject.file("sirajul.jks")
    outputs.file(file)
    doLast {
        if (!file.exists()) {
            println("Generating custom keystore...")
            val process = ProcessBuilder(
                "keytool", "-genkeypair", "-v",
                "-keystore", file.absolutePath,
                "-alias", "Sirajul",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "10000",
                "-dname", "CN=msi.dev, OU=1, O=msi-dev, L=KAPTAI, S=Chattogram, C=BD",
                "-storepass", "sirajul",
                "-keypass", "sirajul"
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                error("Failed to generate keystore ($exitCode): $output")
            } else {
                println("Custom keystore generated successfully: $output")
            }
        }
    }
}

tasks.configureEach {
    if (name.startsWith("package") || name.startsWith("validateSigning")) {
        dependsOn("generateCustomKeystore")
    }
}


