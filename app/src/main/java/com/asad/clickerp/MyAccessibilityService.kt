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

    // کلمات بولدوزر (رد کردن پاپ‌آپ)
    private val bullDozerKeywords = listOf(
        "Accept", "Agree", "Continue", "Next", "Got it", "Allow", "While using the app", "Only this time", "Use precise location", "Yes, I'm in", "Ok",
        "No thanks", "Not now", "Dismiss", "Close", "Deny", "Don't allow",
        "قبول", "تایید", "ادامه", "بعدی", "متوجه شدم", "مجاز است", "هنگام استفاده", "فقط این بار", "بله", "باشه",
        "خیر", "نه", "اکنون نه", "بعداً", "رد کردن", "بستن", "اجازه نده"
    )

    // فقط هدر را لازم داریم تا نقطه شروع آتش را پیدا کنیم
    private val sponsoredHeaders = listOf(
        "Sponsored results", "Sponsored",
        "نتایج حامی مالی", "نتایج تبلیغاتی", "آگهی‌ها"
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

        logAndToast("✅ سرویس متصل شد. حالت رگباری (Blind Sweep) فعال است.")
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
                    // 1. جستجو
                    if (performFullIncognitoSearch(userQuery)) {

                        logAndToast("⏳ موقعیت‌گیری توپخانه...")
                        delay(5000)

                        // 2. اجرای آتش کور زیر هدر
                        if (Prefs.isBotActive(this@MyAccessibilityService)) {
                            performBlindSweep(pageDelaySeconds)
                        }

                    } else {
                        Logger.log("⚠️ خطا در باز کردن مرورگر.")
                    }

                    if (!Prefs.isBotActive(this@MyAccessibilityService)) continue

                    logAndToast("❌ پایان عملیات. بستن مرورگر...")
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

    // --- تابع جدید: شلیک کور به پایین (Pixel Sweep) ---
    private suspend fun performBlindSweep(stayOnPageTime: Int) {
        var scrollAttempts = 0
        val maxScrolls = 5

        Logger.log("💣 شروع جاروی مختصاتی (پیکسل به پیکسل)...")

        while (isRunning && Prefs.isBotActive(this) && scrollAttempts < maxScrolls) {
            delay(3000)
            val root = rootInActiveWindow

            if (root == null) {
                Logger.log("❌ صفحه در دسترس نیست.")
                break
            }

            // 1. فقط هدر را پیدا کن
            val headerNode = findHeaderNode(root)

            if (headerNode != null) {
                val headerRect = Rect()
                headerNode.getBoundsInScreen(headerRect)
                Logger.log("📍 هدر پیدا شد. خط آتش: Y=${headerRect.bottom}")

                val startY = headerRect.bottom + 50 // شروع از 50 پیکسل پایین‌تر از هدر
                val stepY = 120 // فاصله هر شلیک (حدود ارتفاع یک لینک)
                val attempts = 4 // ۴ بار شلیک کن (تا ۴۰۰-۵۰۰ پیکسل پایین‌تر)

                var successfulHit = false

                // حلقه آتش
                for (i in 0 until attempts) {
                    if (!Prefs.isBotActive(this)) break

                    val targetY = startY + (i * stepY)
                    val targetX = screenWidth / 2f // وسط صفحه

                    // اگر از صفحه بیرون زدیم، ادامه نده
                    if (targetY > screenHeight - 100) break

                    Logger.log("💥 شلیک شماره ${i + 1} به مختصات ($targetX, $targetY)...")
                    performTap(targetX, targetY.toFloat())

                    // صبر کن ببینیم اتفاقی میوفته؟
                    Logger.log("⏳ انتظار برای واکنش مرورگر...")
                    delay(2500)

                    // چک کنیم که آیا هنوز در صفحه سرچ هستیم؟
                    // اگر آدرس بار تغییر کرده باشد یا صفحه لود شده باشد، یعنی کلیک گرفته
                    // روش ساده: چک میکنیم آیا هنوز هدر Sponsored results دیده میشه؟
                    val currentRoot = rootInActiveWindow
                    if (currentRoot != null) {
                        val checkHeader = findHeaderNode(currentRoot)
                        if (checkHeader == null) {
                            Logger.log("✅ هدف منهدم شد (هدر دیگر دیده نمی‌شود). ورود به سایت...")
                            successfulHit = true
                            break
                        } else {
                            Logger.log("❌ واکنشی نداشت. شلیک بعدی...")
                        }
                    }
                }

                if (successfulHit) {
                    // صبر برای بازدید سایت
                    delay((stayOnPageTime * 1000L) / 3)
                    performSwipe(screenWidth/2f, screenHeight*0.8f, screenWidth/2f, screenHeight*0.4f, 700)
                    delay((stayOnPageTime * 1000L) / 3)

                    Logger.log("👋 بازگشت...")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    return // پایان موفقیت آمیز
                } else {
                    Logger.log("⚠️ هیچکدام از شلیک‌ها نگرفت. شاید نیاز به اسکرول است.")
                }

            } else {
                Logger.log("⬇️ هدر Sponsored دیده نشد. اسکرول...")
                performSwipe(screenWidth/2f, screenHeight*0.8f, screenWidth/2f, screenHeight*0.2f, 1000)
                delay(3000)
                scrollAttempts++
            }
        }
        Logger.log("🏁 عملیات بدون موفقیت پایان یافت.")
    }

    private fun findHeaderNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (!queue.isEmpty()) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser) {
                val text = node.text?.toString() ?: ""
                val desc = node.contentDescription?.toString() ?: ""
                for (header in sponsoredHeaders) {
                    if (text.equals(header, ignoreCase = true) || desc.equals(header, ignoreCase = true)) {
                        return node
                    }
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    // --- توابع استاندارد ---
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
        if (menuNode != null) {
            performClickNodeOrTap(menuNode)
            delay(1500)
            val incognitoNode = findNodeByText("New Incognito tab") ?: findNodeByText("زبانه ناشناس جدید")
            if (incognitoNode != null) {
                performClickNodeOrTap(incognitoNode)
                delay(4000)
                clearAllPopups()
                val urlBar = findNodeByID("com.android.chrome:id/search_box_text") ?: findNodeByID("com.android.chrome:id/url_bar")
                if (urlBar != null) {
                    performClickNodeOrTap(urlBar)
                    delay(1000)
                    val args = Bundle()
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
                    urlBar.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    delay(2000)
                    val sug = findNodeByID("com.android.chrome:id/line_1")
                    if (sug != null) performClickNodeOrTap(sug)
                    else performTap((screenWidth * 0.9).toFloat(), (screenHeight * 0.9).toFloat())
                    return true
                }
            }
        } else {
            val urlBar = findNodeByID("com.android.chrome:id/search_box_text") ?: findNodeByID("com.android.chrome:id/url_bar")
            if (urlBar != null) {
                performClickNodeOrTap(urlBar)
                delay(1000)
                val args = Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
                urlBar.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                delay(1000)
                val sug = findNodeByID("com.android.chrome:id/line_1")
                if (sug != null) performClickNodeOrTap(sug)
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
                        performClickNodeOrTap(node)
                        return true
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
                    performClickNodeOrTap(found[0])
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
            performClickNodeOrTap(node)
            delay(userDuration * 1000L)
            performClickNodeOrTap(node)
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

    private fun performClickNodeOrTap(node: AccessibilityNodeInfo) {
        if (node.isClickable) {
            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (!clicked) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                performTap(rect.centerX().toFloat(), rect.centerY().toFloat())
            }
        } else {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            performTap(rect.centerX().toFloat(), rect.centerY().toFloat())
        }
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