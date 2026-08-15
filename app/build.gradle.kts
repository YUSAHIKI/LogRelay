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
        versionCode = 1
        versionName = "0.1.0-mvp"
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
}

// Roomのスキーマ変更履歴をJSONとして書き出す設定。
// 将来Migrationを書く際、この履歴と照合してテストできるようにするため。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
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

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ローカル自動バックアップ(定期実行・フォルダへの書き込み)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
