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

The official Android SDK repository index currently lists the released `platforms;android-37.2` package. The Android 17 setup guide specifies API 37 and Build Tools 37, and AGP's [CompileSdkSpec](https://developer.android.com/reference/tools/gradle-api/9.5/com/android/build/api/dsl/CompileSdkSpec) supports selecting the minor API with `release(37) { minorApiLevel = 2 }`. Accordingly, the catalog stores `compileSdk = "37.2"`, each Android module uses the official minor-API DSL, and CI installs that exact SDK package rather than assuming `.0`. Sources: [official SDK package index](https://dl.google.com/android/repository/repository2-4.xml) and [Android 17 SDK setup](https://developer.android.com/about/versions/17/setup-sdk).

## Automated verification

`scripts/update_versions.py` downloads each mapped Maven `maven-metadata.xml` from Google Maven or Maven Central and Gradle's version index from `services.gradle.org`. It never fabricates a version. If any official index cannot be fetched or parsed, it exits without writing a partial catalog update. When it updates the `gradle` pin, it also synchronizes the required `distributionUrl` mirror in `gradle/wrapper/gradle-wrapper.properties`; CI reads the Android SDK/build-tools packages directly from the catalog.

- Default: stable and pre-release versions are eligible.
- `--stable-only`: exclude alpha, beta, RC, preview, canary, dev, snapshot, milestone, and EAP versions.
- `--check`: report whether an eligible newer version exists without editing the catalog.

Compile SDK, target SDK, minimum SDK, and Build Tools are not Maven artifacts; they are selected from installed Android SDK packages and are not altered by this script. Builds require the SDK platform named in the catalog and documented in the workflows.

## Publishing the refresh as a pull request

`.github/workflows/weekly-dependency-update.yml` opens or updates the pull request with the workflow's own `GITHUB_TOKEN` through `peter-evans/create-pull-request`. GitHub only allows that when the repository setting below is enabled:

**Settings → Actions → General → Workflow permissions → Allow GitHub Actions to create and approve pull requests**

With the setting disabled, GitHub answers the pull request call with `GitHub Actions is not permitted to create or approve pull requests`. The branch `automation/dependency-refresh` is still pushed, but no pull request exists, and earlier versions of the workflow failed the whole run at that point.

The workflow now reports that specific state instead of failing: after the publish step it compares the blob hashes of the pushed branch with the revision it just built, linted, and tested, and only when they match does it warn — with the `compare` link in the job summary — and finish successfully. Any other publishing problem (a missing branch, or a branch that does not carry the verified revision) still fails the job, so a green run always means the refreshed catalog is available for review.

The automation commits exactly two files, declared via `add-paths`: `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties`. Generated build outputs, in particular the Room schemas that Room exports to `core/data/schemas/` during the build, are never committed by this workflow.

## Calendar parser test fixture

`jsonTest = "20250517"` is the JVM-only `org.json:json` fixture for exercising the production Android JSON parser in unit tests. The production app still uses Android's built-in `org.json` (no new runtime JSON dependency). The pinned tag was checked on 2026-10-11: [upstream tag](https://github.com/stleary/JSON-java/tree/20250517). It is included in the existing Maven metadata updater.
