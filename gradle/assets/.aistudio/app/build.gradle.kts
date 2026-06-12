plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.msi.mediaplayer"
    minSdk = 24
    targetSdk = 36
    versionCode = 8
    versionName = "5.4"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Ensure Sirajul - msi-dev custom secure signing key is dynamically baked if absent
  val sirajulKeystoreFile = file("${rootDir}/sirajul.jks")

  signingConfigs {
    create("sirajulConfig") {
      storeFile = sirajulKeystoreFile
      storePassword = "sirajulpassword"
      keyAlias = "Sirajul"
      keyPassword = "sirajulpassword"
      enableV1Signing = true
      enableV2Signing = true
      enableV3Signing = true
      enableV4Signing = true
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("sirajulConfig")
    }
    debug {
      signingConfig = signingConfigs.getByName("sirajulConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.common)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

val generateSirajulKeystore = tasks.register("generateSirajulKeystore") {
  val keystoreFile = file("${rootDir}/sirajul.jks")
  outputs.file(keystoreFile)
  doLast {
    if (!keystoreFile.exists()) {
      println("Generative build: creating secure sign keystore...")
      try {
        ProcessBuilder(
          "keytool", "-genkeypair", "-v",
          "-keystore", keystoreFile.absolutePath,
          "-keyalg", "RSA",
          "-keysize", "2048",
          "-validity", "10000",
          "-alias", "Sirajul",
          "-dname", "CN=msi.dev, OU=1, O=msi-dev, L=KAPTAI, S=Chattogram, C=BD",
          "-storepass", "sirajulpassword",
          "-keypass", "sirajulpassword"
        ).inheritIO().start().waitFor()
      } catch (e: Exception) {
        throw GradleException("Failed to generate Sirajul custom signature key: ${e.message}")
      }
    }
  }
}

// Hook to run before compile pre-built checks start
tasks.matching {
  it.name.startsWith("preBuild") ||
  it.name.startsWith("sign") ||
  it.name.startsWith("package")
}.configureEach {
  dependsOn(generateSirajulKeystore)
}

tasks.register("copyApksToRoot") {
  dependsOn("assembleDebug", "assembleRelease")
  doLast {
    val apkDir = layout.buildDirectory.dir("outputs/apk").get().asFile
    val destDir = rootDir
    if (apkDir.exists()) {
      apkDir.walk().filter { it.extension == "apk" }.forEach { file ->
        val targetFile = File(destDir, file.name)
        file.copyTo(targetFile, overwrite = true)
        println("Successfully copied signed APK to ${targetFile.absolutePath}")
      }
    }
  }
}

