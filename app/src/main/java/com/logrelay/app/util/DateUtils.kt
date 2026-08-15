package com.logrelay.app.util

import java.util.Calendar
import java.util.TimeZone

object DateUtils {

    /** 今日の 00:00:00.000 の epoch millis */
    fun startOfToday(): Long = startOfDay(System.currentTimeMillis())

    /** 今日の 23:59:59.999 の epoch millis */
    fun endOfToday(): Long = endOfDay(System.currentTimeMillis())

    /** 指定epoch millisが属する日の 00:00:00.000（端末のローカルタイムゾーン基準） */
    fun startOfDay(epochMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = epochMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /** 指定epoch millisが属する日の 23:59:59.999（端末のローカルタイムゾーン基準） */
    fun endOfDay(epochMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = epochMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    /**
     * 「1日の区切り時刻」を考慮した論理的な1日の開始時刻を求める。
     * 例：dayStartHour=4 の場合、深夜2時の記録は「前日の続き」として扱われる。
     * 深夜に振り返りをする人向けの配慮。dayStartHour=0なら従来のstartOfDayと同じ結果になる。
     */
    fun startOfLogicalDay(epochMillis: Long, dayStartHour: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
        cal.add(Calendar.HOUR_OF_DAY, -dayStartHour)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.HOUR_OF_DAY, dayStartHour)
        return cal.timeInMillis
    }

    /** 論理的な1日の終了時刻(次の区切りの1ミリ秒前) */
    fun endOfLogicalDay(epochMillis: Long, dayStartHour: Int): Long {
        return startOfLogicalDay(epochMillis, dayStartHour) + 24L * 60 * 60 * 1000 - 1
    }

    fun startOfTodayLogical(dayStartHour: Int): Long = startOfLogicalDay(System.currentTimeMillis(), dayStartHour)

    fun endOfTodayLogical(dayStartHour: Int): Long = endOfLogicalDay(System.currentTimeMillis(), dayStartHour)

    /**
     * Compose Material3のDatePickerが返すselectedDateMillisは「UTCでのその日の0時」。
     * 端末のローカルタイムゾーンでの年月日に変換してから、ローカルの1日の範囲を計算する。
     * (これをしないと、UTCとの時差によって前後の日にズレることがある)
     */
    fun dayBoundsFromDatePickerUtcMillis(utcMidnightMillis: Long): Pair<Long, Long> {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMidnightMillis
        }
        val localCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return startOfDay(localCal.timeInMillis) to endOfDay(localCal.timeInMillis)
    }

    /**
     * DatePickerが返すUTC基準の日付から「その月全体」の範囲(ローカルタイムゾーン)を求める。
     * 日にちの情報は無視し、年月だけを使う。
     */
    fun monthBoundsFromDatePickerUtcMillis(utcMidnightMillis: Long): Pair<Long, Long> {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMidnightMillis
        }
        val startCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = (startCal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return startCal.timeInMillis to endCal.timeInMillis
    }
}
