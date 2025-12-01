package com.asad.clickerp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.asad.clickerp.ui.theme.ClickerPTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClickerPTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SettingsScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isBotActive by remember { mutableStateOf(Prefs.isBotActive(context)) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // چک کردن مجدد مجوزها وقتی کاربر به برنامه برمیگردد
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var selectedPanelGravity by remember { mutableIntStateOf(Prefs.getPanelGravity(context)) }
    var selectedSwipeDir by remember { mutableIntStateOf(Prefs.getRecentsSwipeDir(context)) }
    var airplaneDurationText by remember { mutableStateOf(Prefs.getAirplaneDuration(context).toString()) }
    var pageLoadDelayText by remember { mutableStateOf(Prefs.getPageLoadDelay(context).toString()) }

    // خواندن مقدار ذخیره شده فعلی
    var searchQuery by remember { mutableStateOf(Prefs.getSearchQuery(context)) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("کنترل پنل ربات", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // --- بررسی مجوز حیاتی نمایش روی صفحه ---
        if (!hasOverlayPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⚠️ مجوز حیاتی!", color = Color.Red, fontWeight = FontWeight.Bold)
                    Text("برای اینکه ربات بتواند کروم را باز کند، باید مجوز 'نمایش روی برنامه‌های دیگر' را بدهید.", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("فعال‌سازی مجوز Overlay")
                    }
                }
            }
        }

        // --- وضعیت سرویس ---
        Card(
            colors = CardDefaults.cardColors(containerColor = if (Logger.isServiceConnected.value) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (Logger.isServiceConnected.value) "✅ سرویس متصل است" else "❌ سرویس قطع است",
                    color = if (Logger.isServiceConnected.value) Color(0xFF2E7D32) else Color(0xFFC62828),
                    fontWeight = FontWeight.Bold
                )
                if (!Logger.isServiceConnected.value) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("روشن کردن سرویس") }
                }
            }
        }

        // دکمه استارت/استاپ
        Button(
            onClick = {
                if (!hasOverlayPermission) {
                    Toast.makeText(context, "ابتدا مجوز نمایش روی صفحه را بدهید!", Toast.LENGTH_LONG).show()
                } else {
                    isBotActive = !isBotActive
                    Prefs.setBotActive(context, isBotActive)
                    Logger.log(if (isBotActive) "🟢 دکمه شروع زده شد." else "🔴 دکمه توقف زده شد.")
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = if (isBotActive) Color(0xFF4CAF50) else Color(0xFFF44336)),
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Text(text = if (isBotActive) "✅ ربات روشن است" else "⛔ ربات متوقف است", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- کنسول لاگ ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF212121)),
            modifier = Modifier.fillMaxWidth().height(250.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📜 گزارش عملکرد", color = Color.White, fontWeight = FontWeight.Bold)
                    Row {
                        TextButton(onClick = { copyLogsToClipboard(context) }) { Text("📋 کپی", color = Color(0xFF64B5F6)) }
                        TextButton(onClick = { Logger.clear() }) { Text("🗑️ پاک", color = Color(0xFFFF9800)) }
                    }
                }
                Divider(color = Color.Gray)
                LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
                    items(Logger.logs) { log ->
                        Text(text = log, color = Color(0xFF00E676), fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- تنظیمات پیشرفته ---
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
            Text("⚙️ تنظیمات پیشرفته", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            // --- بخش تغییر یافته: انتخاب عبارت جستجو ---
            Text("🔍 عبارت جستجو:", fontWeight = FontWeight.Bold)

            val searchOptions = listOf("امداد خودرو اصفهان فوری", "امداد خودرو فوری اصفهان", "امداد خودرو اصفهان")
            // پیدا کردن ایندکس گزینه انتخاب شده (اگر متن ذخیره شده در لیست نبود، پیش‌فرض اولی انتخاب می‌شود)
            val currentSearchIndex = searchOptions.indexOf(searchQuery).let { if (it == -1) 0 else it }

            RadioOptions(
                options = searchOptions,
                selectedIndex = currentSearchIndex,
                onSelect = { index ->
                    val selected = searchOptions[index]
                    searchQuery = selected
                    Prefs.setSearchQuery(context, selected)
                }
            )
            // ------------------------------------------

            Spacer(modifier = Modifier.height(16.dp))

            Text("⏳ صبر برای لود سایت (ثانیه):", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = pageLoadDelayText,
                onValueChange = { newText -> if (newText.all { it.isDigit() }) { pageLoadDelayText = newText; val s = newText.toIntOrNull() ?: 20; Prefs.setPageLoadDelay(context, s) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("✈️ مکث حالت پرواز (ثانیه):", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = airplaneDurationText,
                onValueChange = { newText -> if (newText.all { it.isDigit() }) { airplaneDurationText = newText; val s = newText.toIntOrNull() ?: 15; Prefs.setAirplaneDuration(context, s) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text("۱. محل باز شدن پنل:", fontWeight = FontWeight.Bold)
            RadioOptions(
                options = listOf("گوشه راست (شیائومی)", "وسط (سامسونگ)", "گوشه چپ"),
                selectedIndex = selectedPanelGravity,
                onSelect = { selectedPanelGravity = it; Prefs.setPanelGravity(context, it) }
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("۲. جهت بستن برنامه:", fontWeight = FontWeight.Bold)
            RadioOptions(
                options = listOf("کشیدن به بالا", "کشیدن به راست", "کشیدن به چپ"),
                selectedIndex = selectedSwipeDir,
                onSelect = { selectedSwipeDir = it; Prefs.setRecentsSwipeDir(context, it) }
            )
        }
        Spacer(modifier = Modifier.height(50.dp))
    }
}

@Composable
fun RadioOptions(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Column {
        options.forEachIndexed { index, text ->
            Row(modifier = Modifier.fillMaxWidth().selectable(selected = (index == selectedIndex), onClick = { onSelect(index) }), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = (index == selectedIndex), onClick = { onSelect(index) })
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

fun copyLogsToClipboard(context: Context) {
    if (Logger.logs.isEmpty()) { Toast.makeText(context, "لیست خالی است!", Toast.LENGTH_SHORT).show(); return }
    val allLogs = Logger.logs.joinToString(separator = "\n")
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Bot Logs", allLogs)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "✅ کپی شد", Toast.LENGTH_SHORT).show()
}