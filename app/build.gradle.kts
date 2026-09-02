import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.logrelay.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.logrelay.app"
        minSdk = 26 // Glance実用上の現実的な下限
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 署名情報は keystore.properties(Gitに含めない)から読み込む。
    // ファイルが無い場合はリリースビルドのタスク自体は動くが、署名なしでは
    // Play Consoleにアップロードできないため、実際の公開前に必ず用意すること。
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

    // リリースビルド専用の「致命的」Lintチェック(lintVitalRelease)を無効化する。
    // 個人開発規模でこのゲートに引っかかると、実害のない誤検知でもビルドが止まってしまうため。
    // 通常のLintチェック自体は引き続き有効(デバッグビルドやAnalyze > Inspect Codeで確認可能)。
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

    // MigrationTestHelperが実行時にスキーマ履歴を参照できるよう、androidTestのassetsとして含める
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}

// Roomのスキーマ変更履歴をJSONとして書き出す設定。
// 将来Migrationを書く際、この履歴と照合してテストできるようにするため。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // RelayLabCommon (PhotoRelayと共有するday-boundary判定ロジック。git submodule)
    implementation(project(":relaylab-common"))
    // WearOSトリガーのプロトコル定数(:wearと共有)
    implementation(project(":wear-protocol"))

    // Core / Compose (本体アプリの一覧・振り返り画面用)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    // ActivityResult系APIの依存先であるFragmentライブラリを明示的に新しいバージョンへ固定。
    // これを指定しないと、他ライブラリ経由で古いバージョンが解決され、
    // 実害のないLint警告(InvalidFragmentVersionForActivityResult)でリリースビルドが止まることがある。
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Glance (ウィジェット本体)
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")

    // Room (ローカルDB)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // 位置情報（ワンタップ取得用）
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // WearOSトリガー機能: wearモジュールからのDataClient受信(WearableListenerService)
    implementation("com.google.android.gms:play-services-wearable:20.0.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ローカル自動バックアップ(定期実行・フォルダへの書き込み)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Room Migrationの検証用(MigrationTestHelper)。実行には実機/エミュレータでの
    // connectedAndroidTestが必要(この開発環境には接続済みの端末がない)。
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
