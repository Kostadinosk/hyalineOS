plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "os.hyaline.core.privilege"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    api(libs.shizuku.api)
    api(libs.shizuku.provider)
    api(libs.libsu.core)
}
