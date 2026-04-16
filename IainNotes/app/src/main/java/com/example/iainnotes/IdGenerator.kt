package com.example.iainnotes

import java.time.LocalDateTime

object IdGenerator {

    fun makeId(): String {
        val now = LocalDateTime.now()
        val millis = now.nano / 1_000_000

        val datePart = "%04d%02d%02d".format(now.year, now.monthValue, now.dayOfMonth)
        val timePart = "%02d%02d%02d".format(now.hour, now.minute, now.second)
        val millisPart = "%03d".format(millis)

        val raw = "$datePart$timePart$millisPart"
        return raw.toLong().toString(16).uppercase()
    }

    fun decodeId(hex: String): String? {
        return try {
            val raw = hex.toLong(16).toString().padStart(17, '0')
            val year  = raw.substring(0, 4)
            val month = raw.substring(4, 6)
            val day   = raw.substring(6, 8)
            val hour  = raw.substring(8, 10)
            val min   = raw.substring(10, 12)
            val sec   = raw.substring(12, 14)
            "$year$month$day-$hour$min$sec"
        } catch (e: Exception) {
            null
        }
    }
}