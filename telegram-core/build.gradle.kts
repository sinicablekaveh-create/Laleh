plugins {
    id("com.android.library")
}

android {
    namespace = "com.sinicable.laleht.telegram"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api("io.github.tdlib-android:core:0.1.1")
}
