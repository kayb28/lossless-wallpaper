import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Firma: solo desde variables de entorno que aporta el CI (secrets del repo).
// Nunca se comitean claves: los archivos *.jks están en .gitignore.
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
val keyAliasEnv: String? = System.getenv("KEY_ALIAS")
val keyPasswordEnv: String? = System.getenv("KEY_PASSWORD")
val hasReleaseSigning = !keystoreFile.isNullOrEmpty() &&
    File(keystoreFile).exists() &&
    !keystorePassword.isNullOrEmpty()

android {
    namespace = "com.kayb28.losslesswallpaper"
    // compileSdk 37.2: exigido por material3 1.5.0-beta02 del Compose BOM 2026.10.01
    // (mismo combo de versiones que usa Mihon en su CI).
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.kayb28.losslesswallpaper"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = File(keystoreFile!!)
                storePassword = keystorePassword
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv ?: keystorePassword
            }
        }
    }

    buildTypes {
        release {
            // MVP sin ofuscación: menos superficie de fallos, APK directo y predecible.
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
