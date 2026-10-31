import com.android.build.api.variant.BuildConfigField

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "io.github.bardesss.wick"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.bardesss.wick"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // The fake server mode is in every build type except release, so R8 removes it there.
    // The baseline profile plugin adds benchmarkRelease and nonMinifiedRelease, which get it too.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
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

// The fake server mode is in every build type except release, so R8 removes it there.
// The baseline profile plugin adds benchmarkRelease and nonMinifiedRelease, which get it too.
androidComponents {
    onVariants { variant ->
        val enabled = variant.buildType != "release"
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
