package com.asad.clickerp

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    // لیستی که تغییراتش باعث آپدیت شدن UI می‌شود
    val logs = mutableStateListOf<String>()
    // متغیر جدید برای وضعیت اتصال سرویس
    var isServiceConnected = mutableStateOf(false)
    // فرمت ساعت: ساعت:دقیقه:ثانیه
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(message: String) {
        val time = timeFormat.format(Date())
        // پیام جدید به بالای لیست اضافه می‌شود
        logs.add(0, "[$time] $message")

        // محدود کردن تعداد لاگ‌ها برای جلوگیری از پر شدن حافظه (مثلا ۱۰۰ تا)
        if (logs.size > 100) {
            logs.removeLast()
        }
    }

    fun clear() {
        logs.clear()
    }
}