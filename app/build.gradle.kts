import com.android.build.api.variant.BuildConfigField

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.baselineprofile)
}

val appVersionName = providers.gradleProperty("versionName").getOrElse("0.1.0")
val versionParts = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(appVersionName)
    ?.groupValues?.drop(1)?.map { it.toInt() }
    ?: throw GradleException("versionName must look like X.Y.Z, got: $appVersionName")
if (versionParts[1] > 99 || versionParts[2] > 99) {
    throw GradleException("minor and patch must be 99 or less, got: $appVersionName")
}
val appVersionCode = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]

// CI decodes the release key to a file. Without it the release APK is left unsigned.
val keystorePath = providers.environmentVariable("WICK_KEYSTORE_PATH").orNull

android {
    namespace = "io.github.bardesss.wick"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.bardesss.wick"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("WICK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WICK_KEY_ALIAS")
                keyPassword = System.getenv("WICK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric 4.17 reaches into FileDescriptor when it sets up the app on JDK 21;
            // without this every Robolectric test fails with "Failed to interact with raw FileDescriptor internals"
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// The fake server mode is in every build type except release, so R8 removes it there.
// The baseline profile plugin adds benchmarkRelease and nonMinifiedRelease, which get it too.
androidComponents {
    onVariants { variant ->
        val enabled = variant.buildType != "release"
        // side by side with the real app, which fake mode must never touch
        if (variant.buildType == "benchmarkRelease" || variant.buildType == "nonMinifiedRelease") {
            variant.applicationId.set("io.github.bardesss.wick.bench")
        }
        variant.buildConfigFields?.put("FAKE_ENABLED", BuildConfigField("boolean", enabled.toString(), null))
    }
}

dependencies {
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.browser)
    implementation(libs.jsoup)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
