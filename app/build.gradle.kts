
plugins {
    id("com.android.application")
    
}

android {
    namespace = "damjay.floating.projects"
    compileSdk = 35
    
    defaultConfig {
        applicationId = "damjay.floating.projects"
        minSdk = 19
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        
        multiDexEnabled = true
        
        vectorDrawables { 
            useSupportLibrary = true
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            isMinifyEnabled = true
        }
    }

}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.core:core:1.12.0")
    
    // MultiDex support
    implementation("androidx.multidex:multidex:2.0.1")
    
    // HTTP client for API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
    
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.7.0")
}
