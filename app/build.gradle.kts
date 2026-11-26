import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseCredentials = providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
    .asText.orNull?.let { contents -> Properties().apply { contents.reader().use(::load) } }

/** Build number = commits on the checked-out branch, so CI prereleases and manual releases share one rising
 * counter the in-app updater can compare. CI passes it explicitly; local builds ask git, 21 if git is missing. */
val ionVersionCode: Int = providers.gradleProperty("ionVersionCode").orNull?.toIntOrNull()
    ?: runCatching {
        providers.exec { commandLine("git", "rev-list", "--count", "HEAD"); isIgnoreExitValue = true }
            .standardOutput.asText.get().trim().toInt()
    }.getOrNull()?.takeIf { it > 21 } ?: 21

/**
 * Robolectric's own downloader fetches its Android framework jar from Maven Central on every CI run, without
 * retries, and Central rate-limits GitHub runners (HTTP 429 failed every Robolectric test). Gradle resolves the
 * same jar instead, with its retries and dependency cache, and the tests run Robolectric in offline mode.
 * Keep the version in step with the Robolectric release when it is upgraded (SDK 35 is the only one used).
 */
val robolectricRuntime: Configuration by configurations.creating { isTransitive = false }
val robolectricJarsDir = layout.buildDirectory.dir("robolectric-jars")
val robolectricJars = tasks.register<Copy>("robolectricJars") {
    from(robolectricRuntime)
    into(robolectricJarsDir)
}

android {
    namespace = "com.freecyber.ion"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.freecyber.ion"
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = ionVersionCode
        versionName = "1.1"
    }
    signingConfigs {
        if (releaseCredentials != null) {
            create("ionRelease") {
                storeFile = rootProject.file(requireNotNull(releaseCredentials.getProperty("storeFile")) {
                    "keystore.properties must contain storeFile"
                })
                storePassword = requireNotNull(releaseCredentials.getProperty("storePassword")) {
                    "keystore.properties must contain storePassword"
                }
                keyAlias = "ion-release"
                keyPassword = storePassword
                storeType = "PKCS12"
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            // Checkouts without credentials can build an unsigned APK, including pull requests.
            signingConfig = signingConfigs.findByName("ionRelease")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric screenshot tests render real resources; images land in app/build/outputs/roborazzi.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("roborazzi.test.record", "true")
            // Robolectric runs offline from the jar Gradle resolved (see robolectricJars below).
            it.dependsOn(robolectricJars)
            it.systemProperty("robolectric.offline", "true")
            it.systemProperty("robolectric.dependency.dir", robolectricJarsDir.get().asFile.absolutePath)
            it.maxHeapSize = "1g"
            if (providers.gradleProperty("skipScreenshots").isPresent) {
                it.filter { excludeTestsMatching("com.freecyber.ion.ScreenshotTest") }
            }
            // README images are written into docs/readme; they are only rendered on request (-PreadmeShots).
            if (!providers.gradleProperty("readmeShots").isPresent) it.filter { excludeTestsMatching("com.freecyber.ion.ReadmeShots*") }
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    robolectricRuntime("org.robolectric:android-all-instrumented:15-robolectric-13954326-i7")
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-beta01")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    // Material You: tonal schemes from a seed colour when the wallpaper palette is unavailable or turned off.
    implementation("com.materialkolor:material-kolor:5.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.glance:glance-appwidget-testing:1.1.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.77.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.77.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
