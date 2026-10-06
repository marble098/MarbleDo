import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "com.marble098.marbledo.core.data"
    compileSdk {
        val sdkParts = libs.versions.compileSdk.get().split('.')
        version = release(sdkParts[0].toInt()) {
            minorApiLevel = sdkParts.getOrElse(1) { "0" }.toInt()
        }
    }
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                // Android projects have no default Protobuf output; register Java-lite explicitly.
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    // MarbleDatabase extends RoomDatabase and is part of this module's public API, so
    // consumers need Room on their compile classpath to resolve inherited members.
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.javalite)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.androidx.room.ktx)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
