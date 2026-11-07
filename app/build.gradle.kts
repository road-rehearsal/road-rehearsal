import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.roadpractice.coach"
    compileSdk = 36
    // CarPropertyManager(속도), CarUxRestrictionsManager
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.roadpractice.coach"
        // Pleos 배포 앱 요구사항: minSdk 28 이상
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // release 서명: 저장소에 없는 keystore.properties(와 release.jks)가 있을 때만. 없으면 서명 없이 빌드됩니다.
    val keyProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.isFile) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keyProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keyProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
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
    }
    // 실제 주행 기록 재생 시험(ReplayTest). 기록은 사용자 위치라 저장소 밖에 두고 -PreplayDir=로 넘깁니다.
    testOptions.unitTests.all {
        it.systemProperty("replay.dir", (project.findProperty("replayDir") ?: "").toString())
    }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // 화면: Jetpack Compose + Material 3, 창 크기에 따른 적응형 배치
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3.adaptive:adaptive:1.0.0")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    testImplementation("junit:junit:4.13.2")
    // 단위 시험에서 코스 JSON을 읽으려고(안드로이드 org.json은 JVM 시험에서 동작하지 않음)
    testImplementation("org.json:json:20240303")
}
