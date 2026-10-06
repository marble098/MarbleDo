# Dependency version sources

Last reviewed: **2026-10-06**. All dependency numbers are centralized in `gradle/libs.versions.toml`. The catalog intentionally includes pre-releases where that was selected; Renovate is configured with `ignoreUnstable: false`.

## Official release indexes

| Catalog group | Official source |
|---|---|
| Gradle Wrapper | [Gradle official version index](https://services.gradle.org/versions/all) and [Gradle 9.9.0-milestone-2 release notes](https://docs.gradle.org/9.9.0-milestone-2/release-notes.html) |
| Android Gradle Plugin | [Google Maven metadata](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml) and [Android Gradle Plugin release notes](https://developer.android.com/build/releases/gradle-plugin) |
| Kotlin / serialization compiler plugin | [Kotlin releases](https://github.com/JetBrains/kotlin/releases), [Maven Central metadata](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml), and the [official KGP/Gradle/AGP compatibility matrix](https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin) |
| KSP | [KSP releases](https://github.com/google/ksp/releases) and [Maven Central metadata](https://repo.maven.apache.org/maven2/com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml) |
| Compose BOM and Material 3 | [Compose BOM releases](https://developer.android.com/jetpack/compose/bom/bom-mapping) and [Material 3 releases](https://developer.android.com/jetpack/androidx/releases/compose-material3) |
| Navigation 3 | [AndroidX Navigation 3 release notes](https://developer.android.com/jetpack/androidx/releases/navigation3) |
| AndroidX components (Activity, Lifecycle, Core, AppCompat, SplashScreen, Room, DataStore, WorkManager, Glance, Benchmark) | The corresponding [AndroidX release pages](https://developer.android.com/jetpack/androidx/releases) and official Google Maven metadata under `https://dl.google.com/dl/android/maven2/` |
| Koin, Coroutines, Kotlin Serialization, Kotlinx DateTime, JUnit, Turbine, Protobuf | Their upstream release pages or Maven Central metadata under `https://repo.maven.apache.org/maven2/` |
| Compile / target SDK and Build Tools | [Android SDK Platform release notes](https://developer.android.com/tools/releases/platforms) and the official `sdkmanager` package index |

Navigation 3 was selected intentionally instead of Navigation 2. Its official AndroidX release page lists `1.3.0-alpha01` as the latest 1.3 pre-release as of this review. The official Gradle version index lists `9.9.0-milestone-2` as the newest non-snapshot release, and Google Maven metadata lists `9.5.0-alpha08` for AGP. These pre-release toolchain pins are intentional and have not been build-validated in this environment. Kotlin's official matrix lists KGP 2.4.20 as fully supported through Gradle 9.7.0 and AGP 9.3.1; the newer selected Gradle/AGP versions are outside that tested range and may produce warnings or incompatibilities. Android modules use AGP 9's built-in Kotlin support rather than applying the incompatible `org.jetbrains.kotlin.android` plugin; see Google's [migration guide](https://developer.android.com/build/migrate-to-built-in-kotlin).

## Automated verification

`scripts/update_versions.py` downloads each mapped Maven `maven-metadata.xml` from Google Maven or Maven Central and Gradle's version index from `services.gradle.org`. It never fabricates a version. If any official index cannot be fetched or parsed, it exits without writing a partial catalog update. When it updates the `gradle` pin, it also synchronizes the required `distributionUrl` mirror in `gradle/wrapper/gradle-wrapper.properties`; CI reads the Android SDK/build-tools packages directly from the catalog.

- Default: stable and pre-release versions are eligible.
- `--stable-only`: exclude alpha, beta, RC, preview, canary, dev, snapshot, milestone, and EAP versions.
- `--check`: report whether an eligible newer version exists without editing the catalog.

Compile SDK, target SDK, minimum SDK, and Build Tools are not Maven artifacts; they are selected from installed Android SDK packages and are not altered by this script. Builds require the SDK platform named in the catalog and documented in the workflows.
