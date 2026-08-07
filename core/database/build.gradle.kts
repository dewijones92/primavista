plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

val schemaDirectory = layout.projectDirectory.dir("schemas").asFile.path

android {
    namespace = "com.dewijones92.primavista.database"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

// Schemas are committed, not generated-and-forgotten: a schema change has to show up as a diff
// in review, because the migration that carries Dewi's practice history forward is written
// against it (docs/spec.md I4).
ksp {
    arg("room.schemaLocation", schemaDirectory)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    // The only place Room entities meet domain types.
    api(project(":core:practice"))
    // api: PrimaVistaDatabase extends RoomDatabase, so Room is part of this module's ABI.
    api(libs.room.runtime)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
