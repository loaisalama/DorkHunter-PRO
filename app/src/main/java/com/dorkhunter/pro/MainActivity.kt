package com.dorkhunter.pro

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.net.URLEncoder

private val safeFileTypes = listOf("PDF", "DOCX", "XLSX", "CSV", "PPTX", "TXT")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DorkHunterApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DorkHunterApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("dorkhunter", 0) }
    var domain by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var excluded by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("Anywhere") }
    var fileType by remember { mutableStateOf("Any") }
    var engine by remember { mutableStateOf("Google") }
    var showSaved by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(loadList(prefs.getString("saved", "[]") ?: "[]")) }
    var history by remember { mutableStateOf(loadList(prefs.getString("history", "[]") ?: "[]")) }
    var expandedEngine by remember { mutableStateOf(false) }
    var expandedType by remember { mutableStateOf(false) }
    var expandedLocation by remember { mutableStateOf(false) }

    val query = remember(domain, keyword, phrase, excluded, location, fileType) {
        buildString {
            if (domain.isNotBlank()) append("site:${domain.trim()} ")
            if (phrase.isNotBlank()) append("\"${phrase.trim()}\" ")
            if (keyword.isNotBlank()) append(keyword.trim().split(Regex("\\s+")).joinToString(" ") { it } + " ")
            if (excluded.isNotBlank()) append(excluded.trim().split(Regex("\\s+")).joinToString(" ") { "-$it" } + " ")
            when (location) {
                "Page title" -> if (keyword.isNotBlank()) append("intitle:${keyword.trim()} ")
                "URL path" -> if (keyword.isNotBlank()) append("inurl:${keyword.trim()} ")
                "Page text" -> if (keyword.isNotBlank()) append("intext:${keyword.trim()} ")
            }
            if (fileType != "Any") append("filetype:${fileType.lowercase()} ")
        }.trim()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFFFF5252),
            secondary = androidx.compose.ui.graphics.Color(0xFFFFB74D),
            background = androidx.compose.ui.graphics.Color(0xFF101216),
            surface = androidx.compose.ui.graphics.Color(0xFF191D24)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("DORKHUNTER PRO", fontWeight = FontWeight.Black)
                            Text("باحث الدوركات • تدقيق وبحث مسؤول", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        TextButton(onClick = { showSaved = !showSaved }) {
                            Icon(Icons.Default.Bookmark, null)
                            Text(if (showSaved) "المنشئ" else "المحفوظات")
                        }
                    }
                )
            }
        ) { padding ->
            if (showSaved) {
                SavedScreen(
                    items = saved,
                    onOpen = { item ->
                        history = (listOf(item) + history).distinct().take(50)
                        prefs.edit().putString("history", JSONArray(history).toString()).apply()
                        openSearch(context, item, engine)
                    },
                    onDelete = { item ->
                        saved = saved.filterNot { it == item }
                        prefs.edit().putString("saved", JSONArray(saved).toString()).apply()
                    },
                    onBack = { showSaved = false },
                    history = history,
                    onClearHistory = {
                        history = emptyList()
                        prefs.edit().putString("history", "[]").apply()
                    }
                )
            } else {
                Column(
                    modifier = Modifier.padding(padding).fillMaxSize()
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("تنبيه الاستخدام المسؤول", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            Text(
                                "استخدم البحث على نطاقات تملكها أو لديك تصريح بفحصها. هذه الأداة لا تفحص الخوادم ولا تتجاوز صلاحيات الوصول.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    OutlinedTextField(domain, { domain = it }, Modifier.fillMaxWidth(),
                        label = { Text("النطاق المصرّح لك بفحصه (اختياري)") },
                        placeholder = { Text("example.com") }, singleLine = true)
                    OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(),
                        label = { Text("كلمات مفتاحية") }, singleLine = true)
                    OutlinedTextField(phrase, { phrase = it }, Modifier.fillMaxWidth(),
                        label = { Text("عبارة دقيقة") }, singleLine = true)
                    OutlinedTextField(excluded, { excluded = it }, Modifier.fillMaxWidth(),
                        label = { Text("استبعاد كلمات (اختياري)") }, singleLine = true)
                    ExposedDropdownMenuBox(expanded = expandedLocation, onExpandedChange = { expandedLocation = it }) {
                        OutlinedTextField(location, {}, Modifier.menuAnchor().fillMaxWidth(),
                            readOnly = true, label = { Text("مكان البحث") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedLocation) })
                        ExposedDropdownMenu(expanded = expandedLocation, onDismissRequest = { expandedLocation = false }) {
                            listOf("Anywhere", "Page title", "URL path", "Page text").forEach {
                                DropdownMenuItem(text = { Text(locationLabel(it)) }, onClick = { location = it; expandedLocation = false })
                            }
                        }
                    }
                    ExposedDropdownMenuBox(expanded = expandedType, onExpandedChange = { expandedType = it }) {
                        OutlinedTextField(fileType, {}, Modifier.menuAnchor().fillMaxWidth(),
                            readOnly = true, label = { Text("نوع المستند") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedType) })
                        ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                            (listOf("Any") + safeFileTypes).forEach {
                                DropdownMenuItem(text = { Text(it) }, onClick = { fileType = it; expandedType = false })
                            }
                        }
                    }
                    ExposedDropdownMenuBox(expanded = expandedEngine, onExpandedChange = { expandedEngine = it }) {
                        OutlinedTextField(engine, {}, Modifier.menuAnchor().fillMaxWidth(),
                            readOnly = true, label = { Text("محرك البحث") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedEngine) })
                        ExposedDropdownMenu(expanded = expandedEngine, onDismissRequest = { expandedEngine = false }) {
                            listOf("Google", "DuckDuckGo", "Bing", "Startpage").forEach {
                                DropdownMenuItem(text = { Text(it) }, onClick = { engine = it; expandedEngine = false })
                            }
                        }
                    }
                    Text("معاينة الاستعلام", fontWeight = FontWeight.Bold)
                    Surface(color = androidx.compose.ui.graphics.Color(0xFF090B0E), shape = MaterialTheme.shapes.medium) {
                        Text(query.ifBlank { "أدخل كلمات البحث لبناء الاستعلام..." },
                            Modifier.fillMaxWidth().padding(14.dp), color = androidx.compose.ui.graphics.Color(0xFF9EE7B2))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (query.isNotBlank()) {
                                    history = (listOf(query) + history).distinct().take(50)
                                    prefs.edit().putString("history", JSONArray(history).toString()).apply()
                                    openSearch(context, query, engine)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = query.isNotBlank()
                        ) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text("ابحث") }
                        OutlinedButton(
                            onClick = {
                                if (query.isNotBlank() && query !in saved) {
                                    saved = (listOf(query) + saved).take(100)
                                    prefs.edit().putString("saved", JSONArray(saved).toString()).apply()
                                }
                            },
                            enabled = query.isNotBlank()
                        ) { Icon(Icons.Default.Bookmark, null); Text("حفظ") }
                    }
                    Text("سجل آخر عمليات البحث: ${history.size}", style = MaterialTheme.typography.labelMedium)
                    Text("الإصدار الأول • بحث عام ومستندات منشورة فقط", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SavedScreen(
    items: List<String>, onOpen: (String) -> Unit, onDelete: (String) -> Unit,
    onBack: () -> Unit, history: List<String>, onClearHistory: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onBack) { Text("العودة إلى المنشئ") }
        Text("الاستعلامات المحفوظة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (items.isEmpty()) Text("لا توجد استعلامات محفوظة بعد.")
        items.forEach { item ->
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text(item)
                    Row {
                        TextButton(onClick = { onOpen(item) }) { Text("فتح البحث") }
                        TextButton(onClick = { onDelete(item) }) { Text("حذف") }
                    }
                }
            }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("سجل البحث", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            TextButton(onClick = onClearHistory) { Text("مسح السجل") }
        }
        history.take(20).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
    }
}

private fun locationLabel(value: String) = when (value) {
    "Anywhere" -> "Anywhere / أي مكان"
    "Page title" -> "عنوان الصفحة"
    "URL path" -> "مسار الرابط"
    else -> "نص الصفحة"
}

private fun openSearch(context: android.content.Context, query: String, engine: String) {
    val encoded = URLEncoder.encode(query, "UTF-8")
    val url = when (engine) {
        "DuckDuckGo" -> "https://duckduckgo.com/?q=$encoded"
        "Bing" -> "https://www.bing.com/search?q=$encoded"
        "Startpage" -> "https://www.startpage.com/sp/search?query=$encoded"
        else -> "https://www.google.com/search?q=$encoded"
    }
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun loadList(raw: String): List<String> = try {
    val arr = JSONArray(raw)
    (0 until arr.length()).map { arr.getString(it) }
} catch (_: Exception) { emptyList() }
