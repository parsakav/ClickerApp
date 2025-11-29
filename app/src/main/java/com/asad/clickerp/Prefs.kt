package com.asad.clickerp

import android.content.Context

object Prefs {
    private const val PREF_NAME = "app_settings"

    // کلیدهای ذخیره‌سازی
    private const val KEY_IS_BOT_ACTIVE = "is_bot_active"
    private const val KEY_PANEL_GRAVITY = "panel_gravity"
    private const val KEY_RECENTS_SWIPE_DIR = "recents_dir"
    private const val KEY_AIRPLANE_DURATION = "airplane_duration"

    // 🆕 کلید جدید برای متن جستجو
    private const val KEY_SEARCH_QUERY = "search_query"

    // ثوابت
    const val GRAVITY_RIGHT = 0
    const val GRAVITY_CENTER = 1
    const val GRAVITY_LEFT = 2

    const val SWIPE_UP = 0
    const val SWIPE_RIGHT = 1
    const val SWIPE_LEFT = 2

    // --- متن جستجو (جدید) ---
    fun setSearchQuery(context: Context, query: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SEARCH_QUERY, query).apply()
    }

    fun getSearchQuery(context: Context): String {
        // پیش‌فرض: امداد خودرو
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SEARCH_QUERY, "امداد خودرو") ?: "امداد خودرو"
    }

    // --- بقیه متدها (بدون تغییر) ---
    fun setBotActive(context: Context, isActive: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_IS_BOT_ACTIVE, isActive).apply()
    }

    fun isBotActive(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_BOT_ACTIVE, false)
    }

    fun setPanelGravity(context: Context, value: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PANEL_GRAVITY, value).apply()
    }

    fun getPanelGravity(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_PANEL_GRAVITY, GRAVITY_RIGHT)
    }

    fun setRecentsSwipeDir(context: Context, value: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_RECENTS_SWIPE_DIR, value).apply()
    }

    fun getRecentsSwipeDir(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_RECENTS_SWIPE_DIR, SWIPE_UP)
    }

    fun setAirplaneDuration(context: Context, seconds: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_AIRPLANE_DURATION, seconds).apply()
    }
// ... داخل object Prefs ...

    private const val KEY_PAGE_LOAD_DELAY = "page_load_delay" // کلید جدید

    // ذخیره زمان لود صفحه (ثانیه)
    fun setPageLoadDelay(context: Context, seconds: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PAGE_LOAD_DELAY, seconds).apply()
    }

    // خواندن زمان لود صفحه (پیش‌فرض ۲۰ ثانیه)
    fun getPageLoadDelay(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_PAGE_LOAD_DELAY, 20)
    }
    fun getAirplaneDuration(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_AIRPLANE_DURATION, 15)
    }
}