# CLAUDE.md

このファイルはClaude Codeがこのプロジェクトで作業する際の前提知識です。セッションが変わっても一貫した実装判断ができるよう、事前に確認しておいてください。

## プロジェクト概要

**RelayLab** は個人開発者YUSAが運営するAndroidアプリ群のブランド。屋号として活動中(札幌西税務署に開業届提出済み)。

**設計哲学**: 「人間が編集者、AIが執筆者」— アプリはその場でワンタップ記録するだけの軽量キャプチャツールで、意味づけ・分析・アドバイス機能は一切持たない。データはObsidian/Claude等の下流ツールに委ねる「引き算」アプローチが、機能過多な競合アプリに対する差別化軸。

**このリポジトリ (LogRelay)**: 位置情報・タイムスタンプをホーム画面ウィジェットからワンタップ記録するアプリ。パッケージ名 `com.logrelay.app`。RelayLab第1弾で、直接配布(GitHub Releases)で先行公開中。Google Play Console組織アカウントはD-U-N-S Number取得待ち。

## 技術スタック・ビルド環境

- **JDK**: Microsoft OpenJDK 21固定(JDK 25ではない)。理由: Kotlin 1.9.24を採用しており、Kotlin側がJava 25に対応したのはKotlin 2.3.0(2025年12月リリース)から。1.9.24→2.3系への移行はK2コンパイラ移行を伴い破壊的変更リスクがあるため、意図的に枯れた組み合わせ(JDK21 LTS + Kotlin 1.9.24)を維持している。JDK25への移行は将来のKotlinメジャーバージョンアップと合わせて計画的に行う方針。
- **compileSdk / targetSdk**: 36 (Android 16.0 "Baklava")
- **minSdk**: 26
- **Kotlin**: 1.9.24
- **Gradle**: 8.9 (Wrapper経由)
- **Android Gradle Plugin**: 8.7.2 (compileSdk=36に対して未検証の警告が出るが動作は問題なし。`android.suppressUnsupportedCompileSdk=36` で抑制可能、現状は未抑制のまま許容)
- **UI**: Jetpack Compose, Glance（ウィジェット）
- **DB**: Room (schema v4がリリースベースライン。`app/schemas/` はgit管理下に置き、マイグレーション履歴として保持する)
- **非同期処理**: WorkManager

## アーキテクチャ原則（RelayLab共通）

- **アプリ間のランタイム依存はゼロ**: LogRelay/PhotoRelay/ConditionRelay間で共有Room DBやContentProviderは使わない。タイムスタンプ表記ルール・day-boundary判定ロジックのみ共通Kotlinモジュールとして切り出し、各アプリがビルド時に取り込む。
- **データ統合はSAF(Storage Access Framework)経由**: 各relayアプリの保存先フォルダをユーザーが同じ場所に固定することで疑似的な統合を実現する。ContentProviderは使わない。
- **Intent/Deep Linkによるアプリ間遷移は片方のみインストールでも壊れないこと**: どちらのアプリの起動も、もう一方の存在に依存させない。
- **day-boundary設定は各アプリ共通のデザインパターン**: 日付の切り替わり時刻をユーザーが設定できるようにし、夜型ユーザーに配慮する。
- **オンボーディングは最小限**: フルオンボーディングは引き算の思想に反する。設定画面へのガイドリンク1本＋初回起動時のウィジェット未追加時のみ出す一度きりの案内、程度に留める。

## Git / リポジトリ運用

- `.gitignore` はワイルドカード(`**/build/`, `**/.idea/` など)で全階層をカバーすること。先頭スラッシュ(`/build`)はルート直下にしかマッチしないため、`app/build/` のようなサブディレクトリのビルド生成物が誤ってコミットされるリスクがある。
- 以下は絶対にコミットしない: `keystore.properties`(署名鍵情報), `local.properties`(環境依存パス), `*.jks` / `*.keystore`, `app/release/`(ビルド済みAPK/AABなどの成果物)
- ビルド済みAPKはGitHub Releasesの添付ファイルとして配布し、リポジトリ本体には含めない。
- `app/schemas/` (Room DBのスキーマ履歴)は例外的にgit管理下に置く(`!app/schemas/`)。
- コミット前は必ず `git status` で差分に含まれるファイルを確認し、鍵情報(`keystore.properties`, `*.jks`/`*.keystore`)・環境依存ファイル(`local.properties`)・ビルド生成物(`app/release/`, `**/build/`)が紛れ込んでいないか確認してからステージングする。`git add -A` / `git add .` は使わず、意図したファイルを個別に指定する。

## 配布戦略

- Google Play Console(組織アカウント)登録がD-U-N-S Number待ちのため、当面はGitHub Releases経由でAPKを直接配布。
- 告知の優先順位: X(Twitter) → note.com記事 → YouTube Shorts。
- ランディングページ(`logrelay-guide.html`)はGitHub Pagesでホスティングし、既存のプライバシーポリシーページ(`https://yusahiki.github.io/logrelay-privacy/`)と統合。

## 開発時の留意点

- 複雑な機能は明示的に後回しにし、小さく焦点を絞った実装を優先する。
- 変更は都度レビューしてから確定させる(自動でどんどん進めない)。
- Markdown/Obsidianが下流の主要な受け皿。LogRelayのクリップボード整形出力は、他のRelayアプリの出力と組み合わせて1つの日記的ナラティブになるよう設計する。
