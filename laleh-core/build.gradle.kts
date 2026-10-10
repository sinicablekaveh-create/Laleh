plugins {
    id("com.android.library")
}

android {
    namespace = "com.sinicable.laleht.offline"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
