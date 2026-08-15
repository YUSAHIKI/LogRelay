package com.logrelay.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {

    @Insert
    suspend fun insert(record: Record): Long

    @Update
    suspend fun update(record: Record)

    // 削除されていない記録のみ、新しい順（本体アプリの一覧用）
    @Query("SELECT * FROM records WHERE deletedAt IS NULL ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Record>>

    // 「1日の振り返り」画面用：削除されていない、指定日範囲の記録
    @Query("SELECT * FROM records WHERE deletedAt IS NULL AND timestamp BETWEEN :startOfDay AND :endOfDay ORDER BY timestamp ASC")
    fun observeForDay(startOfDay: Long, endOfDay: Long): Flow<List<Record>>

    // ゴミ箱：削除された記録を、削除日時が新しい順に
    @Query("SELECT * FROM records WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<Record>>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getById(id: Long): Record?

    @Query("UPDATE records SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    @Query("UPDATE records SET deletedAt = :deletedAt WHERE id IN (:ids)")
    suspend fun softDeleteMany(ids: List<Long>, deletedAt: Long)

    @Query("UPDATE records SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("UPDATE records SET deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restoreMany(ids: List<Long>)

    @Query("UPDATE records SET placeName = :placeName WHERE id = :id")
    suspend fun updatePlaceName(id: Long, placeName: String)

    @Query("UPDATE records SET photoPath = :photoPath WHERE id = :id")
    suspend fun updatePhotoPath(id: Long, photoPath: String?)

    // バックアップ用：削除済みも含めた全件(復元時に完全な状態に戻せるように)
    @Query("SELECT * FROM records")
    suspend fun getAllRaw(): List<Record>

    // バックアップ復元用：既存データを全消去してから復元データを挿入する
    @Query("DELETE FROM records")
    suspend fun deleteAllRaw()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<Record>)

    // ゴミ箱からの「完全に削除」。ユーザーが明示的に選択した場合のみ呼ばれる
    @Query("DELETE FROM records WHERE id IN (:ids)")
    suspend fun hardDeleteMany(ids: List<Long>)

    // 保持期限(thresholdより古い削除日時)を過ぎたものを完全に消す
    @Query("DELETE FROM records WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun purgeExpired(threshold: Long)
}
