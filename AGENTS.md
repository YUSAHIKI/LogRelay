# AGENTS.md

このファイルはCodexがこのプロジェクトで作業する際の前提知識です。セッションが変わっても一貫した実装判断ができるよう、事前に確認しておいてください。

## プロジェクト概要

**RelayLab** は個人開発者YUSAが運営するAndroidアプリ群のブランド。屋号として活動中(札幌西税務署に開業届提出済み)。

**設計哲学**: 「人間が編集者、AIが執筆者」— アプリはその場でワンタップ記録するだけの軽量キャプチャツールで、意味づけ・分析・アドバイス機能は一切持たない。データはObsidian/Codex等の下流ツールに委ねる「引き算」アプローチが、機能過多な競合アプリに対する差別化軸。

**このリポジトリ (LogRelay)**: 位置情報・タイムスタンプをホーム画面ウィジェットからワンタップ記録するアプリ。パッケージ名 `com.logrelay.app`。RelayLab第1弾で、直接配布(GitHub Releases)で先行公開中。Google Play Console組織アカウントはD-U-N-S Number取得待ち。

**記録トリガーは4経路**: ホーム画面ウィジェット / アプリ内「＋」ボタン(過去時刻の指定可) / NFCタグ / WearOS(Tile・ランチャー・ホームキー二回押し)。いずれも同じ`RecordRepository`・同じ`Record`テーブルに収束させ、トリガーごとに別の記録ロジックを作らないこと。

## モジュール構成

| モジュール | 役割 |
| --- | --- |
| `:app` | フォン側アプリ本体(ウィジェット・UI・Room DB・NFC・Wear受信) |
| `:wear` | WearOS側アプリ(Tile＋起動＝記録。記録ロジックは持たず、DataClientでフォンへ送るだけ) |
| `:wear-protocol` | フォン/ウォッチ間のDataClientプロトコル定数の共有元(`kotlin("jvm")`のローカルモジュール) |
| `:relaylab-common` | PhotoRelayと共有するday-boundary判定ロジック(git submodule) |

## 技術スタック・ビルド環境

- **JDK**: Microsoft OpenJDK 21固定(JDK 25ではない)。理由: Kotlin 1.9.24を採用しており、Kotlin側がJava 25に対応したのはKotlin 2.3.0(2025年12月リリース)から。1.9.24→2.3系への移行はK2コンパイラ移行を伴い破壊的変更リスクがあるため、意図的に枯れた組み合わせ(JDK21 LTS + Kotlin 1.9.24)を維持している。JDK25への移行は将来のKotlinメジャーバージョンアップと合わせて計画的に行う方針。
- **compileSdk / targetSdk**: 36 (Android 16.0 "Baklava")
- **minSdk**: 26
- **Kotlin**: 1.9.24
- **Gradle**: 8.9 (Wrapper経由)
- **Android Gradle Plugin**: 8.7.2 (compileSdk=36に対して未検証の警告が出るが動作は問題なし。`android.suppressUnsupportedCompileSdk=36` で抑制可能、現状は未抑制のまま許容)
- **UI**: Jetpack Compose, Glance（ウィジェット）。配色はRelayLabのサブトラクティブ・パレット(`#1A237E`等)、書体はInter(本文)/JetBrains Mono(数値・時刻)。ドット方眼紙モチーフ・日付スタンプ風タイポグラフィは2026-08のRelayLabデザイン移行で撤去済み(アイコンの「判子」モチーフのみ継承)。
- **DB**: Room。**現在のschemaはv6**。`app/schemas/` はgit管理下に置き、マイグレーション履歴として保持する。
- **WearOS**: `androidx.wear.tiles:tiles` は **1.4.1に固定**(最新の1.6.2はKotlin 2.1でコンパイルされており、本プロジェクトのKotlin 1.9.24と非互換)。フォン/ウォッチ間は`play-services-wearable`のDataClient。
- **非同期処理**: WorkManager

## Room スキーマとマイグレーション

公開済みリリース(v0.1.0-mvp / versionCode 1)のベースラインは **v4**。そこからの移行パスを必ず維持すること。

- **v4 → v5 (`MIGRATION_4_5`)**: `manual_past`マーカーを`tag`列から独立させ、専用の`isManualPast`列へ移す。既存の`tag = 'manual_past'`の行は`isManualPast = 1`に変換し`tag`をNULLにクリアする。それ以外の通常タグは変更しない。
- **v5 → v6 (`MIGRATION_5_6`)**: `sourceTriggerId`列(NULL許容・**UNIQUE制約付き**)を追加。WearOSのDataItemが重複配送された際の二重記録を防ぐための識別子。既存行はすべてNULLになる。
- **`sourceTriggerId`のNULLの扱いは意図的**: SQLiteのUNIQUE制約は複数のNULLを重複とみなさないため、ウィジェット/NFC/手動追加(いずれもトリガーIDを持たない)の記録は何件でも共存できる。一方WearOS経由は非NULLのUUIDを入れるので、同じUUIDの2回目のINSERTは`SQLiteConstraintException`で弾かれる。`RecordRepository.captureFromWatch`はこの例外を**異常系ではなく`AlreadyProcessed`という正常な結果**として扱う。
- **破壊的マイグレーション(`fallbackToDestructiveMigration`)は使わない**。既存ユーザーの記録が消えるため、テストの都合であっても導入しないこと。
- マイグレーションの検証は`app/src/androidTest/.../RecordDatabaseMigrationTest.kt`(`MigrationTestHelper`)にある。実行には実機/エミュレータでの`connectedAndroidTest`が必要。

## バックアップ / 復元

- バックアップはZIP形式(`manifest.json` ＋ `photos/`に写真の実体)。ゴミ箱の中身も含めた全件をエクスポートする。
- 復元は`RecordDao.replaceAll()`で**全消去＋挿入を1トランザクション**として行う。分けて呼ぶと、途中で失敗した際に「既存データは消えたが復元データも入っていない」空のDBが残るため、この単位を崩さないこと。
- 旧形式(v5以前)のバックアップも読める。`isManualPast`は`optBoolean(key, false)`、`sourceTriggerId`は`isNull(key)`判定でキー欠落時のデフォルトに落ちるため。この後方互換は壊さないこと。
- **既知の制約**: `insertAll`は`OnConflictStrategy.REPLACE`のため、バックアップファイル内に同一の非NULL `sourceTriggerId`を持つレコードが複数含まれていた場合(通常操作では発生しないが、手動編集・破損ファイルでは起こり得る)、例外にならず後勝ちで上書きされる。

## NFCトリガー

- 独自MIMEタイプ `application/vnd.logrelay.trigger` のNDEFタグのみに反応する(他アプリのタグへの誤反応を防ぐため)。タグ書き込み機能は設定画面の奥に内蔵。
- 記録内容は**ウィジェットタップと完全に同一**(分類タグは付けず、記録時刻は現在時刻固定、`isManualPast`も立てない)。複数タグの識別・使い分けはスコープ外で、1枚のタグ＝1つの汎用トリガーとして扱う。
- `uses-feature android:name="android.hardware.nfc" android:required="false"` は必須。NFC非搭載端末へのインストールをブロックしないため、`required="false"`を外さないこと。`NfcAdapter.getDefaultAdapter()`がnullの場合は全てのNFC処理を素通りし、通常機能は影響を受けない。
- タグ書き込み時の`enableReaderMode`に **`FLAG_READER_SKIP_NDEF_CHECK`を付けてはいけない**。このフラグはNDEF判定自体をスキップするため、未フォーマットのタグで`Ndef.get()`も`NdefFormatable.get()`も両方nullになり「NDEF形式に対応していません」の誤判定を起こす(過去に実際に踏んだ)。
- **既知の制約**: `MainActivity`は`exported="true"`(ランチャーとして必須)で、NFCの`intent-filter`にも応答するため、理論上は他アプリが同じIntentを直接送って記録をトリガーできる。影響は「意図しない記録が1件増える」程度でクラッシュやデータ漏洩はなく、ウィジェットタップと同一の設計方針の範囲内として現状は許容している。同様に、NFC経路には`sourceTriggerId`による重複排除がないため、タグを近接範囲に置き続けた場合の連続記録は防いでいない。

## アーキテクチャ原則（RelayLab共通）

- **アプリ間のランタイム依存はゼロ**: LogRelay/PhotoRelay/ConditionRelay間で共有Room DBやContentProviderは使わない。day-boundary判定ロジック(`startOfLogicalDay`)は[RelayLabCommon](https://github.com/YUSAHIKI/RelayLabCommon)という共通Kotlinモジュール(Android非依存の`kotlin("jvm")`)にgit submoduleとして切り出し済み(`common/relaylab-common`、`com.relaylab.common.DateUtils`)。LogRelay固有の合成ロジック(週次/月次ダイジェストの境界計算など)や表示フォーマット関連は引き続きLogRelay側の`util/DateUtils.kt`に残している(引き算の方針上、共通化の範囲は最小限に留める)。RelayLabCommonの`build.gradle.kts`はKotlinプラグインのバージョンを固定していない点に注意(LogRelay=Kotlin 1.9.24、PhotoRelay=Kotlin 2.0.21で異なるため、固定すると取り込み先で衝突する)。
- **データ統合はSAF(Storage Access Framework)経由**: 各relayアプリの保存先フォルダをユーザーが同じ場所に固定することで疑似的な統合を実現する。ContentProviderは使わない。
- **Intent/Deep Linkによるアプリ間遷移は片方のみインストールでも壊れないこと**: どちらのアプリの起動も、もう一方の存在に依存させない。
- **day-boundary設定は各アプリ共通のデザインパターン**: 日付の切り替わり時刻をユーザーが設定できるようにし、夜型ユーザーに配慮する。
- **オンボーディングは最小限**: フルオンボーディングは引き算の思想に反する。設定画面へのガイドリンク1本＋初回起動時のウィジェット未追加時のみ出す一度きりの案内、程度に留める。

## 機能スコープに関する決定事項(取り下げた案・歯止め)

将来の提案・実装判断が同じ議論を繰り返さないよう、機能単位で確定した方針を残しておく。

- **写真は1レコード1枚までで固定(意図的なコア設計であり、実装コストの都合ではない)**: 姉妹アプリPhotoRelayが「複数カットから代表カットを選ぶ」役割を担い、選ばれた1枚だけをIntent経由でLogRelayに渡す、という前提でRelayLabアプリ間の役割分担が成立している。LogRelay側で複数写真に対応するとこの住み分けが崩れるため、**複数写真対応は不採用**。「複数カットを残したい」というニーズはPhotoRelay側で吸収する。
- **タグ/アイコン分類機能は着手してよいが、以下の歯止めを厳守すること**:
  - タグは軽い分類ラベルに留める。タグ別の件数集計・傾向表示・グラフ化など「分析」に踏み込む機能には発展させない(設計哲学「意味づけ・分析・アドバイス機能は一切持たない」に抵触するため。分類までは許容範囲、集計・傾向表示・評価は範囲外)。
  - `Record.tag`列は**分類ラベル専用**。かつて手動追加(＋ボタン)で過去の時刻を選んだ記録を示す`manual_past`マーカーを兼ねていたが、`MIGRATION_4_5`で専用の`isManualPast`列に分離済み。この2用途をカンマ区切り等で再び同居させないこと。

## Git / リポジトリ運用

- `.gitignore` はワイルドカード(`**/build/`, `**/.idea/` など)で全階層をカバーすること。先頭スラッシュ(`/build`)はルート直下にしかマッチしないため、`app/build/` のようなサブディレクトリのビルド生成物が誤ってコミットされるリスクがある。
- 以下は絶対にコミットしない: `keystore.properties`(署名鍵情報), `local.properties`(環境依存パス), `*.jks` / `*.keystore`, `app/release/`(ビルド済みAPK/AABなどの成果物), `.gradle-user/`(GRADLE_USER_HOMEを別名で置いた場合のGradleキャッシュ。`**/.gradle/`は「.gradle」という名前にしかマッチしないため別途除外が必要)
- ビルド済みAPKはGitHub Releasesの添付ファイルとして配布し、リポジトリ本体には含めない。
- `app/schemas/` (Room DBのスキーマ履歴)は例外的にgit管理下に置く(`!app/schemas/`)。
- コミット前は必ず `git status` で差分に含まれるファイルを確認し、鍵情報(`keystore.properties`, `*.jks`/`*.keystore`)・環境依存ファイル(`local.properties`)・ビルド生成物(`app/release/`, `**/build/`)が紛れ込んでいないか確認してからステージングする。`git add -A` / `git add .` は使わず、意図したファイルを個別に指定する。

## 配布戦略

- Google Play Console(組織アカウント)登録がD-U-N-S Number待ちのため、当面はGitHub Releases経由でAPKを直接配布。
- **公開するのはフォンAPK(`:app`)のみ**。WearOSのコード(`:wear`)はリポジトリに含めるが、**WearAPKは今回のリリース成果物に含めない**。Tile UIのデザイン・振動等のフィードバック仕様・使い方の案内・実機検証が固まった後、別フェーズで公開する。
- **バージョンの位置づけ**: `0.2.0-alpha` (versionCode 2)。versionCode 1 / `0.1.0-mvp`(Room v4)からのアップグレードインストールで、v4→v5→v6のマイグレーションが走る。alphaを付けているのは、WearOS連携が「フォン側は完成しているがウォッチAPKは未配布」という中途の状態にあり、NFC・Wear周りの実機検証が限定的な端末でしかできていないため。
- リリースAPKは`keystore.properties`(非コミット)から署名情報を読む。**署名鍵は必ず同一のものを使い続けること**(証明書が変わるとアップグレードインストールができなくなり、既存ユーザーのデータが引き継げない)。v0.1.0-mvp APKの署名証明書SHA-256は `338b3921a964bc8000652750cabd34c975cbb297d099e3450e9db008bfc5efd7`。新しいAPKをリリースする前に`apksigner verify --print-certs`で一致を確認する。
- 告知の優先順位: X(Twitter) → note.com記事 → YouTube Shorts。
- ランディングページ(`logrelay-guide.html`)はGitHub Pagesでホスティングし、既存のプライバシーポリシーページ(`https://yusahiki.github.io/logrelay-privacy/`)と統合。

## 開発時の留意点

- 複雑な機能は明示的に後回しにし、小さく焦点を絞った実装を優先する。
- 変更は都度レビューしてから確定させる(自動でどんどん進めない)。
- Markdown/Obsidianが下流の主要な受け皿。LogRelayのクリップボード整形出力は、他のRelayアプリの出力と組み合わせて1つの日記的ナラティブになるよう設計する。
