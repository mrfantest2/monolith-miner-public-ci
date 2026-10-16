plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val inheritedDebugKeyStore = providers.environmentVariable("MONOLITH_DEBUG_KEYSTORE").orNull
val releaseStoreFile = providers.gradleProperty("releaseStoreFile").orNull
val releaseStorePassword = providers.gradleProperty("releaseStorePassword").orNull
val releaseKeyAlias = providers.gradleProperty("releaseKeyAlias").orNull
val releaseKeyPassword = providers.gradleProperty("releaseKeyPassword").orNull
val releaseSigningConfigured = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "com.fantest.monolithminer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fantest.monolithminer"
        minSdk = 24
        targetSdk = 36
        versionCode = 14
        versionName = "1.0.1"
    }

    signingConfigs {
        // Opt-in shared debug signing identity for data-preserving OP7 upgrades.
        // Keystore stays outside git and is supplied through the environment.
        if (!inheritedDebugKeyStore.isNullOrBlank()) {
            getByName("debug") {
                val debugFile = file(inheritedDebugKeyStore)
                require(debugFile.isFile) { "Configured Monolith Miner debug keystore does not exist" }
                storeFile = debugFile
            }
        }
        if (releaseSigningConfigured) {
            create("playUpload") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        // Side-by-side preview build: never replaces the Google Play-signed app.
        create("preview") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
        }
        release {
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("playUpload")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}





