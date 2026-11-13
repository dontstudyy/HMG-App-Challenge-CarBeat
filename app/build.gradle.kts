plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dontstudyy.carbeat"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.dontstudyy.carbeat"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // AAOS Car API (CarPropertyManager 등) 컴파일용
    useLibrary("android.car")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Pleos SDK (probe에서 검증된 버전)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // 곡 재생 (판정 기준 시각 = 재생 위치)
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    testImplementation(libs.junit)
    // JVM 단위 테스트에서는 android.jar의 org.json이 비어 있어 실제 구현을 쓴다
    testImplementation("org.json:json:20240303")
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
