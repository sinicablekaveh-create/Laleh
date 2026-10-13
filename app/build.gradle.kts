plugins {
    id("com.android.application")
}

android {
    namespace = "com.hooshikadev.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hooshikadev.app"
        minSdk = 26
        targetSdk = 35

        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.setSrcDirs(listOf("src/hooshikadev/java"))
            manifest.srcFile("src/hooshikadev/AndroidManifest.xml")
            res.setSrcDirs(listOf("src/hooshikadev/res"))
            assets.setSrcDirs(emptyList<String>())
        }
        getByName("test") {
            java.setSrcDirs(listOf("src/hooshikadevTest/java"))
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
