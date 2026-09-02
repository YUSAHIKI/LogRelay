package com.logrelay.app.util

import com.logrelay.app.data.Record
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportHelper {

    /** 「記録一覧」と同じ日付グループ形式でMarkdownを組み立てる */
    fun toMarkdown(records: List<Record>): String {
        val dayFormatter = SimpleDateFormat("yyyy年M月d日(E)", Locale.JAPAN)
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.JAPAN)
        val grouped = records.sortedByDescending { it.timestamp }.groupBy { dayFormatter.format(Date(it.timestamp)) }

        val sb = StringBuilder("# LogRelay 記録\n\n")
        grouped.forEach { (day, dayRecords) ->
            sb.append("## $day\n\n")
            dayRecords.forEach { record ->
                val time = timeFormatter.format(Date(record.timestamp))
                val location = locationLabel(record)
                sb.append("- **$time** $location")
                if (record.memo.isNotBlank()) {
                    sb.append(" — ${record.memo.replace("\n", " ")}")
                }
                sb.append("\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    /** スプレッドシートで扱いやすいよう、時系列順(古い→新しい)でCSVを組み立てる */
    fun toCsv(records: List<Record>): String {
        val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN)
        val sb = StringBuilder("日時,緯度,経度,地名,メモ\n")
        records.sortedBy { it.timestamp }.forEach { record ->
            sb.append(csvField(timeFormatter.format(Date(record.timestamp)))).append(",")
            sb.append(csvField(record.latitude?.toString() ?: "")).append(",")
            sb.append(csvField(record.longitude?.toString() ?: "")).append(",")
            sb.append(csvField(placeNameLabel(record))).append(",")
            sb.append(csvField(record.memo))
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun csvField(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    // AIへの受け渡しを見据え、手動追加(＋ボタン)で過去の時刻を選んだ記録には
    // 「位置情報は記録操作時点のもの」であることが伝わるよう、書き出し内容にも注記を含める
    private fun manualPastSuffix(record: Record): String = if (record.isManualPast) " ※後から追加" else ""

    private fun locationLabel(record: Record): String {
        val base = when {
            record.placeName != null -> record.placeName
            record.latitude != null && record.longitude != null -> "(${record.latitude}, ${record.longitude})"
            else -> ""
        }
        val suffix = manualPastSuffix(record)
        return if (suffix.isEmpty()) base else "$base$suffix".trim()
    }

    private fun placeNameLabel(record: Record): String {
        val base = record.placeName ?: ""
        val suffix = manualPastSuffix(record)
        return if (suffix.isEmpty()) base else "$base$suffix".trim()
    }

    /** バックアップ用：全カラムをそのままJSON化する(削除済みも含めて完全復元できるように) */
    fun toBackupJson(records: List<Record>): String {
        val array = JSONArray()
        records.forEach { r ->
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("timestamp", r.timestamp)
            obj.put("latitude", r.latitude ?: JSONObject.NULL)
            obj.put("longitude", r.longitude ?: JSONObject.NULL)
            obj.put("tag", r.tag ?: JSONObject.NULL)
            obj.put("memo", r.memo)
            obj.put("deletedAt", r.deletedAt ?: JSONObject.NULL)
            obj.put("placeName", r.placeName ?: JSONObject.NULL)
            // 絶対パスはバックアップ先の端末で意味を持たないため、ファイル名だけを保存する。
            // 実体はZIP内のphotos/フォルダに同梱される
            obj.put("photoPath", r.photoPath?.let { java.io.File(it).name } ?: JSONObject.NULL)
            obj.put("isManualPast", r.isManualPast)
            obj.put("sourceTriggerId", r.sourceTriggerId ?: JSONObject.NULL)
            array.put(obj)
        }
        val root = JSONObject()
        root.put("version", 1)
        root.put("records", array)
        return root.toString(2)
    }

    /** バックアップJSONを読み込み、Recordのリストに戻す */
    fun fromBackupJson(json: String): List<Record> {
        val root = JSONObject(json)
        val array = root.getJSONArray("records")
        val result = mutableListOf<Record>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                Record(
                    id = obj.getLong("id"),
                    timestamp = obj.getLong("timestamp"),
                    latitude = if (obj.isNull("latitude")) null else obj.getDouble("latitude"),
                    longitude = if (obj.isNull("longitude")) null else obj.getDouble("longitude"),
                    tag = if (obj.isNull("tag")) null else obj.getString("tag"),
                    memo = obj.optString("memo", ""),
                    deletedAt = if (obj.isNull("deletedAt")) null else obj.getLong("deletedAt"),
                    placeName = if (obj.isNull("placeName")) null else obj.getString("placeName"),
                    photoPath = if (obj.isNull("photoPath")) null else obj.getString("photoPath"),
                    isManualPast = obj.optBoolean("isManualPast", false),
                    sourceTriggerId = if (obj.isNull("sourceTriggerId")) null else obj.getString("sourceTriggerId")
                )
            )
        }
        return result
    }
}
