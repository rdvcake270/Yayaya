plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
compileOptions {
    sourceCompatibility JavaVersion.VERSION_17
    targetCompatibility JavaVersion.VERSION_17
}

kotlinOptions {
    jvmTarget = '17'
}
 namespace = "org.example.espport"
 compileSdk = 35
 defaultConfig { applicationId = "org.example.espport"; minSdk = 26; targetSdk = 28; versionCode = 1; versionName = "0.1"; ndk { abiFilters += "arm64-v8a" } }
 externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
 buildTypes { getByName("release") { isMinifyEnabled = false } }
}
