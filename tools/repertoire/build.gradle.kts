// An author-side tool, not part of the app: :app does not depend on it, so nothing here ships.
// It deliberately uses the app's OWN parser, grader and curriculum rather than reimplementing
// them, so "this piece imports cleanly" means the same thing here as it will on the phone.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:score"))
    implementation(project(":core:practice"))

    testImplementation(libs.junit)
}

application {
    mainClass.set("com.dewijones92.primavista.tools.repertoire.MainKt")
}
