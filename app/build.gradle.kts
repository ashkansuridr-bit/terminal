plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ksPath = providers.environmentVariable("TERMINAL_KEYSTORE_PATH").orNull
val ksPass = providers.environmentVariable("TERMINAL_KEYSTORE_PASSWORD").orNull
val ksAlias = providers.environmentVariable("TERMINAL_KEY_ALIAS").orNull
val ksKeyPass = providers.environmentVariable("TERMINAL_KEY_PASSWORD").orNull
val signingReady = listOf(ksPath, ksPass, ksAlias, ksKeyPass).all { !it.isNullOrBlank() }
val googleWebClientId = providers.environmentVariable("GOOGLE_WEB_CLIENT_ID").orNull ?: ""

android {
    namespace = "app.terminalssh.secure"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.terminalssh.secure"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.5.1"
        resourceConfigurations += listOf("fa", "en")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Iranian markets ship to devices that often have no Google Play Services at all,
    // so the optional account integration is a build-time choice, not a runtime one.
    flavorDimensions += "distribution"
    productFlavors {
        // Cafe Bazaar / Myket: no Play Services dependency compiled in.
        create("market") {
            dimension = "distribution"
            isDefault = true
        }
        // Google Play: optional Google sign-in, client id injected at build time.
        create("gplay") {
            dimension = "distribution"
            resValue("string", "google_web_client_id", googleWebClientId)
        }
    }

    signingConfigs {
        if (signingReady) {
            create("marketRelease") {
                storeFile = file(ksPath!!)
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = ksKeyPass
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        /**
         * A shareable test build: minified like release, but under its own application id
         * and signed with the debug key.
         *
         * The distinct id is the point. A build signed with a throwaway key but carrying
         * the production id can never be upgraded to the real signed release — Android
         * refuses a signature change — and uninstalling to fix that destroys the
         * AndroidKeyStore vault, taking every saved password and private key with it.
         * This variant installs alongside the real app instead.
         */
        create("preview") {
            initWith(getByName("release"))
            // initWith does not carry the shrinker settings, and without them this build
            // ships the whole material-icons library: 18 MB instead of 6 MB.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingReady) signingConfig = signingConfigs.getByName("marketRelease")
        }
    }

    // Iranian markets accept APK only. Per-ABI splits cut ~28MB down to ~9MB each.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // buildConfig is off by default in AGP 8; the About screen reads BuildConfig.VERSION_NAME.
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

// Give every split a distinct versionCode so markets accept them side by side.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.find { it.filterType.name == "ABI" }?.identifier
            val offset = when (abi) {
                "armeabi-v7a" -> 1
                "arm64-v8a" -> 2
                "x86_64" -> 3
                else -> 0
            }
            output.versionCode.set((android.defaultConfig.versionCode ?: 1) * 10 + offset)
        }
    }
}

dependencies {
    implementation("com.github.mwiede:jsch:2.28.6")
    implementation("org.connectbot:termlib:0.1.0")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.biometric:biometric:1.1.0")

    // Google-only: absent from the market APK entirely.
    "gplayImplementation"("androidx.credentials:credentials:1.6.0")
    "gplayImplementation"("androidx.credentials:credentials-play-services-auth:1.6.0")
    "gplayImplementation"("com.google.android.libraries.identity.googleid:googleid:1.2.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation(kotlin("test"))

    // Instrumentation: the vault's real constraints live in the AndroidKeyStore provider,
    // which no JVM test can reach.
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
