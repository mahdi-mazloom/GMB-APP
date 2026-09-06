package com.example.util

import java.util.Calendar
import java.util.Locale

object PersianDateHelper {

    private val PERSIAN_MONTH_NAMES = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    /**
     * Converts a Gregorian date (year, month: 1..12, day: 1..31) to Solar Hijri (Jalali).
     * Returns Triple(jalaliYear, jalaliMonth, jalaliDay)
     */
    fun gregorianToJalali(gYear: Int, gMonth: Int, gDay: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

        var gy = gYear - 1600
        var gm = gMonth - 1
        var gd = gDay - 1

        var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400

        for (i in 0 until gm) {
            gDayNo += gDaysInMonth[i]
        }
        if (gm > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) {
            gDayNo++
        }
        gDayNo += gd

        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        var jm = 0
        for (i in 0..11) {
            if (jDayNo < jDaysInMonth[i]) {
                jm = i + 1
                break
            }
            jDayNo -= jDaysInMonth[i]
        }
        val jd = jDayNo + 1
        return Triple(jy, jm, jd)
    }

    /**
     * Converts a Solar Hijri (Jalali) date to Gregorian.
     * Returns Triple(gregorianYear, gregorianMonth: 1..12, gregorianDay: 1..31)
     */
    fun jalaliToGregorian(jYear: Int, jMonth: Int, jDay: Int): Triple<Int, Int, Int> {
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        val jy = jYear - 979
        val jm = jMonth - 1
        val jd = jDay - 1

        var jDayNo = 365L * jy + (jy / 33) * 8 + ((jy % 33 + 3) / 4)
        for (i in 0 until jm) {
            jDayNo += jDaysInMonth[i]
        }
        jDayNo += jd

        var gDayNo = jDayNo + 79
        var gy = 1600 + 400 * (gDayNo / 146097)
        gDayNo %= 146097

        var leap = true
        if (gDayNo >= 36525) {
            gDayNo--
            gy += 100 * (gDayNo / 36524)
            gDayNo %= 36524

            if (gDayNo >= 365) {
                gDayNo++
            } else {
                leap = false
            }
        }

        gy += 4 * (gDayNo / 1461)
        gDayNo %= 1461

        if (gDayNo >= 366) {
            leap = false
            gDayNo--
            gy += gDayNo / 365
            gDayNo %= 365
        }

        val gDaysInMonth = intArrayOf(
            31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
        )
        var gm = 0
        for (i in 0..11) {
            if (gDayNo < gDaysInMonth[i]) {
                gm = i + 1
                break
            }
            gDayNo -= gDaysInMonth[i]
        }
        val gd = gDayNo + 1
        return Triple(gy.toInt(), gm, gd.toInt())
    }

    /**
     * Flexible date parser that returns Triple(jalaliYear, jalaliMonth, jalaliDay).
     * Supports:
     * - Jalali formatted: "1405/06/20", "1405-06-20", "1405/6/20"
     * - Gregorian formatted: "2026-09-11", "2026/09/11"
     * - Persian text formatted: "20 شهریور 1405"
     * - Unix timestamps: "1789045200"
     */
    fun parseDateToJalali(rawDate: String?): Triple<Int, Int, Int>? {
        if (rawDate.isNullOrBlank()) return null
        val clean = toEnglishDigits(rawDate.trim())

        // Check if Unix timestamp (seconds or milliseconds)
        val timestamp = clean.toLongOrNull()
        if (timestamp != null && timestamp > 1000000000L) {
            val millis = if (timestamp < 100000000000L) timestamp * 1000L else timestamp
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            return gregorianToJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        // Check for text containing Persian month names (e.g. "20 شهریور 1405")
        for (i in PERSIAN_MONTH_NAMES.indices) {
            val mName = PERSIAN_MONTH_NAMES[i]
            if (rawDate.contains(mName)) {
                val numbers = Regex("\\d+").findAll(clean).map { it.value.toInt() }.toList()
                if (numbers.isNotEmpty()) {
                    val year = numbers.firstOrNull { it in 1300..1500 }
                    val day = numbers.firstOrNull { it in 1..31 }
                    if (year != null && day != null) {
                        return Triple(year, i + 1, day)
                    }
                }
            }
        }

        // Split by delimiter '/', '-', '.', or space
        val datePart = clean.substringBefore(" ").substringBefore("T")
        val tokens = datePart.split(Regex("[/\\-\\.]")).filter { it.isNotBlank() }
        if (tokens.size >= 3) {
            val p0 = tokens[0].toIntOrNull() ?: return null
            val p1 = tokens[1].toIntOrNull() ?: return null
            val p2 = tokens[2].substring(0, minOf(2, tokens[2].length)).toIntOrNull() ?: return null

            if (p0 in 1300..1500) {
                // Already Jalali!
                return Triple(p0, p1, p2)
            } else if (p0 in 1900..2200) {
                // Gregorian date: convert to Jalali
                return gregorianToJalali(p0, p1, p2)
            }
        }
        return null
    }

    /**
     * Formats any date string into the requested standard Shamsi format:
     * e.g. "1405/06/20"
     */
    fun formatToShamsiDate(rawDate: String?, fallbackDays: Int? = null): String {
        val parsed = parseDateToJalali(rawDate)
        if (parsed != null) {
            val (jy, jm, jd) = parsed
            if (jy >= 1480 || jy >= 2090) {
                return "نامحدود"
            }
            return String.format(Locale.US, "%04d/%02d/%02d", jy, jm, jd)
        }

        // If rawDate could not be parsed but fallbackDays is available
        if (fallbackDays != null && fallbackDays > 0) {
            return getExpiryDateShamsiFormatted(fallbackDays)
        }

        return rawDate ?: "نامحدود"
    }

    /**
     * Backwards-compatible parser: returns standard format "1405/06/20"
     */
    fun parseGregorianToShamsi(dateStr: String?): String {
        return formatToShamsiDate(dateStr)
    }

    /**
     * Calculates the Solar Hijri date for N days from today and formats as "1405/06/20".
     */
    fun getExpiryDateShamsiFormatted(daysFromNow: Int): String {
        if (daysFromNow >= 900) return "نامحدود"
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, daysFromNow)
        val (jy, jm, jd) = gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        return String.format(Locale.US, "%04d/%02d/%02d", jy, jm, jd)
    }

    /**
     * Accurately calculates remaining days between today and the target date.
     * Handles Shamsi dates ("1405/06/20"), Gregorian ("2026-09-11"), and timestamps.
     */
    fun calculateRemainingDays(finishDateStr: String?, fallbackDays: Int? = null): Int {
        if (finishDateStr.isNullOrBlank()) {
            return fallbackDays ?: 30
        }

        val parsed = parseDateToJalali(finishDateStr)
        if (parsed == null) {
            return fallbackDays ?: 30
        }

        val (jy, jm, jd) = parsed
        // If first-connection placeholder or unlimited
        if (jy >= 1480 || jy >= 2090) {
            return fallbackDays ?: 999
        }

        val (gy, gm, gd) = jalaliToGregorian(jy, jm, jd)

        // Today at 00:00:00
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Target day at 00:00:00
        val targetCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, gy)
            set(Calendar.MONTH, gm - 1)
            set(Calendar.DAY_OF_MONTH, gd)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffMillis = targetCal.timeInMillis - todayCal.timeInMillis
        val diffDays = (diffMillis / (1000L * 60L * 60L * 24L)).toInt()

        return if (diffDays < 0) {
            0 // Expired in the past
        } else if (diffDays == 0) {
            // Expires today: still active until end of today
            1
        } else {
            diffDays
        }
    }

    /**
     * Normalizes Persian/Arabic digits to ASCII 0-9 for reliable parsing.
     */
    fun toEnglishDigits(input: String): String {
        return input
            .replace('۰', '0').replace('۱', '1').replace('۲', '2')
            .replace('۳', '3').replace('۴', '4').replace('۵', '5')
            .replace('۶', '6').replace('۷', '7').replace('۸', '8')
            .replace('۹', '9')
            .replace('٠', '0').replace('١', '1').replace('٢', '2')
            .replace('٣', '3').replace('٤', '4').replace('٥', '5')
            .replace('٦', '6').replace('٧', '7').replace('٨', '8')
            .replace('٩', '9')
    }

    /**
     * Formats numbers to Persian/Farsi digits for a native look.
     */
    fun toPersianDigits(input: String): String {
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val sb = StringBuilder()
        for (c in input) {
            if (c in '0'..'9') {
                sb.append(persianDigits[c - '0'])
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }
}
