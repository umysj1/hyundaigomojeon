import java.util.Properties

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "kr.gildongmu.bingo"
    compileSdk = 35
    defaultConfig {
        applicationId = "kr.gildongmu.bingo"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // 한국관광공사 TourAPI 서비스키(공공데이터포털). local.properties에 tourapi.key=... 로 넣는다. 저장소에는 올리지 않는다.
        // 제출·배포용 APK는 키 없이: gradle assembleDebug -PnoTourKey (전주 시연 카드는 assets/study에 들어 있어 그대로 나온다)
        val tourKey = if (project.hasProperty("noTourKey")) "" else localProps.getProperty("tourapi.key", "")
        buildConfigField("String", "TOUR_KEY", "\"$tourKey\"")
    }
    buildTypes {
        // 공모전 제출용 release: 서명은 디버그 키(설치·실행 확인용), 난독화 없음
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("com.google.zxing:core:3.5.3")
}
