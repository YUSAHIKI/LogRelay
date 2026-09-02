import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.logrelay.app.wear"
    compileSdk = 36

    defaultConfig {
        // フォン版(:app)と必ず同一のapplicationIdにすること。
        // Wear OS Data Layer(DataClient)は、フォン側とウォッチ側で同じapplicationIdの
        // アプリ同士でないとDataItemを配送しない(PoCで実際に確認済み)。
        applicationId = "com.logrelay.app"
        minSdk = 30 // Wear OS 3以降(Galaxy Watch 6等の現行機種はこれ以上)
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-mvp"
    }

    // フォン版(:app)と同一署名にする必要があるため、同じkeystore.propertiesを参照する。
    // デバッグビルドは端末共通のdebug.keystoreを使うため、この対応がなくても:appと自動的に一致する。
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    val hasKeystoreProperties = keystorePropertiesFile.exists()
    if (hasKeystoreProperties) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        if (hasKeystoreProperties) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasKeystoreProperties) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    // WearOSトリガーが使う共通定数(DataItemパス・DataMapキー・sourceの値)。
    // フォン側(:app)のRecordRepository.captureFromWatchと定義を揃えるため同じソースを参照する。
    implementation(project(":wear-protocol"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Wear OS向けMaterialコンポーネント(丸型画面向けレイアウト)
    implementation("androidx.wear.compose:compose-material:1.4.1")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")

    // Wear OS Data Layer(DataClient)。フォンへのトリガー送信に使う
    implementation("com.google.android.gms:play-services-wearable:20.0.1")

    // Wear Tile(ホーム画面のタイルからワンタップで記録を送れるようにする)。
    // 1.6.2はKotlin 2.1でコンパイルされておりプロジェクトのKotlin 1.9.24と非互換だったため、
    // Kotlin 1.9系と互換性のある1.4.1に固定している。
    implementation("androidx.wear.tiles:tiles:1.4.1")
    // TileService.onTileRequest等が返すListenableFuture(Guava)用。静的な内容しか返さないため
    // Futures.immediateFuture()で即時完了させる(非同期処理自体は使わない)
    implementation("com.google.guava:guava:33.7.1-android")

    // 位置情報(ウォッチ内蔵GPS)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Task<T>(DataClient/FusedLocationProviderClient)をsuspend関数から扱うための.await()拡張
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
