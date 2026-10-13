plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Defina em ~/.gradle/gradle.properties (não commitado):
//   meutunelApiUrl=https://api.seudominio.com
//   meutunelApiToken=...
val apiUrl = (project.findProperty("meutunelApiUrl") as String?).orEmpty()
val apiToken = (project.findProperty("meutunelApiToken") as String?).orEmpty()

android {
    namespace = "app.meutunel"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.meutunel"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.2.0"
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
        buildConfigField("String", "API_TOKEN", "\"$apiToken\"")
    }
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.wireguard.android:tunnel:1.0.20230706")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
