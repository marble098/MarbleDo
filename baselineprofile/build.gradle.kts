plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.marble098.marbledo.baselineprofile"
    compileSdk {
        val sdkParts = libs.versions.compileSdk.get().split('.')
        version = release(sdkParts[0].toInt()) {
            minorApiLevel = sdkParts.getOrElse(1) { "0" }.toInt()
        }
    }
    buildToolsVersion = libs.versions.buildTools.get()
    targetProjectPath = ":app"
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

baselineProfile {
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.runner)
}
