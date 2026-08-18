plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.tofairy.child"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.tofairy.child"
        // 7~9세는 물려받은(보급형/저사양) 폰 비율이 높음 → minSdk를 보수적으로 잡는다.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // A축 Shieldstral runtime과 향후 ML Router는 서로 독립된 feature flag로 검증한다.
        // 골격 빌드는 어떤 모델 artifact 없이도 돌아야 한다 → 둘 다 기본값 false.
        buildConfigField("boolean", "AXIS_A_SCREENING_ENABLED", "false")
        buildConfigField("boolean", "ML_ROUTER_ENABLED", "false")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // 관계/기억/집계 상태의 현 골격 저장소. production 키 수명주기와 저장 primitive는
    // Android Keystore/secure storage 기준으로 docs/50 §4 검증 뒤 확정한다.
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
