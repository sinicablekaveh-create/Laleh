import java.util.Base64

plugins {
    id("com.android.application")
}

android {
    namespace = "com.sinicable.telegramelectric"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sinicable.telegramelectric"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "1.8.0"
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
}

dependencies {
    implementation("io.github.tdlib-android:core:0.1.1")
}


val generateAppLogo by tasks.registering {
    doLast {
        val encoded = (1..7).joinToString("") { index ->
            file("src/main/assets/logo_%02d.b64".format(index)).readText().trim()
        }
        val output = file("src/main/res/drawable/app_logo.jpg")
        output.parentFile.mkdirs()
        output.writeBytes(Base64.getDecoder().decode(encoded))
    }
}

tasks.named("preBuild") {
    dependsOn(generateAppLogo)
}
