package com.logrelay.app.util

import android.content.Context

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
}

enum class ViewMode { LIST, CARD }
