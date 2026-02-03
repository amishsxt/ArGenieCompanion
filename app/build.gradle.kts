plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.argeniecompanion"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.argeniecompanion"
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appAuthRedirectScheme"] = "argenieauth"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        viewBinding {
            enable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
    kotlinOptions {
        jvmTarget = "11"
    }


}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.localbroadcastmanager)
    implementation(libs.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(libs.android.async.http)
    implementation(libs.okhttp)

    //open-id for keycloak
    implementation(libs.appauth)

    //livekit sdk
    implementation(libs.livekit.android)

    //mqtt
    implementation(libs.hivemq.mqtt.client)
    implementation(libs.netty.handler)
    implementation(libs.netty.codec.http2)

}