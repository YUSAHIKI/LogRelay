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
@Database(entities = [Record::class], version = 4, exportSchema = true)
abstract class RecordDatabase : RoomDatabase() {

    abstract fun recordDao(): RecordDao

    companion object {
        @Volatile
        private var INSTANCE: RecordDatabase? = null

        // 例：将来カラムを追加する場合のテンプレート(現時点では未使用)
        // val MIGRATION_4_5 = object : Migration(4, 5) {
        //     override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        //         database.execSQL("ALTER TABLE records ADD COLUMN newColumn TEXT")
        //     }
        // }

        private val MIGRATIONS: Array<Migration> = arrayOf(
            // MIGRATION_4_5,
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
