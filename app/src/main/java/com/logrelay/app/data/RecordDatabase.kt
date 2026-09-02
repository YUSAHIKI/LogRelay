package com.logrelay.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * バージョン4を「リリース前の最終スキーマ」として固定する。
 * ここから先、テーブル構造を変える(列の追加・削除・型変更など)ときは、
 * 必ず下のMIGRATIONS配列に新しいMigrationを追加すること。
 *
 * 【NGな進め方】
 * versionだけ上げて、fallbackToDestructiveMigration()に頼る
 * → リリース後にこれをやると、ユーザーの記録・写真が全て消える。
 *
 * 【正しい進め方】
 * 1. Recordにカラムを追加/変更する
 * 2. @Database の version を +1 する
 * 3. 「MIGRATION_4_5」のような命名でMigrationオブジェクトを書き、
 *    database.execSQL("ALTER TABLE records ADD COLUMN ...") のようにSQLで変更を反映する
 * 4. MIGRATIONS配列に追加する
 * 5. app/schemas/ 配下に出力される新しいスキーマJSONをコミットしておく
 *    (次のMigrationを書くときの検証材料になる)
 */
@Database(entities = [Record::class], version = 6, exportSchema = true)
abstract class RecordDatabase : RoomDatabase() {

    abstract fun recordDao(): RecordDao

    companion object {
        @Volatile
        private var INSTANCE: RecordDatabase? = null

        // tag列を「分類タグ」の実用途に転用するため、これまでtagに間借りしていた
        // manual_pastマーカーを専用カラム(isManualPast)へ切り出す。
        // 既存データの'manual_past'タグは新カラムへ付け替え、tagはクリアする
        // (分類タグとしての初期状態=未分類に戻す)。
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE records ADD COLUMN isManualPast INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE records SET isManualPast = 1, tag = NULL WHERE tag = 'manual_past'")
            }
        }

        // WearOSトリガー機能: DataItemのUUIDをsourceTriggerIdとして持たせ、
        // UNIQUE制約による重複処理防止(INSERT時のSQLiteConstraintException捕捉)を可能にする。
        // ウィジェット/NFC等の既存経路は常にnullのままなので、既存データ・挙動への影響はない。
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE records ADD COLUMN sourceTriggerId TEXT")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_records_sourceTriggerId ON records(sourceTriggerId)")
            }
        }

        private val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_4_5,
            MIGRATION_5_6,
        )

        // ウィジェット(プロセス外から呼ばれる)とアプリ本体の両方から
        // 同じインスタンスを安全に共有するためシングルトン化
        fun getInstance(context: Context): RecordDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RecordDatabase::class.java,
                    "logrelay.db"
                )
                    .addMigrations(*MIGRATIONS)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
