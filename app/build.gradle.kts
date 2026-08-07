plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// compileSdk/minSdk, Java level, and lint policy come from the root build's androidDefaults.
android {
    namespace = "com.dewijones92.primavista"
    defaultConfig {
        applicationId = "com.dewijones92.primavista"
        targetSdk = libs.versions.targetSdk.get().toInt()
        // CI passes monotonically increasing values (-PversionCode / -PversionName)
        // so Obtainium sees every main-tip build as an upgrade.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.1.0-dev"
        // Short git SHA of the build, shown in-app and in every diagnostics report so a
        // report can always be tied to the exact code that produced it.
        //
        // providers.exec, not ProcessBuilder: launching a process at configuration time is
        // incompatible with the configuration cache, and this build keeps the cache on.
        val gitSha = (project.findProperty("gitSha") as String?)
            ?: providers.exec {
                commandLine("git", "rev-parse", "--short", "HEAD")
                workingDir = rootDir
                isIgnoreExitValue = true
            }.standardOutput.asText.map { it.trim() }.orNull.takeUnless { it.isNullOrBlank() }
            ?: "local"
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Real key in CI (from secrets); local release builds fall back to the
        // debug key so they remain installable without the keystore.
        val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        aidl = false
        buildConfig = true
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:audio"))
    implementation(project(":core:database"))
    implementation(project(":core:notation"))
    implementation(project(":core:practice"))
    implementation(project(":core:score"))
    implementation(project(":lib:common"))
    implementation(project(":lib:pitch"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
