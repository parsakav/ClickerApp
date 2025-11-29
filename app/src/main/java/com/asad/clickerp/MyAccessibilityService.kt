package com.asad.clickerp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.*
import java.net.URL
import java.nio.charset.Charset

class MyAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var isRunning = false
    private var screenWidth = 0
    private var screenHeight = 0
    private var lastKnownIp: String = "نامشخص"

    // لیست کلمات برای رد کردن موانع
    private val bullDozerKeywords = listOf(
        "Accept", "Agree", "Continue", "Next", "Got it", "Allow", "While using the app", "Only this time", "Use precise location", "Yes, I'm in", "Ok",
        "No thanks", "Not now", "Dismiss", "Close", "Deny", "Don't allow",
        "قبول", "تایید", "ادامه", "بعدی", "متوجه شدم", "مجاز است", "هنگام استفاده", "فقط این بار", "بله", "باشه",
        "خیر", "نه", "اکنون نه", "بعداً", "رد کردن", "بستن", "اجازه نده"
    )

    // 1. لیست هدرهای بخش تبلیغات (طبق تصویر شما)
    private val sponsoredHeaders = listOf(
        "Sponsored results",
        "نتایج حامی مالی",
        "نتایج تبلیغاتی",
        "آگهی‌ها",
        "Ads"
    )

    // 2. لیست کلمات تکی برای تشخیص تبلیغ (روش قبلی)
    private val adLabelKeywords = listOf(
        "Sponsored", "Ad", "Ads",
        "آگهی", "تبلیغ", "اسپانسر"
    )

    private val closeAllTexts = listOf("Close all", "Clear all", "بستن همه", "پاکسازی", "حذف همه", "بستن همه برنامه‌ها")

    override fun onServiceConnected() {
        super.onServiceConnected()
        Logger.isServiceConnected.value = true

        val wm = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        logAndToast("✅ سرویس متصل شد. آماده شکار.")
        startAutomationLoop()
    }

    private fun startAutomationLoop() {
        if (isRunning) return
        isRunning = true

        serviceScope.launch {
            Logger.log("🔄 موتور ربات روشن شد.")

            while (isRunning) {
                if (!Prefs.isBotActive(this@MyAccessibilityService)) {
                    delay(3000)
                    continue
                }

                val userQuery = Prefs.getSearchQuery(this@MyAccessibilityService)
                val pageDelaySeconds = Prefs.getPageLoadDelay(this@MyAccessibilityService)

                logAndToast("🚀 شروع عملیات: $userQuery")

                try {
                    // 1. باز کردن کروم و سرچ
                    if (performFullIncognitoSearch(userQuery)) {

                        logAndToast("⏳ تحلیل نتایج جستجو...")
                        delay(5000)

                        // 2. کلیک هوشمند روی تبلیغات (زیر هدر یا دارای لیبل)
                        if (Prefs.isBotActive(this@MyAccessibilityService)) {
                            clickHeaderBasedAds(pageDelaySeconds)
                        }

                    } else {
                        Logger.log("⚠️ خطا در باز کردن مرورگر.")
                    }

                    if (!Prefs.isBotActive(this@MyAccessibilityService)) continue

                    logAndToast("❌ پایان سیکل. بستن برنامه...")
                    closeChromeForcefully()
                    delay(3000)

                    if (Prefs.isBotActive(this@MyAccessibilityService)) {
                        logAndToast("✈️ تغییر IP...")
                        ensureIpChange()
                    }

                } catch (e: Exception) {
                    Logger.log("⚠️ خطا: ${e.message}")
                    e.printStackTrace()
                    delay(5000)
                }
            }
        }
    }

    // --- تابع اصلی شکار تبلیغات ---
    private suspend fun clickHeaderBasedAds(stayOnPageTime: Int) {
        var currentAdIndex = 0
        var scrollAttempts = 0
        val maxScrolls = 7

        Logger.log("💰 شروع اسکن ساختاری (Header Scan)...")

        while (isRunning && Prefs.isBotActive(this) && scrollAttempts < maxScrolls) {
            delay(2500)
            val root = rootInActiveWindow
            if (root == null) {
                Logger.log("❌ صفحه در دسترس نیست.")
                break
            }

            // 1. پیدا کردن تبلیغات با الگوریتم ترکیبی (هدر + لیبل)
            val detectedAds = scanForAdsRecursive(root)

            // حذف موارد تکراری (ممکن است یک نود هم لیبل داشته باشد هم زیر هدر باشد)
            val uniqueAds = detectedAds.distinct()

            Logger.log("🔍 در این نما ${uniqueAds.size} تبلیغ شناسایی شد.")

            if (currentAdIndex < uniqueAds.size) {
                val targetNode = uniqueAds[currentAdIndex]
                Logger.log("🎯 هدف‌گیری تبلیغ #${currentAdIndex + 1}...")

                if (performClickOnAd(targetNode)) {
                    Logger.log("✅ کلیک موفق. مشاهده سایت...")

                    // شبیه‌سازی رفتار کاربر
                    delay((stayOnPageTime * 1000L) / 3)
                    performSwipe(screenWidth/2f, screenHeight*0.8f, screenWidth/2f, screenHeight*0.4f, 700)
                    delay((stayOnPageTime * 1000L) / 3)

                    Logger.log("🔙 بازگشت...")
                    performGlobalAction(GLOBAL_ACTION_BACK)

                    currentAdIndex++
                } else {
                    Logger.log("⚠️ کلیک ناموفق. بعدی...")
                    currentAdIndex++
                }
            } else {
                Logger.log("⬇️ اسکرول برای یافتن هدرهای بیشتر...")
                performSwipe(screenWidth/2f, screenHeight*0.8f, screenWidth/2f, screenHeight*0.2f, 1000)
                delay(3000)
                currentAdIndex = 0
                scrollAttempts++
            }
        }
    }

    /**
     * الگوریتم اسکن درختی:
     * کل درخت صفحه را پیمایش می‌کند.
     * اگر به هدری مثل "Sponsored results" برسد، متغیر huntingMode فعال می‌شود.
     * وقتی huntingMode فعال است، ۳ نود قابل کلیک بعدی را به عنوان تبلیغ ذخیره می‌کند.
     */
    private fun scanForAdsRecursive(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val foundAds = mutableListOf<AccessibilityNodeInfo>()

        // وضعیت شکار: آیا ما الان زیر یک هدر تبلیغاتی هستیم؟
        var huntingLimit = 0

        fun traverse(node: AccessibilityNodeInfo) {
            if (!node.isVisibleToUser) return

            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val allText = "$text $desc"

            // الف) بررسی آیا این نود، هدر است؟
            // (مثل تصویر شما: "Sponsored results")
            for (header in sponsoredHeaders) {
                if (text.equals(header, ignoreCase = true) || desc.equals(header, ignoreCase = true)) {
                    Logger.log("🚩 هدر تبلیغاتی پیدا شد: $header")
                    huntingLimit = 3 // شکار ۳ آیتم بعدی را فعال کن
                    return // خود هدر قابل کلیک نیست، برو بعدی
                }
            }

            // ب) اگر در حالت شکار هستیم، این نود را بگیر
            if (huntingLimit > 0) {
                if (node.isClickable) {
                    Logger.log("🔥 شکار لینک زیر هدر: ${node.className}")
                    foundAds.add(node)
                    huntingLimit--
                    return // نود را گرفتیم، نرو داخل فرزندانش (جلوگیری از کلیک تکراری روی اجزای داخلی)
                } else {
                    // اگر خود نود کلیک نمی‌شود، شاید والد قابل کلیک دارد که در پیمایش قبلی رد شده؟
                    // اینجا فقط ادامه می‌دهیم تا فرزند قابل کلیک پیدا شود
                }
            }

            // ج) روش سنتی: بررسی لیبل مستقیم (Ad) برای اطمینان
            // (اگر هدر پیدا نشد ولی آیتم تکی وجود داشت)
            if (huntingLimit == 0) { // فقط اگر در حالت شکار نیستیم چک کن (که تکراری نشود)
                for (keyword in adLabelKeywords) {
                    val isExact = text.equals(keyword, ignoreCase = true)
                    val isStart = text.startsWith("$keyword ", ignoreCase = true) || text.startsWith("$keyword:", ignoreCase = true)

                    if (text.length < 20 && (isExact || isStart)) {
                        // این یک لیبل است. باید والد قابل کلیکش را پیدا کنیم
                        val clickableParent = findClickableAncestor(node, 5)
                        if (clickableParent != null) {
                            foundAds.add(clickableParent)
                            return
                        }
                    }
                }
            }

            // ادامه پیمایش درخت
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    traverse(child)
                }
            }
        }

        traverse(root)
        return foundAds
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo, maxLevels: Int): AccessibilityNodeInfo? {
        var current = node
        repeat(maxLevels) {
            val parent = current.parent ?: return null
            if (parent.isClickable) return parent
            current = parent
        }
        return current
    }

    private fun performClickOnAd(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        return false
    }

    // --- توابع استاندارد (بدون تغییر) ---
    private suspend fun performFullIncognitoSearch(query: String): Boolean {
        performGlobalAction(GLOBAL_ACTION_HOME)
        delay(1500)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
        intent.setPackage("com.android.chrome")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try { startActivity(intent) } catch (e: Exception) { return false }
        delay(6000)
        repeat(2) { if (clearAllPopups()) delay(1500) }

        val menuNode = findNodeByContentDescription("More options") ?: findNodeByID("com.android.chrome:id/menu_button")
        if (menuNode != null && clickNode(menuNode)) {
            delay(1500)
            val incognitoNode = findNodeByText("New Incognito tab") ?: findNodeByText("زبانه ناشناس جدید")
            if (incognitoNode != null && clickNode(incognitoNode)) {
                delay(4000)
                clearAllPopups()
                val urlBar = findNodeByID("com.android.chrome:id/search_box_text") ?: findNodeByID("com.android.chrome:id/url_bar")
                if (urlBar != null) {
                    clickNode(urlBar)
                    delay(1000)
                    val args = Bundle()
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
                    urlBar.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    delay(2000)
                    val sug = findNodeByID("com.android.chrome:id/line_1")
                    if (sug != null) clickNode(sug)
                    else performTap((screenWidth * 0.9).toFloat(), (screenHeight * 0.9).toFloat())
                    return true
                }
            }
        } else {
            val urlBar = findNodeByID("com.android.chrome:id/search_box_text") ?: findNodeByID("com.android.chrome:id/url_bar")
            if (urlBar != null) {
                clickNode(urlBar)
                delay(1000)
                val args = Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
                urlBar.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                delay(1000)
                val sug = findNodeByID("com.android.chrome:id/line_1")
                if (sug != null) clickNode(sug)
                return true
            }
        }
        return false
    }

    private suspend fun clearAllPopups(): Boolean {
        val root = rootInActiveWindow ?: return false
        for (keyword in bullDozerKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isVisibleToUser && node.isEnabled) {
                        if (clickNode(node)) return true
                    }
                }
            }
        }
        return false
    }

    private suspend fun closeChromeForcefully() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        delay(3000)
        val root = rootInActiveWindow
        if (root != null) {
            for (text in closeAllTexts) {
                val found = root.findAccessibilityNodeInfosByText(text)
                if (!found.isNullOrEmpty()) {
                    performClickNode(found[0])
                    delay(1000)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }
            }
        }
        val dir = Prefs.getRecentsSwipeDir(this)
        val cx = screenWidth / 2f
        val cy = screenHeight / 2f
        val (x1, y1, x2, y2) = when (dir) {
            Prefs.SWIPE_UP -> listOf(cx, screenHeight * 0.85f, cx, screenHeight * 0.15f)
            Prefs.SWIPE_RIGHT -> listOf(screenWidth * 0.1f, cy, screenWidth * 0.9f, cy)
            Prefs.SWIPE_LEFT -> listOf(screenWidth * 0.9f, cy, screenWidth * 0.1f, cy)
            else -> listOf(cx, screenHeight * 0.8f, cx, screenHeight * 0.2f)
        }
        performSwipe(x1, y1, x2, y2, 300)
        delay(500)
        performSwipe(x1, y1, x2, y2, 300)
        delay(1000)
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private suspend fun ensureIpChange() {
        var ipChanged = false
        var retryCount = 0
        while (!ipChanged && Prefs.isBotActive(this)) {
            performAirplaneToggleSequence()
            if (!Prefs.isBotActive(this)) break
            Logger.log("⏳ چک کردن IP...")
            delay(12000)
            val newIp = getPublicIp()
            if (newIp != null && newIp != lastKnownIp) {
                logAndToast("IP جدید: $newIp")
                lastKnownIp = newIp
                ipChanged = true
            } else {
                retryCount++
                if (retryCount > 3) break
            }
        }
    }

    private suspend fun performAirplaneToggleSequence() {
        val userDuration = Prefs.getAirplaneDuration(this)
        performOpenNotificationPanel()
        delay(2500)
        var airplaneNode = findAirplaneModeButton()
        if (airplaneNode == null) {
            performSwipe((screenWidth * 0.9f), (screenHeight * 0.5f), (screenWidth * 0.1f), (screenHeight * 0.5f), 500)
            delay(1500)
            airplaneNode = findAirplaneModeButton()
        }
        airplaneNode?.let { node ->
            performClickNode(node)
            delay(userDuration * 1000L)
            performClickNode(node)
            delay(2000)
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(500)
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun performOpenNotificationPanel() {
        val gravity = Prefs.getPanelGravity(this)
        val startX = when (gravity) {
            Prefs.GRAVITY_RIGHT -> screenWidth * 0.9f
            Prefs.GRAVITY_LEFT -> screenWidth * 0.1f
            else -> screenWidth / 2f
        }
        performSwipe(startX, 10f, startX, screenHeight * 0.6f, 400)
    }

    private suspend fun getPublicIp(): String? {
        return withContext(Dispatchers.IO) {
            try { URL("https://api.ipify.org").readText(Charset.defaultCharset()) } catch (e: Exception) { null }
        }
    }

    private fun findAirplaneModeButton(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val targets = listOf("Airplane", "Flight", "حالت هواپیما", "حالت پرواز", "پرواز")
        for (text in targets) {
            val list = root.findAccessibilityNodeInfosByText(text)
            if (!list.isNullOrEmpty()) return list[0]
        }
        return null
    }

    private fun findNodeByID(id: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val list = root.findAccessibilityNodeInfosByViewId(id)
        return list.firstOrNull()
    }

    private fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val list = root.findAccessibilityNodeInfosByText(text)
        return list.firstOrNull { it.isVisibleToUser } ?: list.firstOrNull()
    }

    private fun findNodeByContentDescription(desc: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findNodeRecursive(root, desc)
    }
    private fun findNodeRecursive(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (node.contentDescription != null && node.contentDescription.toString().contains(desc, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeRecursive(child, desc)
            if (result != null) return result
        }
        return null
    }
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }
    private fun performClickNode(node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        performTap(rect.centerX().toFloat(), rect.centerY().toFloat())
    }
    private fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build()
        dispatchGesture(gesture, null, null)
    }
    private fun performTap(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        path.lineTo(x, y)
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 100)).build()
        dispatchGesture(gesture, null, null)
    }
    private fun logAndToast(msg: String) {
        Logger.log(msg)
        Handler(Looper.getMainLooper()).post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }
    override fun onDestroy() {
        super.onDestroy()
        Logger.isServiceConnected.value = false
        isRunning = false
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { isRunning = false; serviceScope.cancel() }
}