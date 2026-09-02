package com.logrelay.app.util

import com.relaylab.common.DateUtils as CommonDateUtils
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

    // 「1日の区切り時刻」を考慮した論理的な1日の開始時刻(startOfLogicalDay)は、
    // PhotoRelayと共有するRelayLabCommonモジュールに切り出し済み。
    // ここではそれを土台にしたLogRelay固有の合成ロジック(週・月次ダイジェスト等)のみを持つ。

    /** 論理的な1日の終了時刻(次の区切りの1ミリ秒前) */
    fun endOfLogicalDay(epochMillis: Long, dayStartHour: Int): Long {
        return CommonDateUtils.startOfLogicalDay(epochMillis, dayStartHour) + 24L * 60 * 60 * 1000 - 1
    }

    fun startOfTodayLogical(dayStartHour: Int): Long = CommonDateUtils.startOfLogicalDay(System.currentTimeMillis(), dayStartHour)

    fun endOfTodayLogical(dayStartHour: Int): Long = endOfLogicalDay(System.currentTimeMillis(), dayStartHour)

    /**
     * Compose Material3のDatePickerが返すselectedDateMillisは「UTCでのその日の0時」。
     * 端末のローカルタイムゾーンでの年月日に変換してから、ローカルの1日の範囲を計算する。
     * (これをしないと、UTCとの時差によって前後の日にズレることがある)
     */
    fun dayBoundsFromDatePickerUtcMillis(utcMidnightMillis: Long): Pair<Long, Long> {
        val localMidnight = localMidnightFromDatePickerUtcMillis(utcMidnightMillis)
        return startOfDay(localMidnight) to endOfDay(localMidnight)
    }

    /**
     * 「週の開始曜日」(weekStartDay: Calendar.SUNDAY(1)〜Calendar.SATURDAY(7))と
     * 「1日の区切り時刻」(dayStartHour)を考慮した、論理的な週の開始時刻を求める。
     * dayStartHourを考慮するのは、日をまたぐ深夜の記録が「今日の振り返り」タブと
     * 週次ダイジェストとで別の週に振り分けられてしまう、という食い違いを避けるため。
     */
    fun startOfLogicalWeek(epochMillis: Long, dayStartHour: Int, weekStartDay: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = CommonDateUtils.startOfLogicalDay(epochMillis, dayStartHour) }
        while (cal.get(Calendar.DAY_OF_WEEK) != weekStartDay) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return cal.timeInMillis
    }

    /** 論理的な週の終了時刻(次の週の開始の1ミリ秒前) */
    fun endOfLogicalWeek(epochMillis: Long, dayStartHour: Int, weekStartDay: Int): Long {
        return startOfLogicalWeek(epochMillis, dayStartHour, weekStartDay) + 7L * 24 * 60 * 60 * 1000 - 1
    }

    /**
     * 「月の開始日」(monthStartDay: 1〜31)と「1日の区切り時刻」(dayStartHour)を考慮した、
     * 論理的な月の開始時刻を求める。
     * monthStartDayが29〜31で、その日が存在しない月(2月など)の場合はroundToNextMonthに従う:
     * false(前の日に丸める)ならその月の末日、true(次の日に丸める)なら翌月1日を開始日とする。
     *
     * 「周期の基準年月」をmonthStartDayとの比較のみで先に確定させ、その基準年月に対して
     * 丸め処理を適用する2段階の実装にしている。丸め後の実際の日付から直接「+1か月」する実装だと、
     * 丸めで月をまたいだ結果、周期の基準がズレて終了時刻の計算が狂うため。
     */
    fun startOfLogicalMonth(epochMillis: Long, dayStartHour: Int, monthStartDay: Int, roundToNextMonth: Boolean = false): Long {
        val (cycleYear, cycleMonth) = logicalMonthCycleAnchor(epochMillis, dayStartHour, monthStartDay)
        return logicalMonthCycleStart(cycleYear, cycleMonth, dayStartHour, monthStartDay, roundToNextMonth)
    }

    /** 論理的な月の終了時刻(次の月の開始の1ミリ秒前) */
    fun endOfLogicalMonth(epochMillis: Long, dayStartHour: Int, monthStartDay: Int, roundToNextMonth: Boolean = false): Long {
        val (cycleYear, cycleMonth) = logicalMonthCycleAnchor(epochMillis, dayStartHour, monthStartDay)
        val nextCal = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, cycleYear)
            set(Calendar.MONTH, cycleMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, 1)
        }
        return logicalMonthCycleStart(nextCal.get(Calendar.YEAR), nextCal.get(Calendar.MONTH), dayStartHour, monthStartDay, roundToNextMonth) - 1
    }

    /** epochMillisが属する「月次周期」の基準年月(まだmonthStartDayの丸めは適用していない)を求める */
    private fun logicalMonthCycleAnchor(epochMillis: Long, dayStartHour: Int, monthStartDay: Int): Pair<Int, Int> {
        val cal = Calendar.getInstance().apply { timeInMillis = CommonDateUtils.startOfLogicalDay(epochMillis, dayStartHour) }
        if (cal.get(Calendar.DAY_OF_MONTH) < monthStartDay) {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.MONTH, -1)
        }
        return cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
    }

    /** 基準年月(cycleYear, cycleMonth)に対して、monthStartDayの丸め処理を適用した実際の開始時刻を求める */
    private fun logicalMonthCycleStart(cycleYear: Int, cycleMonth: Int, dayStartHour: Int, monthStartDay: Int, roundToNextMonth: Boolean): Long {
        val cal = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, cycleYear)
            set(Calendar.MONTH, cycleMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (monthStartDay > maxDay) {
            if (roundToNextMonth) {
                cal.add(Calendar.MONTH, 1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
            } else {
                cal.set(Calendar.DAY_OF_MONTH, maxDay)
            }
        } else {
            cal.set(Calendar.DAY_OF_MONTH, monthStartDay)
        }
        cal.set(Calendar.HOUR_OF_DAY, dayStartHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** DatePickerで選んだ日付を含む「論理的な週」の範囲を求める(週次ダイジェスト用) */
    fun logicalWeekBoundsFromDatePickerUtcMillis(utcMidnightMillis: Long, dayStartHour: Int, weekStartDay: Int): Pair<Long, Long> {
        val localMidnight = localMidnightFromDatePickerUtcMillis(utcMidnightMillis)
        val start = startOfLogicalWeek(localMidnight, dayStartHour, weekStartDay)
        return start to endOfLogicalWeek(localMidnight, dayStartHour, weekStartDay)
    }

    /** DatePickerで選んだ日付を含む「論理的な月」の範囲を求める(月次ダイジェスト用) */
    fun logicalMonthBoundsFromDatePickerUtcMillis(
        utcMidnightMillis: Long,
        dayStartHour: Int,
        monthStartDay: Int,
        roundToNextMonth: Boolean = false
    ): Pair<Long, Long> {
        val localMidnight = localMidnightFromDatePickerUtcMillis(utcMidnightMillis)
        val start = startOfLogicalMonth(localMidnight, dayStartHour, monthStartDay, roundToNextMonth)
        return start to endOfLogicalMonth(localMidnight, dayStartHour, monthStartDay, roundToNextMonth)
    }

    /** Compose DatePickerのUTC基準の年月日を、端末のローカルタイムゾーンでの同じ年月日の0時に変換する */
    private fun localMidnightFromDatePickerUtcMillis(utcMidnightMillis: Long): Long {
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
        return localCal.timeInMillis
    }
}
