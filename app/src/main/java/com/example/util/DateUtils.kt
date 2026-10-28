package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val displayMonthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())

    fun getCurrentMonthKey(): String {
        return monthKeyFormat.format(Date())
    }

    fun formatDisplayMonth(monthKey: String): String {
        return try {
            val date = monthKeyFormat.parse(monthKey) ?: Date()
            displayMonthFormat.format(date)
        } catch (e: Exception) {
            monthKey
        }
    }

    fun formatShortDate(timestamp: Long): String {
        return shortDateFormat.format(Date(timestamp))
    }

    fun formatDay(timestamp: Long): String {
        return dayFormat.format(Date(timestamp))
    }

    fun formatTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }

    fun getMonthStartAndEndTime(monthKey: String): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        try {
            val date = monthKeyFormat.parse(monthKey)
            if (date != null) {
                calendar.time = date
            }
        } catch (e: Exception) {
            // fallback current month
        }
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endTime = calendar.timeInMillis

        return Pair(startTime, endTime)
    }

    fun getPreviousMonthKey(monthKey: String): String {
        val calendar = Calendar.getInstance()
        try {
            val date = monthKeyFormat.parse(monthKey)
            if (date != null) {
                calendar.time = date
            }
        } catch (e: Exception) {
            // ignore
        }
        calendar.add(Calendar.MONTH, -1)
        return monthKeyFormat.format(calendar.time)
    }

    fun getNextMonthKey(monthKey: String): String {
        val calendar = Calendar.getInstance()
        try {
            val date = monthKeyFormat.parse(monthKey)
            if (date != null) {
                calendar.time = date
            }
        } catch (e: Exception) {
            // ignore
        }
        calendar.add(Calendar.MONTH, 1)
        return monthKeyFormat.format(calendar.time)
    }

    fun getDaysInMonth(monthKey: String): Int {
        val calendar = Calendar.getInstance()
        try {
            val date = monthKeyFormat.parse(monthKey)
            if (date != null) {
                calendar.time = date
                return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            }
        } catch (e: Exception) {
            // ignore
        }
        return 30
    }
}
