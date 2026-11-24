plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val fittyVersionCode = providers.gradleProperty("fittyVersionCode").map { value ->
    requireNotNull(value.toIntOrNull()?.takeIf { it > 0 }) {
        "fittyVersionCode must be a positive integer"
    }
}.getOrElse(3)

val fittyVersionName = providers.gradleProperty("fittyVersionName").map { value ->
    require(value.isNotBlank() && value == value.trim()) {
        "fittyVersionName must be non-blank and have no surrounding whitespace"
    }
    value
}.getOrElse("1.0")

android {
    namespace = "com.delaynore.fitty"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.delaynore.fitty"
        minSdk = 26
        targetSdk = 36
        versionCode = fittyVersionCode
        versionName = fittyVersionName
        testInstrumentationRunner = "com.delaynore.fitty.FittyTestRunner"
    }

    testOptions.unitTests.isIncludeAndroidResources = true

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

ksp {
    arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    ksp("androidx.room:room-compiler:2.8.3")

    testImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("org.json:json:20240303")

    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<Test>().configureEach {
    javaLauncher = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
}