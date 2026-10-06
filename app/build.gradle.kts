import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.marble098.marbledo"
    compileSdk {
        val sdkParts = libs.versions.compileSdk.get().split('.')
        version = release(sdkParts[0].toInt()) {
            minorApiLevel = sdkParts.getOrElse(1) { "0" }.toInt()
        }
    }
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "com.marble098.marbledo"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = providers.environmentVariable("MARBLE_VERSION_CODE").orElse(libs.versions.appVersionCode.get()).get().toInt()
        versionName = providers.environmentVariable("MARBLE_VERSION_NAME").orElse(libs.versions.appVersionName.get()).get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    val signingFile = providers.environmentVariable("MARBLE_SIGNING_STORE_FILE").orNull
    val signingPassword = providers.environmentVariable("MARBLE_SIGNING_STORE_PASSWORD").orNull
    val signingAlias = providers.environmentVariable("MARBLE_SIGNING_KEY_ALIAS").orNull
    val signingKeyPassword = providers.environmentVariable("MARBLE_SIGNING_KEY_PASSWORD").orNull
    val releaseSigning = signingConfigs.maybeCreate("release")
    val canSignRelease = !signingFile.isNullOrBlank() && !signingPassword.isNullOrBlank() &&
        !signingAlias.isNullOrBlank() && !signingKeyPassword.isNullOrBlank()
    if (canSignRelease) {
        releaseSigning.storeFile = file(signingFile!!)
        releaseSigning.storePassword = signingPassword
        releaseSigning.keyAlias = signingAlias
        releaseSigning.keyPassword = signingKeyPassword
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (canSignRelease) signingConfig = releaseSigning
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/DEPENDENCIES")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    // Attach the generated profiles from the producer module to the app variants.
    baselineProfile(project(":baselineprofile"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:countdown"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.compose.window.size)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.work.runtime)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
