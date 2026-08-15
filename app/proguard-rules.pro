# LogRelay用 ProGuard/R8ルール
#
# Compose・Room・Glance・WorkManagerの各ライブラリは、それぞれのAARに
# consumer-rules.pro(ライブラリ側が指定する既定のルール)を同梱しており、
# 通常はここに書かなくても自動的に守られる。
# ここに書いているのは、それでも壊れやすい「リフレクションで生成/呼び出される」箇所への追加保護。

# --- Room ---
# @Entityのデータクラスと、KSPが生成するDao実装はリフレクション/生成コードから参照されるため、
# 難読化で名前が変わると動かなくなる。Entityクラスと生成された*_Implクラスを保護する。
-keep class com.logrelay.app.data.Record { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- Glance ウィジェット ---
# AppWidgetProviderやActionCallbackの実装クラスは、システム(AndroidManifestのreceiver定義や
# Glanceのアクション解決)からクラス名で参照されるため、名前を変えられると動かなくなる。
-keep class com.logrelay.app.widget.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver
-keep class * extends androidx.glance.appwidget.GlanceAppWidget
-keep class * implements androidx.glance.appwidget.action.ActionCallback

# --- WorkManager ---
# CoroutineWorkerのサブクラスは、WorkManagerがクラス名から実行時にリフレクションで
# インスタンス化するため、コンストラクタとクラス名を保護する必要がある。
-keep class com.logrelay.app.util.AutoBackupWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- kotlinx.serialization等は未使用のため対象外 ---
# --- org.json (標準ライブラリ、難読化対象外なので特別な指定は不要) ---
