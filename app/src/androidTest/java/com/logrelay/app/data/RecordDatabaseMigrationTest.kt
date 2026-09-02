package com.logrelay.app.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"

/**
 * Room schema v4(公開リリース基準)からv5・v6へのMigrationを検証する。
 *
 * 実行には実機/エミュレータでのconnectedAndroidTest実行が必要。この開発環境には接続済みの
 * 端末がないため、コンパイル確認は行ったが実行はできていない(README/リリースメモに明記のこと)。
 * データレベルの整合性は別途、生のsqlite3 CLIによるドライラン(同じMigration SQL文をv4形状の
 * テーブルに適用)でも確認済み。
 *
 * `helper.runMigrationsAndValidate(..., validateDroppedTables = true)` は、androidTestの
 * assetsとして含めたapp/schemas配下のJSON(build.gradle.ktsのsourceSets設定を参照)と、
 * 実際にMigration後にできたテーブル定義が一致するかも合わせて検証する。
 */
@RunWith(AndroidJUnit4::class)
class RecordDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RecordDatabase::class.java
    )

    // v4時点のrecordsテーブルへ直接INSERTする(app/schemas/.../4.jsonのcreateSqlに対応する列のみ)
    private fun SupportSQLiteDatabase.insertV4Record(
        timestamp: Long,
        latitude: Double?,
        longitude: Double?,
        tag: String?,
        memo: String
    ) {
        execSQL(
            "INSERT INTO records (timestamp, latitude, longitude, tag, memo) VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any?>(timestamp, latitude, longitude, tag, memo)
        )
    }

    @Test
    fun migrate4To5_preservesRecordsAndConvertsManualPastTag() {
        helper.createDatabase(TEST_DB, 4).apply {
            insertV4Record(1_700_000_000_000, 35.0, 139.0, null, "normal record")
            insertV4Record(1_700_000_001_000, null, null, "manual_past", "past-entry record")
            insertV4Record(1_700_000_002_000, 35.1, 139.1, "tag_1", "tagged record")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, RecordDatabase.MIGRATION_4_5)

        val cursor = db.query("SELECT tag, isManualPast, memo FROM records ORDER BY id")
        var count = 0
        while (cursor.moveToNext()) {
            count++
            val tag = if (cursor.isNull(0)) null else cursor.getString(0)
            val isManualPast = cursor.getInt(1)
            when (val memo = cursor.getString(2)) {
                "normal record" -> {
                    assertNull("通常記録のtagはnullのまま", tag)
                    assertEquals("通常記録のisManualPastは0", 0, isManualPast)
                }
                "past-entry record" -> {
                    // tag='manual_past' は isManualPast=1 へ移行し、tagはクリアされる
                    assertNull("manual_pastタグはクリアされる", tag)
                    assertEquals("manual_pastタグはisManualPast=1へ移行する", 1, isManualPast)
                }
                "tagged record" -> {
                    // manual_past以外の通常タグはそのまま保持される
                    assertEquals("通常タグ(manual_past以外)は変更されない", "tag_1", tag)
                    assertEquals(0, isManualPast)
                }
                else -> fail("想定外のレコード: $memo")
            }
        }
        cursor.close()
        assertEquals("3件とも保持されている", 3, count)
        db.close()
    }

    @Test
    fun migrate5To6_sourceTriggerIdIsNullableAndUnique() {
        helper.createDatabase(TEST_DB, 4).apply {
            insertV4Record(1_700_000_000_000, 35.0, 139.0, null, "widget record")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 5, true, RecordDatabase.MIGRATION_4_5).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, RecordDatabase.MIGRATION_5_6)

        // 既存行(v4からの移行分)のsourceTriggerIdはNULLのまま
        val existingCursor = db.query("SELECT sourceTriggerId FROM records")
        assertTrue(existingCursor.moveToFirst())
        assertTrue("移行前から存在する行のsourceTriggerIdはNULL", existingCursor.isNull(0))
        existingCursor.close()

        // ウィジェット/NFC相当: sourceTriggerIdがNULLの行を複数追加できる(UNIQUE制約に抵触しない)
        db.execSQL("INSERT INTO records (timestamp, memo) VALUES (2000, 'widget record 2')")
        db.execSQL("INSERT INTO records (timestamp, memo) VALUES (3000, 'nfc record')")

        val countCursor = db.query("SELECT COUNT(*) FROM records")
        countCursor.moveToFirst()
        assertEquals("NULLのsourceTriggerIdは複数件共存できる", 3, countCursor.getInt(0))
        countCursor.close()

        // WearOS相当: 非NULLのsourceTriggerIdを持つ行を1件追加できる
        db.execSQL(
            "INSERT INTO records (timestamp, memo, sourceTriggerId) VALUES (4000, 'wear record 1', 'uuid-aaaa-1111')"
        )

        // 同一sourceTriggerIdの重複INSERTはUNIQUE制約で拒否される(DataItemの重複配送防止の要)
        var constraintViolated = false
        try {
            db.execSQL(
                "INSERT INTO records (timestamp, memo, sourceTriggerId) VALUES (5000, 'wear record 2 dup', 'uuid-aaaa-1111')"
            )
        } catch (e: SQLiteConstraintException) {
            constraintViolated = true
        }
        assertTrue("同一sourceTriggerIdの重複INSERTはSQLiteConstraintExceptionになるはず", constraintViolated)

        val finalCountCursor = db.query("SELECT COUNT(*) FROM records")
        finalCountCursor.moveToFirst()
        assertEquals("重複INSERTは反映されず4件のまま", 4, finalCountCursor.getInt(0))
        finalCountCursor.close()

        db.close()
    }

    @Test
    fun migrate4To6_allAtOnce_preservesDataThroughBothMigrations() {
        helper.createDatabase(TEST_DB, 4).apply {
            insertV4Record(1_700_000_000_000, 35.0, 139.0, null, "normal record")
            insertV4Record(1_700_000_001_000, null, null, "manual_past", "past-entry record")
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 6, true,
            RecordDatabase.MIGRATION_4_5, RecordDatabase.MIGRATION_5_6
        )

        val countCursor = db.query("SELECT COUNT(*) FROM records")
        countCursor.moveToFirst()
        assertEquals("v4→v6を一括適用しても2件とも保持される", 2, countCursor.getInt(0))
        countCursor.close()

        val pastCursor = db.query(
            "SELECT isManualPast, tag, sourceTriggerId FROM records WHERE memo = 'past-entry record'"
        )
        assertTrue(pastCursor.moveToFirst())
        assertEquals(1, pastCursor.getInt(0))
        assertTrue("tagはクリアされている", pastCursor.isNull(1))
        assertTrue("sourceTriggerIdはNULL(ウィジェット/NFC/既存データ由来)", pastCursor.isNull(2))
        pastCursor.close()

        db.close()
    }
}
