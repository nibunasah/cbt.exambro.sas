plugins {
    alias(libs.plugins.android.application)
}

android {
    signingConfigs {
        getByName("debug") {
            storeFile = file("C:\\Users\\Lenovo\\AndroidStudioProjects\\cbt.exambro.sas\\cbt-sas.keystore")
            keyAlias = "sas_alias"
            keyPassword = "Chombad85!"
            storePassword = "Chombad85!"
        }
        create("Release Build") {
            storeFile = file("C:\\Users\\Lenovo\\AndroidStudioProjects\\cbt.exambro.sas\\cbt-sas.keystore")
            keyAlias = "sas_alias"
            keyPassword = "Chombad85!"
            storePassword = "Chombad85!"
        }
    }
    namespace = "cbt.exambro.sas"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "cbt.exambro.sas"
        minSdk = 24
        targetSdk = 36
        versionCode = 206
        versionName = "3.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        signingConfig = signingConfigs.getByName("Release Build")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("Release Build")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.swiperefreshlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}