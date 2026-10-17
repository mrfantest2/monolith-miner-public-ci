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

// AdMob identifiers are app-specific. No Gnome Score or other game's IDs.
val prodApp = providers.gradleProperty("monolithAdmobAppId").orElse(
    providers.environmentVariable("MONOLITH_ADMOB_APP_ID")).orNull.orEmpty()
val prodBanner = providers.gradleProperty("monolithAdmobBannerId").orElse(
    providers.environmentVariable("MONOLITH_ADMOB_BANNER_ID")).orNull.orEmpty()
val prodReward = providers.gradleProperty("monolithAdmobRewardedId").orElse(
    providers.environmentVariable("MONOLITH_ADMOB_REWARDED_ID")).orNull.orEmpty()
val prodIds = listOf(prodApp, prodBanner, prodReward)
require(prodIds.all { it.isEmpty() } || prodIds.all { it.isNotEmpty() }) {
    "Provide all three Monolith AdMob IDs or none."
}
val prodEnabled = prodApp.matches(Regex("ca-app-pub-[0-9]{16}~[0-9]{10}")) &&
    prodBanner.matches(Regex("ca-app-pub-[0-9]{16}/[0-9]{10}")) &&
    prodReward.matches(Regex("ca-app-pub-[0-9]{16}/[0-9]{10}")) &&
    !prodApp.startsWith("ca-app-pub-3940256099942544")
require(prodIds.all { it.isEmpty() } || prodEnabled) {
    "Monolith AdMob IDs must be valid non-test IDs."
}
val testApp = "ca-app-pub-3940256099942544~3347511713"
val testBanner = "ca-app-pub-3940256099942544/9214589741"
val testReward = "ca-app-pub-3940256099942544/5224354917"

android {
    namespace = "com.fantest.monolithminer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fantest.monolithminer"
        minSdk = 24
        targetSdk = 36
        versionCode = 15
        versionName = "1.0.2"
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
        debug {
            manifestPlaceholders["admobAppId"] = testApp
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$testBanner\"")
            buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"$testReward\"")
        }
        // Side-by-side preview build: never replaces the Google Play-signed app.
        create("preview") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
        }
        release {
            // No live ads are requested until all Monolith-specific IDs are supplied.
            manifestPlaceholders["admobAppId"] = if (prodEnabled) prodApp else testApp
            buildConfigField("boolean", "ADS_ENABLED", prodEnabled.toString())
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID",
                "\"${if (prodEnabled) prodBanner else testBanner}\"")
            buildConfigField("String", "ADMOB_REWARDED_UNIT_ID",
                "\"${if (prodEnabled) prodReward else testReward}\"")
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
    implementation("com.google.android.gms:play-services-ads:24.8.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
