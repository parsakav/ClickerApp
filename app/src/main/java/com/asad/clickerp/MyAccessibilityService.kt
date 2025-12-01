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

    // --- لیست سیاه (سایت‌هایی که نباید کلیک شوند) ---
    private val excludedDomains = listOf(
        "emdad-khodro-esfahan",
        "emdadkhodro-bushehr",
        "emdad-khodro-esfahan.ir",
        "emdadkhodro-bushehr.com"
    )

    // کلمات بولدوزر (رد کردن پاپ‌آپ)
    private val bullDozerKeywords = listOf(
        "See results closer to you", "Use precise location", "Not now", "No thanks", "Stay signed out", "Dismiss",
        "نتایج نزدیک‌تر را ببینید", "استفاده از مکان دقیق", "اکنون نه", "نه، متشکرم", "خیر", "بعداً",
        "Accept", "Agree", "Got it", "Allow", "Close", "Deny", "Don't allow",
        "قبول", "تایید", "بستن", "رد کردن"
    )

    // هدرهای هدف
    private val sponsoredHeaders = listOf(
        "Sponsored results", "Sponsored", "Sponsored result",
        "نتایج حامی مالی", "نتایج تبلیغاتی", "آگهی‌ها", "آگهی"
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

        logAndToast("✅ سرویس متصل شد. فیلترینگ سایت‌های ممنوعه فعال است.")
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

                        logAndToast("⏳ اسکن هوشمند و فیلترینگ...")
                        delay(5000)

                        // 2. اجرای کلیک هوشمند با فیلتر لیست سیاه
                        if (Prefs.isBotActive(this@MyAccessibilityService)) {
                            clickFirstValidAd(pageDelaySeconds)
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

    // --- تابع کلیک هوشمند با فیلتر ---
    private suspend fun clickFirstValidAd(stayOnPageTime: Int) {
        var attempts = 0
        val maxAttempts = 6

        Logger.log("🛡️ شروع اسکن با فیلتر لیست سیاه...")

        while (isRunning && Prefs.isBotActive(this) && attempts < maxAttempts) {
            delay(2500)

            // مدیریت پاپ‌آپ‌ها
            if (checkAndClearPopups()) {
                Logger.log("🧹 مانع حذف شد.")
                delay(2000)
            }

            val root = rootInActiveWindow
            if (root == null) {
                Logger.log("❌ صفحه قطع شد.")
                break
            }

            val headerNode = findHeaderNode(root)

            if (headerNode != null) {
                val headerRect = Rect()
                headerNode.getBoundsInScreen(headerRect)
                Logger.log("📍 هدر پیدا شد.")

                // پیدا کردن تمام کاندیداهای زیر هدر
                val candidates = findAllTargetsBelow(root, headerRect.bottom)
                Logger.log("🔍 ${candidates.size} تبلیغ پیدا شد. بررسی لیست سیاه...")

                var targetFound = false

                // بررسی تک تک کاندیداها
                for (node in candidates) {
                    // چک کردن آیا این نود شامل کلمات ممنوعه است؟
                    if (isNodeBlacklisted(node)) {
                        Logger.log("⛔ تبلیغ ممنوعه شناسایی شد! رد کردن...")
                        continue // برو سراغ بعدی
                    }

                    // اگر ممنوع نبود، کلیک کن
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    Logger.log("✅ هدف مجاز تایید شد. شلیک به (${rect.centerX()}, ${rect.centerY()})...")

                    performTap(rect.centerX().toFloat(), rect.centerY().toFloat())
                    targetFound = true

                    // بررسی موفقیت کلیک
                    Logger.log("⏳ بررسی ورود به سایت...")
                    delay(4000)

                    // اگر هدر غیب شد، یعنی وارد شدیم
                    val checkRoot = rootInActiveWindow
                    if (checkRoot != null && findHeaderNode(checkRoot) == null) {
                        Logger.log("🚀 ورود موفق! توقف $stayOnPageTime ثانیه...")
                        handleSiteVisit(stayOnPageTime)
                        return // پایان موفقیت آمیز
                    } else {
                        Logger.log("⚠️ کلیک عمل نکرد. تلاش مجدد...")
                    }
                    break // از حلقه کاندیداها بیا بیرون تا دوباره اسکن کنیم (شاید صفحه جابجا شده)
                }

                if (!targetFound) {
                    Logger.log("⚠️ هیچ تبلیغ مجازی پیدا نشد (یا همه ممنوعه بودند). اسکرول...")
                    performSwipe(screenWidth/2f, screenHeight*0.8f, screenWidth/2f, screenHeight*0.4f, 800)
                }

            } else {
                Logger.log("⬇️ هدر دیده نمی‌شود. اسکرول...")
                performSwipe(screenWidth/2f, screenHeight*0.8f, screenWidth/2f, screenHeight*0.2f, 1000)
                attempts++
            }
        }
        Logger.log("🏁 عملیات بدون نتیجه پایان یافت.")
    }

    // --- تابع بازگشتی برای چک کردن کلمات ممنوعه داخل یک نود ---
    private fun isNodeBlacklisted(node: AccessibilityNodeInfo): Boolean {
        // صف برای پیمایش تمام فرزندان نود
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)

        var counter = 0
        while (!queue.isEmpty() && counter < 100) { // محدودیت برای جلوگیری از هنگ
            val current = queue.removeFirst()
            counter++

            val text = current.text?.toString()?.lowercase() ?: ""
            val desc = current.contentDescription?.toString()?.lowercase() ?: ""
            val combined = "$text $desc"

            for (badWord in excludedDomains) {
                if (combined.contains(badWord.lowercase())) {
                    Logger.log("🚫 کلمه ممنوعه یافت شد: '$badWord' در متن: '${combined.take(20)}...'")
                    return true
                }
            }

            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    // پیدا کردن تمام اهداف زیر هدر
    private fun findAllTargetsBelow(root: AccessibilityNodeInfo, headerBottomY: Int): List<AccessibilityNodeInfo> {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (!queue.isEmpty()) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser) {
                val rect = Rect()
                node.getBoundsInScreen(rect)

                // شرط مکانی: زیر هدر باشد (تا 1000 پیکسل)
                if (rect.top > headerBottomY && rect.top < headerBottomY + 1000) {
                    // شرط ابعاد: ارتفاع بیشتر از 40 پیکسل
                    if (rect.height() > 40 && rect.width() > 100) {
                        // شرط: یا قابل کلیک باشد یا متن داشته باشد (چون ممکنه کانتینر متن باشه)
                        candidates.add(node)
                    }
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        // فیلتر کردن: فقط آنهایی که "فرزندان" نودهای دیگر نیستند (ریشه‌های کارت)
        // برای سادگی، بر اساس Y مرتب میکنیم. اولین مورد، بالاترین کارت است.
        candidates.sortBy {
            val r = Rect()
            it.getBoundsInScreen(r)
            r.top
        }

        // ما فقط موارد سطح بالا را میخواهیم. یک فیلتر ساده: اگر فاصله Y دو آیتم خیلی کم بود، تکراری حساب کن
        val distinctCandidates = mutableListOf<AccessibilityNodeInfo>()
        var lastY = -100
        for (cand in candidates) {
            val r = Rect()
            cand.getBoundsInScreen(r)
            if (r.top - lastY > 50) { // حداقل 50 پیکسل فاصله با قبلی
                distinctCandidates.add(cand)
                lastY = r.top
            }
        }

        return distinctCandidates
    }

    // عملیات داخل سایت
    private suspend fun handleSiteVisit(stayOnPageTime: Int) {
        Logger.log("⏳ توقف در سایت ($stayOnPageTime ثانیه)...")
        delay(stayOnPageTime * 1000L)
        performSwipe(screenWidth/2f, screenHeight*0.8f, screenWidth/2f, screenHeight*0.4f, 1000)
        delay(2000)
        Logger.log("👋 بازگشت...")
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun checkAndClearPopups(): Boolean {
        val root = rootInActiveWindow ?: return false
        for (keyword in bullDozerKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isVisibleToUser) {
                        Logger.log("⚠️ حذف مانع: '${node.text}'")
                        performClickNodeOrTap(node)
                        return true
                    }
                }
            }
        }
        return false
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

        checkAndClearPopups()
        delay(1000)

        val menuNode = findNodeByContentDescription("More options") ?: findNodeByID("com.android.chrome:id/menu_button")
        if (menuNode != null) {
            performClickNodeOrTap(menuNode)
            delay(1500)
            val incognitoNode = findNodeByText("New Incognito tab") ?: findNodeByText("زبانه ناشناس جدید")
            if (incognitoNode != null) {
                performClickNodeOrTap(incognitoNode)
                delay(4000)
                checkAndClearPopups()

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
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
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
        if (x < 0 || y < 0 || x > screenWidth || y > screenHeight) return
        val path = Path()
        path.moveTo(x, y)
        path.lineTo(x, y)
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 150)).build()
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