package com.logrelay.app.util

import android.content.Context
import java.util.Calendar
import com.logrelay.app.data.Record

/**
 * 「1日の区切り時刻」を保存する。深夜0時以降に振り返りをする人が、
 * 例えば「4時が日付の切り替わり」に設定できるようにするための設定値。
 */
object SettingsStore {

    private const val PREFS_NAME = "logrelay_settings"
    private const val KEY_DAY_START_HOUR = "day_start_hour"
    private const val KEY_VIEW_MODE_PREFIX = "view_mode_tab_"

    fun getDayStartHour(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_DAY_START_HOUR, 0)
    }

    fun setDayStartHour(context: Context, hour: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_DAY_START_HOUR, hour.coerceIn(0, 23)).apply()
    }

    // 週次/月次ダイジェストの基準日。day-boundary(1日の区切り時刻)設定と同じ考え方の延長。
    private const val KEY_WEEK_START_DAY = "week_start_day"
    private const val KEY_MONTH_START_DAY = "month_start_day"

    /** 週の開始曜日。Calendar.SUNDAY(1)〜Calendar.SATURDAY(7)。デフォルトは月曜始まり */
    fun getWeekStartDay(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_WEEK_START_DAY, Calendar.MONDAY)
    }

    fun setWeekStartDay(context: Context, weekStartDay: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_WEEK_START_DAY, weekStartDay.coerceIn(Calendar.SUNDAY, Calendar.SATURDAY)).apply()
    }

    /** 月の開始日。1〜31。デフォルトは1日始まり */
    fun getMonthStartDay(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_MONTH_START_DAY, 1)
    }

    fun setMonthStartDay(context: Context, monthStartDay: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_MONTH_START_DAY, monthStartDay.coerceIn(1, 31)).apply()
    }

    // 開始日として29〜31日を選んだ場合、その日が存在しない月(2月など)の丸め方向。
    // 29-28日を選んでいる間は使われない(UI上もその場合のみ表示する)
    private const val KEY_MONTH_START_DAY_ROUND_TO_NEXT = "month_start_day_round_to_next"

    /** true=次の月の1日に丸める、false(デフォルト)=その月の末日に丸める */
    fun getMonthStartDayRoundToNext(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_MONTH_START_DAY_ROUND_TO_NEXT, false)
    }

    fun setMonthStartDayRoundToNext(context: Context, roundToNext: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MONTH_START_DAY_ROUND_TO_NEXT, roundToNext).apply()
    }

    /** タブごと(0:記録一覧 1:今日の振り返り 2:ゴミ箱)に表示形式を独立して保存する */
    fun getViewMode(context: Context, tabIndex: Int): ViewMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("$KEY_VIEW_MODE_PREFIX$tabIndex", ViewMode.LIST.name)
        return runCatching { ViewMode.valueOf(raw ?: ViewMode.LIST.name) }.getOrDefault(ViewMode.LIST)
    }

    fun setViewMode(context: Context, tabIndex: Int, mode: ViewMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("$KEY_VIEW_MODE_PREFIX$tabIndex", mode.name).apply()
    }

    // ローカル自動バックアップの保存先(SAFのツリーUri文字列)と間隔(時間、0=オフ)
    private const val KEY_BACKUP_FOLDER_URI = "backup_folder_uri"
    private const val KEY_BACKUP_INTERVAL_HOURS = "backup_interval_hours"

    fun getBackupFolderUri(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_BACKUP_FOLDER_URI, null)
    }

    fun setBackupFolderUri(context: Context, uri: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BACKUP_FOLDER_URI, uri).apply()
    }

    fun getBackupIntervalHours(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_BACKUP_INTERVAL_HOURS, 0L)
    }

    fun setBackupIntervalHours(context: Context, hours: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_BACKUP_INTERVAL_HOURS, hours).apply()
    }

    // 「AI用にコピー」で使うテンプレート文言。
    // {date} {location} {memo} のプレースホルダーを、実際の値に置き換えて使う。
    private const val KEY_AI_PROMPT_TEMPLATE = "ai_prompt_template"

    const val DEFAULT_AI_PROMPT_TEMPLATE = """このログについて、背景や意味を深掘りする質問を1つしてください。

---
date: {date}
location: {location}
memo: |
  {memo}
---"""

    fun getAiPromptTemplate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_AI_PROMPT_TEMPLATE, null) ?: DEFAULT_AI_PROMPT_TEMPLATE
    }

    fun setAiPromptTemplate(context: Context, template: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_AI_PROMPT_TEMPLATE, template).apply()
    }

    // タグの表示名(5スロット固定、個数・並び順は変更不可。名称のみ編集可能)
    private const val KEY_TAG_LABEL_PREFIX = "tag_label_"

    fun getTagLabel(context: Context, slotKey: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val default = Record.TAG_DEFAULT_LABELS[slotKey] ?: slotKey
        return prefs.getString("$KEY_TAG_LABEL_PREFIX$slotKey", null) ?: default
    }

    fun setTagLabel(context: Context, slotKey: String, label: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("$KEY_TAG_LABEL_PREFIX$slotKey", label).apply()
    }
}

enum class ViewMode { LIST, CARD }
