plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.approbot"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.approbot"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    packaging {
        resources.excludes += "META-INF/INDEX.LIST"
        resources.excludes += "META-INF/io.netty.versions.properties"
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Room (base de datos local)
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // ViewModel + LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")

    // MQTT (HiveMQ client, funciona sin broker externo en red local)
    implementation("com.hivemq:hivemq-mqtt-client:1.3.3")

    // Gson (serialización de mensajes MQTT)
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
