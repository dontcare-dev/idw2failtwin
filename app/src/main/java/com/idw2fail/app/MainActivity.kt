package com.idw2fail.app

import android.os.Bundle
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Typeface
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.io.Writer
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private val Ink = Color(0xFFFFFFFF)
private val Panel = Color(0xFFF5F6F8)
private val Panel2 = Color(0xFFE9ECF0)
private val Muted = Color(0xFF68717D)
private val Accent = Color(0xFF17191D)
private val Mint = Color(0xFF147D67)
private val Coral = Color(0xFFD85C4A)
private val Violet = Color(0xFF6950A1)
private val Gold = Color(0xFF9B6B00)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        setContent { IdwTheme { IdwApp() } }
    }
}

@Composable
private fun IdwTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        background = Ink, surface = Panel, primary = Accent, secondary = Mint,
        onBackground = Color(0xFF17191D), onSurface = Color(0xFF17191D)
    ), content = content)
}

private class CappedTextWriter(private val maxChars: Int) : Writer() {
    private val output = StringBuilder(maxChars)
    override fun write(chars: CharArray, offset: Int, length: Int) {
        val remaining = maxChars - output.length
        if (remaining > 0 && length > 0) output.append(chars, offset, minOf(length, remaining))
    }
    override fun flush() = Unit
    override fun close() = Unit
    override fun toString(): String = output.toString()
}

private class LocalData(context: android.content.Context) {
    private val file = java.io.File(context.filesDir, "study-data.json")
    fun read(): JSONObject = try { if (file.exists()) JSONObject(file.readText()) else JSONObject() } catch (_: Exception) { JSONObject() }
    fun write(data: JSONObject) { file.writeText(data.toString(2)) }
    fun exportJson(): String = read().toString(2)
    fun restoreJson(raw: String) {
        val restored = JSONObject(raw)
        if (!restored.has("documents") && !restored.has("notes") && !restored.has("flashcards") && !restored.has("tasks")) {
            throw IllegalArgumentException("This file does not look like an idw2fail backup.")
        }
        write(restored)
    }
    fun documents(): JSONArray = read().optJSONArray("documents") ?: JSONArray()
    fun addDocument(name: String, text: String, pages: JSONArray = JSONArray()) {
        val d = read(); val list = d.optJSONArray("documents") ?: JSONArray()
        list.put(JSONObject().put("id", UUID.randomUUID().toString()).put("name", name).put("text", text).put("pages", pages).put("subject", "General").put("createdAt", System.currentTimeMillis()))
        d.put("documents", list); write(d)
    }
    fun setDocumentSubject(id: String, subject: String) {
        val d = read(); val list = d.optJSONArray("documents") ?: JSONArray()
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i)
            if (item?.optString("id") == id) item.put("subject", subject.trim().ifBlank { "General" })
        }
        d.put("documents", list); write(d)
    }
    fun setQuizResult(id: String, correct: Boolean) {
        val d = read(); val list = d.optJSONArray("quizzes") ?: JSONArray()
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i)
            if (item?.optString("id") == id) {
                item.put("attempted", true)
                item.put("lastCorrect", correct)
                item.put("attemptCount", item.optInt("attemptCount") + 1)
                if (!correct) item.put("wrong", true)
            }
        }
        d.put("quizzes", list); write(d)
    }
    fun removeDocument(id: String) {
        val d = read(); val old = d.optJSONArray("documents") ?: JSONArray(); val next = JSONArray()
        for (i in 0 until old.length()) if (old.optJSONObject(i)?.optString("id") != id) next.put(old.get(i))
        d.put("documents", next); write(d)
    }
    fun entries(bucket: String): JSONArray = read().optJSONArray(bucket) ?: JSONArray()
    fun addEntry(bucket: String, title: String, body: String) {
        val d = read(); val list = d.optJSONArray(bucket) ?: JSONArray()
        list.put(JSONObject().put("id", UUID.randomUUID().toString()).put("title", title).put("body", body).put("done", false).put("createdAt", System.currentTimeMillis()))
        d.put(bucket, list); write(d)
    }
    fun setDone(bucket: String, id: String, done: Boolean) {
        val d = read(); val list = d.optJSONArray(bucket) ?: JSONArray()
        for (i in 0 until list.length()) { val item = list.optJSONObject(i); if (item?.optString("id") == id) item.put("done", done) }
        d.put(bucket, list); write(d)
    }
    fun setSaved(bucket: String, id: String, saved: Boolean) {
        val d = read()
        val list = d.optJSONArray(bucket) ?: JSONArray()
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i)
            if (item?.optString("id") == id) item.put("saved", saved)
        }
        d.put(bucket, list)
        write(d)
    }
    fun setFlag(bucket: String, id: String, field: String, value: Boolean) {
        val d = read()
        val list = d.optJSONArray(bucket) ?: JSONArray()
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i)
            if (item?.optString("id") == id) item.put(field, value)
        }
        d.put(bucket, list)
        write(d)
    }
    fun renameDocument(id: String, name: String) {
        val d = read()
        val list = d.optJSONArray("documents") ?: JSONArray()
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i)
            if (item?.optString("id") == id) item.put("name", name.trim())
        }
        d.put("documents", list)
        write(d)
    }
    fun removeEntry(bucket: String, id: String) {
        val d = read(); val old = d.optJSONArray(bucket) ?: JSONArray(); val next = JSONArray()
        for (i in 0 until old.length()) if (old.optJSONObject(i)?.optString("id") != id) next.put(old.get(i))
        d.put(bucket, next); write(d)
    }
}

private class KeyVault(context: android.content.Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "private_ai_settings",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    fun provider(): String = prefs.getString("provider", "Groq") ?: "Groq"
    fun keys(provider: String): List<String> = (prefs.getString("keys_$provider", "") ?: "").lineSequence().map { it.trim() }.filter { it.isNotBlank() }.distinct().toList()
    fun save(provider: String, value: String) { prefs.edit().putString("provider", provider).putString("keys_$provider", value.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n")).apply() }
}

@Composable
private fun IdwApp() {
    val context = LocalContext.current
    val data = remember { LocalData(context) }
    val vault = remember { KeyVault(context) }
    var page by remember { mutableStateOf("Home") }
    var refresh by remember { mutableIntStateOf(0) }
    var aiProvider by remember { mutableStateOf(vault.provider()) }
    var apiKeys by remember { mutableStateOf(listOf("Groq", "OpenAI", "Gemini").associateWith { vault.keys(it).joinToString("\n") }) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var studyBoard by remember { mutableStateOf<JSONObject?>(null) }
    var hubResult by remember { mutableStateOf("") }
    var hubBoard by remember { mutableStateOf<JSONObject?>(null) }
    var offlineMode by remember { mutableStateOf(context.getSharedPreferences("app_settings", 0).getBoolean("offline_mode", false)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val docs = remember(refresh) { data.documents() }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri) ?: throw Exception("Could not create backup file.")
                    output.use { it.write(data.exportJson().toByteArray(Charsets.UTF_8)) }
                }
                status = "Backup exported."
            } catch (e: Exception) { status = e.message ?: "Backup export failed." }
        }
    }
    val examPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val pdf = PdfDocument()
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; color = android.graphics.Color.BLACK }
                    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = android.graphics.Color.BLACK }
                    var pageNo = 1
                    var page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
                    var canvas = page.canvas
                    var y = 52f
                    canvas.drawText("SDS-IDW2FAILTWIN", 40f, y, titlePaint); y += 30f
                    canvas.drawText("FULL-CHAPTER MOCK EXAM · 3 HOURS", 40f, y, paint); y += 34f
                    canvas.drawText("Name: ____________________    Date: ______________", 40f, y, paint); y += 34f
                    val saved = data.entries("exams")
                    val paper = if (saved.length() > 0) runCatching { JSONObject(saved.optJSONObject(0)?.optString("body") ?: "{}") }.getOrElse { JSONObject() } else JSONObject()
                    val qs = paper.optJSONArray("questions") ?: JSONArray()
                    for (i in 0 until qs.length()) {
                        val q = qs.optJSONObject(i) ?: continue
                        val lines = listOf("${i + 1}. [${q.optString("type")}] ${q.optString("question")}") +
                            (0 until (q.optJSONArray("choices")?.length() ?: 0)).map { n -> "   ${'A' + n}. ${q.optJSONArray("choices")?.optString(n)}" } +
                            listOf("   Marks: ${q.optInt("points", 1)}", "")
                        for (line in lines) {
                            if (y > 790f) { pdf.finishPage(page); pageNo++; page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create()); canvas = page.canvas; y = 50f }
                            canvas.drawText(line.take(90), 40f, y, paint); y += 20f
                        }
                    }
                    pdf.finishPage(page)
                    val output = context.contentResolver.openOutputStream(uri) ?: throw Exception("Could not create exam PDF.")
                    output.use { pdf.writeTo(it) }; pdf.close()
                }
                status = "Exam PDF exported."
            } catch (e: Exception) { status = e.message ?: "Exam PDF export failed." }
        }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val raw = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw Exception("Could not read backup file.")
                }
                data.restoreJson(raw); refresh++; status = "Backup restored on this device."
            } catch (e: Exception) { status = e.message ?: "Backup restore failed." }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; status = "Reading your PDF…"
            try {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "Study material.pdf"
                val extractedData = withContext(Dispatchers.IO) {
                    val temp = java.io.File.createTempFile("idw-pdf-", ".pdf", context.cacheDir)
                    try {
                        val input = context.contentResolver.openInputStream(uri) ?: throw Exception("The selected file could not be opened.")
                        input.use { source ->
                            temp.outputStream().use { target ->
                                val buffer = ByteArray(8192)
                                var total = 0L
                                while (true) {
                                    val count = source.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    if (total > 30L * 1024L * 1024L) throw Exception("This PDF is larger than 30 MB. Try a smaller file.")
                                    target.write(buffer, 0, count)
                                }
                            }
                        }
                        PDDocument.load(temp, MemoryUsageSetting.setupTempFileOnly()).use { pdf ->
                            if (pdf.numberOfPages > 60) throw Exception("This PDF has ${pdf.numberOfPages} pages. Please use a file with 60 pages or fewer.")
                            val stripper = PDFTextStripper()
                            val pages = JSONArray()
                            val fullText = StringBuilder()
                            for (pageNumber in 1..pdf.numberOfPages) {
                                stripper.startPage = pageNumber
                                stripper.endPage = pageNumber
                                val pageWriter = CappedTextWriter(12000)
                                stripper.writeText(pdf, pageWriter)
                                val pageText = pageWriter.toString()
                                pages.put(JSONObject().put("number", pageNumber).put("text", pageText))
                                if (fullText.length < 80000) fullText.append("\n[Page $pageNumber]\n").append(pageText.take(80000 - fullText.length))
                            }
                            Pair(fullText.toString(), pages)
                        }
                    } finally { temp.delete() }
                }
                val extracted = extractedData.first
                if (extracted.isBlank()) throw Exception("No selectable text was found. This may be a scanned PDF.")
                data.addDocument(name, extracted, extractedData.second); refresh++
                status = "PDF added · ${extracted.length} characters extracted"
            } catch (e: Exception) { status = "Couldn't read that PDF: ${e.message ?: "Unsupported or damaged PDF."}" }
            finally { busy = false }
        }
    }
    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFF5F6F8), RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFF17191D).copy(alpha = .10f), RoundedCornerShape(24.dp)),
                containerColor = Color.Transparent,
                contentColor = Color(0xFF17191D)
            ) {
                listOf("Home", "Library", "Study", "MCQs", "More").forEach { item ->
                    val icon = when (item) {
                        "Home" -> Icons.Rounded.Home
                        "Library" -> Icons.Rounded.FolderOpen
                        "Study" -> Icons.Rounded.AutoAwesome
                        "MCQs" -> Icons.Rounded.Quiz
                        else -> Icons.Rounded.MoreHoriz
                    }
                    NavigationBarItem(selected = page == (if (item == "MCQs") "Quizzes" else item), onClick = { page = if (item == "MCQs") "Quizzes" else item },
                        icon = { Icon(icon, null) }, label = { Text(item, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent,
                            indicatorColor = Color(0xFF17191D).copy(alpha = .10f), unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(Violet, Coral, Mint))), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.School, null, tint = Ink, modifier = Modifier.size(21.dp)) }; Spacer(Modifier.width(10.dp)); Text("SDS-IDW2FAILTWIN", fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp) }
            }
            Crossfade(targetState = page, animationSpec = tween(260), label = "page") { currentPage ->
            when (currentPage) {
                "Home" -> HomeScreen(docs.length(), data.entries("flashcards").length()) { page = it }
                "Search" -> GlobalSearchScreen(docs, data.entries("quizzes"), data.entries("flashcards"), data.entries("syllabus"), data.entries("formulas")) { page = it }
                "Tools" -> LearningHubScreen(
                    documents = docs, result = hubResult, board = hubBoard, status = status, busy = busy, offline = offlineMode,
                    onOfflineChange = { offlineMode = it; context.getSharedPreferences("app_settings", 0).edit().putBoolean("offline_mode", it).apply(); status = if (it) "Offline mode enabled. Saved study material remains available." else "Offline mode disabled." },
                    onSetSubject = { id, subject -> data.setDocumentSubject(id, subject); refresh++ },
                    onSaveFormula = { title, body -> data.addEntry("formulas", title, body); refresh++; status = "Formula sheet saved on this device." },
                    onGenerate = { mode, prompt, documentId ->
                        if (offlineMode) status = "Turn off offline mode to use AI."
                        else if (offlineMode) { status = "Offline mode is on. Turn it off to use AI." } else if (vault.keys(aiProvider).isEmpty()) { page = "Settings"; status = "Add an API key for your selected provider first." }
                        else scope.launch {
                            val selected = (0 until docs.length()).mapNotNull { docs.optJSONObject(it) }.firstOrNull { it.optString("id") == documentId }
                            if (selected == null) { status = "Choose a study PDF first." }
                            else {
                                busy = true; status = "Mochi is preparing $mode…"; hubResult = ""; hubBoard = null
                                try {
                                    val pages = selected.optJSONArray("pages") ?: JSONArray()
                                    val source = buildString {
                                        if (pages.length() > 0) {
                                            for (i in 0 until pages.length()) {
                                                val p = pages.optJSONObject(i) ?: continue
                                                append("[Source: ${selected.optString("name")}, page ${p.optInt("number", i + 1)}]\n")
                                                append(p.optString("text")).append("\n")
                                            }
                                        } else append("[Source: ${selected.optString("name")}; page index unavailable for this older import]\n").append(selected.optString("text"))
                                    }.take(14000)
                                    val task = when (mode) {
                                        "Ask PDF" -> "Answer this question using ONLY the supplied PDF text: $prompt. Cite every factual claim with [filename, p. N] only when the page marker is present. Never invent page numbers. If the PDF does not contain the answer, say so."
                                        "Mind map" -> "Create a clear mind map for ${prompt.ifBlank { selected.optString("name") }} based on the PDF. Return a concise answer with central idea and branches. Add a whiteboard board with labelled nodes and connecting lines, coordinates 0..1000."
                                        "Past paper" -> "Analyse this imported past exam paper. Identify subject areas, question types, recurring themes visible in this document, topic distribution where countable, and a practical revision checklist. Do not claim trends across years from one paper. Cite page references when available. $prompt"
                                        "Diagram practice" -> "Create a school-level diagram practice exercise for ${prompt.ifBlank { "the main relevant topic in this PDF" }}. Explain what the learner should label, provide a simple labelled answer guide, and draw a clear whiteboard diagram using vector lines, circles and text. Cite PDF page references when available."
                                        else -> "Extract the important formulas from this PDF for ${prompt.ifBlank { "the main topics" }}. For each give formula, define symbols, units, and one short use note. Cite page references when available. Use a clean numbered list."
                                    }
                                    val raw = askWithFallback(aiProvider, vault.keys(aiProvider), task, source)
                                    val parsed = extractTutorResponse(raw)
                                    hubResult = parsed.first
                                    hubBoard = parsed.second
                                    status = if (hubResult.isBlank()) "No usable result was returned. Try again." else "Generated from ${selected.optString("name")}."
                                } catch (e: Exception) { status = e.message ?: "AI tool failed." }
                                finally { busy = false }
                            }
                        }
                    }
                )
                "Readiness" -> ExamReadinessScreen(data.entries("syllabus"), data.entries("quizzes"), data.entries("flashcards"), docs)
                "Library" -> LibraryScreen(docs, busy, status, onAdd = { picker.launch(arrayOf("application/pdf")) }, onDelete = { data.removeDocument(it); refresh++ }, onRename = { id, name -> data.renameDocument(id, name); refresh++ })
                "Study" -> StudyScreen(question, { question = it }, answer, studyBoard, busy, status, onAsk = {
                    if (offlineMode) { status = "Offline mode is on. Turn it off to use AI." } else if (vault.keys(aiProvider).isEmpty()) { page = "Settings"; status = "Add an API key for your selected provider first." }
                    else scope.launch {
                        busy = true; answer = ""; studyBoard = null; status = "Mochi is thinking…"
                        try {
                            val contextText = buildString { for (i in 0 until docs.length()) append(docs.optJSONObject(i)?.optString("text", "")).append("\n") }.take(14000)
                            val raw = askWithFallback(aiProvider, vault.keys(aiProvider), question, contextText)
                            val parsed = extractTutorResponse(raw)
                            answer = parsed.first
                            studyBoard = parsed.second ?: if (question.contains("pendulum", true)) pendulumBoard() else null
                            status = ""
                        } catch (e: Exception) { status = e.message ?: "AI request failed." }
                        busy = false
                    }
                })
                "Practice" -> PracticeScreen(
                    cards = data.entries("flashcards"), refresh = refresh, generating = busy, status = status,
                    onGenerate = {
                        if (offlineMode) { status = "Offline mode is on. Turn it off to use AI." } else if (vault.keys(aiProvider).isEmpty()) { page = "Settings"; status = "Add an API key for your selected provider first." }
                        else if (docs.length() == 0) status = "Import a PDF first so the AI has material to study."
                        else scope.launch {
                            busy = true; status = "Creating flashcards from your PDFs…"
                            try {
                                val contextText = buildString { for (i in 0 until docs.length()) append(docs.optJSONObject(i)?.optString("text", "")).append("\n") }.take(14000)
                                val raw = askWithFallback(aiProvider, vault.keys(aiProvider), "Create 8 useful school revision flashcards from the study material. Avoid repeating existing flashcards. Return ONLY a valid JSON array. Each item must have exactly two string fields: question and answer. Do not include markdown fences or any text outside the JSON array.", contextText)
                                val start = raw.indexOf('['); val end = raw.lastIndexOf(']')
                                if (start < 0 || end <= start) throw Exception("The AI returned an invalid flashcard format. Try again.")
                                val generated = JSONArray(raw.substring(start, end + 1))
                                var added = 0
                                for (i in 0 until generated.length()) {
                                    val item = generated.optJSONObject(i) ?: continue
                                    val q = item.optString("question").trim(); val a = item.optString("answer").trim()
                                    if (q.isNotBlank() && a.isNotBlank()) {
                                        val duplicate = (0 until data.entries("flashcards").length()).any { data.entries("flashcards").optJSONObject(it)?.optString("title")?.trim()?.equals(q, true) == true }
                                        if (!duplicate) { data.addEntry("flashcards", q, a); added++ }
                                    }
                                }
                                refresh++; status = if (added > 0) "$added flashcards added to your deck." else "No usable cards were returned. Try again."
                            } catch (e: Exception) { status = e.message ?: "Flashcard generation failed." }
                            busy = false
                        }
                    },
                    onAdd = { front, back -> data.addEntry("flashcards", front, back); refresh++ },
                    onDelete = { data.removeEntry("flashcards", it); refresh++ },
                    onSave = { id, saved -> data.setSaved("flashcards", id, saved); refresh++ },
                    onKnown = { id, known -> data.setFlag("flashcards", id, "known", known); refresh++ }
                )
                "More" -> MoreScreen(
                    taskCount = data.entries("tasks").length(),
                    onFlashcards = { page = "Practice" }, onQuizzes = { page = "Quizzes" }, onPlanner = { page = "Planner" }, onSyllabus = { page = "Syllabus" }, onSettings = { page = "Settings" }, onProgress = { page = "Progress" }, onExams = { page = "Exams" },
                    onTools = { page = "Tools" }, onSearch = { page = "Search" }, onReadiness = { page = "Readiness" }
                )
                "Quizzes" -> QuizScreen(
                    quizzes = data.entries("quizzes"), refresh = refresh, generating = busy, status = status,
                    onGenerate = {
                        if (offlineMode) { status = "Offline mode is on. Turn it off to use AI." } else if (vault.keys(aiProvider).isEmpty()) { page = "Settings"; status = "Add an API key for your selected provider first." }
                        else if (docs.length() == 0) status = "Import a PDF first so the AI has material to study."
                        else scope.launch {
                            busy = true; status = "Generating your practice quiz…"
                            try {
                                val contextText = buildString { for (i in 0 until docs.length()) append(docs.optJSONObject(i)?.optString("text", "")).append("\n") }.take(14000)
                                val raw = askWithFallback(aiProvider, vault.keys(aiProvider), "Create 10 school-level multiple-choice questions from the study material. Do not repeat these existing questions: " + (0 until data.entries("quizzes").length()).mapNotNull { data.entries("quizzes").optJSONObject(it)?.optString("title") }.joinToString(" | ").take(5000) + ". Return ONLY a valid JSON array. Each item must contain question (string), choices (array of exactly four short strings), answer (one exact choice string), and explanation (string). Exactly one correct answer per question. No markdown or text outside JSON.", contextText)
                                val start = raw.indexOf('['); val end = raw.lastIndexOf(']')
                                if (start < 0 || end <= start) throw Exception("The AI returned an invalid quiz format. Try again.")
                                val generated = JSONArray(raw.substring(start, end + 1))
                                var added = 0
                                for (i in 0 until generated.length()) {
                                    val item = generated.optJSONObject(i) ?: continue
                                    val options = item.optJSONArray("choices") ?: item.optJSONArray("options")
                                    val answerChoice = item.optString("answer", item.optString("correct_answer")).trim()
                                    val choiceValues = if (options == null) emptyList() else
                                        (0 until options.length()).map { index ->
                                            val option = options.opt(index)
                                            when (option) {
                                                is JSONObject -> option.optString("text", option.optString("value", option.optString("option"))).trim()
                                                else -> option.toString().trim()
                                            }
                                        }
                                    val uniqueChoices = choiceValues.map { it.lowercase() }.toSet().size == 4
                                    val answerLabel = answerChoice.uppercase().removePrefix("OPTION ").trim().removeSuffix(".")
                                    val correctChoice = choiceValues.firstOrNull { it.equals(answerChoice, true) }
                                        ?: answerLabel.takeIf { it.length == 1 && it[0] in 'A'..'D' }
                                            ?.let { choiceValues.getOrNull(it[0] - 'A') }
                                    val questionText = item.optString("question").trim()
                                    if (questionText.isNotBlank() && choiceValues.size == 4 && choiceValues.all { it.isNotBlank() } && uniqueChoices && correctChoice != null) {
                                        val duplicate = (0 until data.entries("quizzes").length()).any { data.entries("quizzes").optJSONObject(it)?.optString("title")?.trim()?.equals(questionText, true) == true }
                                        if (!duplicate) {
                                            val normalized = JSONObject(item.toString()).put("question", questionText)
                                                .put("choices", JSONArray(choiceValues)).put("answer", correctChoice)
                                            normalized.remove("options")
                                            normalized.remove("correct_answer")
                                            data.addEntry("quizzes", questionText, normalized.toString())
                                            added++
                                        }
                                    }
                                }
                                refresh++; status = if (added > 0) "$added quiz questions added." else "No usable questions were returned. Try again."
                            } catch (e: Exception) { status = e.message ?: "Quiz generation failed." }
                            busy = false
                        }
                    },
                    onDelete = { data.removeEntry("quizzes", it); refresh++ },
                    onSave = { id, saved -> data.setSaved("quizzes", id, saved); refresh++ },
                    onWrong = { id, wrong -> data.setFlag("quizzes", id, "wrong", wrong); refresh++ },
                    onResult = { id, correct -> data.setQuizResult(id, correct); refresh++ }
                )
                "Exams" -> ExamScreen(exam = data.entries("exams"), generating = busy, status = status, onMake = {
                    if (offlineMode) { status = "Offline mode is on. Turn it off to use AI." } else if (vault.keys(aiProvider).isEmpty()) { page = "Settings"; status = "Add an API key for your selected provider first." }
                    else if (docs.length() == 0) status = "Import a study PDF first."
                    else scope.launch {
                        busy = true; status = "Generating five sections from your study material…"
                        try {
                            val studyText = buildString { for (i in 0 until docs.length()) append(docs.optJSONObject(i)?.optString("text", "")).append("\n") }.take(14000)
                            val sections = listOf("MCQ" to "multiple-choice", "True / False" to "true-or-false", "Short answer" to "short-answer", "Question answer" to "medium-length written-answer", "Long answer" to "long-answer")
                            val all = JSONArray()
                            for ((label, format) in sections) {
                                val prompt = "Create exactly 20 distinct $format school exam questions based only on the supplied syllabus and study material. Cover the major topics broadly, not just the first pages. Return ONLY a JSON array of objects with fields type (exactly '$label'), question, choices (four strings for MCQ, ['True','False'] for True / False, omit for written questions), answer (correct answer or marking points), and points (integer). No repeated questions. No markdown."
                                val raw = askWithFallback(aiProvider, vault.keys(aiProvider), prompt, studyText)
                                val a = raw.indexOf('['); val b = raw.lastIndexOf(']')
                                if (a < 0 || b <= a) throw Exception("The AI returned an invalid $label section. Try again.")
                                val batch = JSONArray(raw.substring(a, b + 1))
                                if (batch.length() < 15) throw Exception("The $label section returned too few questions. Try again.")
                                for (j in 0 until minOf(20, batch.length())) { val item = batch.optJSONObject(j) ?: continue; if (item.optString("question").isNotBlank()) all.put(item) }
                            }
                            if (all.length() != 100) throw Exception("The paper contained ${all.length()} valid questions instead of 100. Try again.")
                            val paper = JSONObject().put("questions", all).put("createdAt", System.currentTimeMillis())
                            val previous = data.entries("exams")
                            for (i in 0 until previous.length()) data.removeEntry("exams", previous.optJSONObject(i)?.optString("id") ?: "")
                            data.addEntry("exams", "100-question exam", paper.toString()); refresh++
                            status = "100 questions generated across five sections."
                        } catch (e: Exception) { status = e.message ?: "Exam generation failed." }
                        busy = false
                    }
                }, onExport = { examPdfPicker.launch("SDS-100-question-exam.pdf") })
                "Progress" -> ProgressScreen(
                    documentCount = docs.length(),
                    cardCount = data.entries("flashcards").length(),
                    quizCount = data.entries("quizzes").length(),
                    tasks = data.entries("tasks")
                )
                "Planner" -> PlannerScreen(
                    tasks = data.entries("tasks"),
                    onAdd = { title -> data.addEntry("tasks", title, ""); refresh++ },
                    onToggle = { id, done -> data.setDone("tasks", id, done); refresh++ },
                    onDelete = { data.removeEntry("tasks", it); refresh++ }
                )
                "Syllabus" -> SyllabusScreen(
                    topics = data.entries("syllabus"),
                    onAdd = { title -> data.addEntry("syllabus", title, ""); refresh++ },
                    onToggle = { id, done -> data.setDone("syllabus", id, done); refresh++ },
                    onDelete = { data.removeEntry("syllabus", it); refresh++ }
                )
                else -> SettingsScreen(
                    provider = aiProvider,
                    keys = apiKeys,
                    onSave = { provider, value -> aiProvider = provider; apiKeys = apiKeys + (provider to value); vault.save(provider, value); status = "Provider keys saved securely on this device." },
                    status = status,
                    onExport = { exportPicker.launch("idw2fail-backup.json") },
                    onImport = { backupPicker.launch(arrayOf("application/json", "text/json")) }
                )
            }
            }
        }
    }
}

@Composable
private fun HomeScreen(documentCount: Int, cardCount: Int, navigate: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Home", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp) }
        item {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFFEAF4F1), Color(0xFFF1EEF8), Color(0xFFFFF0EB))), RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFF17191D).copy(alpha = .10f), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Text("Study dashboard", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
                Button(
                    onClick = { navigate("Study") },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) { Text("Start studying") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("PDFs", documentCount.toString(), Icons.Rounded.FolderOpen, Modifier.weight(1f))
                StatCard("Flashcards", cardCount.toString(), Icons.Rounded.Style, Modifier.weight(1f))
            }
        }
        item {
            Text("Your study", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(top = 10.dp)) {
                QuickRow("Library", "PDFs and textbooks", Icons.Rounded.FolderOpen) { navigate("Library") }
                QuickRow("Flashcards", "Review your cards", Icons.Rounded.Style) { navigate("Practice") }
                QuickRow("Quizzes", "Test what you know", Icons.Rounded.Quiz) { navigate("Quizzes") }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xFF17191D).copy(alpha = .045f), RoundedCornerShape(20.dp)).border(1.dp, Color(0xFF17191D).copy(alpha = .10f), RoundedCornerShape(20.dp)).padding(16.dp)) {
        Icon(icon, null, tint = Accent); Spacer(Modifier.height(15.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold); Text(title, color = Muted, fontSize = 11.sp)
    }
}
@Composable
private fun QuickRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF17191D).copy(alpha = .045f), RoundedCornerShape(17.dp)).border(1.dp, Color(0xFF17191D).copy(alpha = .10f), RoundedCornerShape(17.dp)).clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp); Text(subtitle, color = Muted, fontSize = 11.sp) }
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun LibraryScreen(docs: JSONArray, busy: Boolean, status: String, onAdd: () -> Unit, onDelete: (String) -> Unit, onRename: (String, String) -> Unit) {
    var search by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf("") }
    var editingName by remember { mutableStateOf("") }
    var subjectFilter by remember { mutableStateOf("All subjects") }
    val allDocs = (0 until docs.length()).mapNotNull { docs.optJSONObject(it) }
    val subjects = allDocs.map { it.optString("subject", "General").ifBlank { "General" } }.distinct().sorted()
    val visibleDocs = allDocs.filter { (subjectFilter == "All subjects" || it.optString("subject", "General") == subjectFilter) && (search.isBlank() || it.optString("name").contains(search, true) || it.optString("text").contains(search, true)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 18.dp)) {
        Text("Library", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search PDFs and their text") }, singleLine = true, shape = RoundedCornerShape(13.dp), colors = fieldColors())
        Row(Modifier.fillMaxWidth().padding(top = 7.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = subjectFilter == "All subjects", onClick = { subjectFilter = "All subjects" }, label = { Text("All subjects") })
            subjects.forEach { subject -> FilterChip(selected = subjectFilter == subject, onClick = { subjectFilter = subject }, label = { Text(subject) }) }
        }
        if (editingId.isNotBlank()) {
            OutlinedTextField(editingName, { editingName = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("PDF name") }, singleLine = true, colors = fieldColors())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { editingId = ""; editingName = "" }) { Text("Cancel") }
                TextButton(onClick = { if (editingName.isNotBlank()) onRename(editingId, editingName); editingId = ""; editingName = "" }) { Text("Save name") }
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = onAdd, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Import a PDF", fontWeight = FontWeight.SemiBold)
        }
        if (status.isNotBlank()) Text(status, color = Mint, fontSize = 11.sp, modifier = Modifier.padding(vertical = 10.dp))
        if (docs.length() == 0) EmptyState("No documents", "Import a PDF to begin.", Icons.Rounded.FolderOpen)
        else if (visibleDocs.isEmpty()) EmptyState("No matching PDFs", "Try a different search term.", Icons.Rounded.Search)
        else Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 16.dp)) {
            visibleDocs.forEach { doc ->
                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PictureAsPdf, null, tint = Accent); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(doc.optString("name"), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("${doc.optString("subject", "General")} · ${doc.optString("text").length} characters", color = Muted, fontSize = 10.sp)
                    }
                    IconButton(onClick = { editingId = doc.optString("id"); editingName = doc.optString("name") }) { Icon(Icons.Rounded.Edit, "Rename PDF", tint = Muted) }
                    IconButton(onClick = { onDelete(doc.optString("id")) }) { Icon(Icons.Rounded.DeleteOutline, "Delete PDF", tint = Muted) }
                }
            }
        }
    }
}

@Composable
private fun StudyScreen(question: String, onQuestion: (String) -> Unit, answer: String, board: JSONObject?, busy: Boolean, status: String, onAsk: () -> Unit) {
    val context = LocalContext.current
    val speech = remember(context) { TextToSpeech(context) { } }
    DisposableEffect(speech) { onDispose { speech.stop(); speech.shutdown() } }
    val speechResult = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onQuestion)
        }
    }
    val speechPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            runCatching { speechResult.launch(intent) }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Study", fontSize = 28.sp, fontWeight = FontWeight.SemiBold) }
        item {
            OutlinedTextField(
                value = question, onValueChange = onQuestion,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ask anything…") },
                minLines = 3, maxLines = 6,
                shape = RoundedCornerShape(16.dp), colors = fieldColors()
            )
        }
        item {
            OutlinedButton(onClick = {
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    runCatching { speechResult.launch(intent) }
                } else speechPermission.launch(Manifest.permission.RECORD_AUDIO)
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Mic, null); Spacer(Modifier.width(7.dp)); Text("Speak your question")
            }
        }
        item {
            Button(
                onClick = onAsk, enabled = !busy && question.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Ink)
                else Icon(Icons.Rounded.Send, null)
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "Thinking…" else "Ask")
            }
        }
        if (status.isNotBlank()) item { Text(status, color = Mint, fontSize = 12.sp) }
        if (answer.isNotBlank()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Pets, null, tint = Violet, modifier = Modifier.size(23.dp))
                    Text("Mochi · Study cat", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                }
                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(14.dp)) {
                    Text(answer, fontSize = 14.sp, lineHeight = 22.sp)
                    TextButton(onClick = { speech.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "mochi-answer") }, modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.Rounded.VolumeUp, null); Spacer(Modifier.width(6.dp)); Text("Read aloud")
                    }
                }
            }
        }
        if (board != null) item { StudyWhiteboard(board) }
    }
}

@Composable
private fun SettingsScreen(provider: String, keys: Map<String, String>, onSave: (String, String) -> Unit, status: String, onExport: () -> Unit, onImport: () -> Unit) {
    var selectedProvider by remember(provider) { mutableStateOf(provider) }
    var keyText by remember(provider, keys) { mutableStateOf(keys[provider] ?: "") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 18.dp)) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Text("AI providers", color = Muted, fontSize = 13.sp)
        Text("Choose a provider", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 10.dp)) {
            listOf("Groq", "OpenAI", "Gemini").forEach { name ->
                FilterChip(selected = selectedProvider == name, onClick = { selectedProvider = name; keyText = keys[name] ?: "" }, label = { Text(name) })
            }
        }
        Text("Add one or more API keys below, one per line. The app tries the next key when a request is rate-limited. Keys are encrypted on this device.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        OutlinedTextField(value = keyText, onValueChange = { keyText = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Paste API keys, one per line") }, minLines = 3, maxLines = 7, shape = RoundedCornerShape(15.dp), colors = fieldColors())
        Button(onClick = { onSave(selectedProvider, keyText) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(50.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) { Icon(Icons.Rounded.Lock, null); Spacer(Modifier.width(8.dp)); Text("Save provider keys") }
        if (status.isNotBlank()) Text(status, color = Mint, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(24.dp))
        Text("Data", color = Muted, fontSize = 13.sp)
        Text("Study data is stored in a private JSON file inside the app. Imported PDF text is stored locally so your tutor can reference it. When you ask the AI a question, relevant text is sent to Groq.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Rounded.FileDownload, null); Spacer(Modifier.width(6.dp)); Text("Export backup") }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Rounded.FileUpload, null); Spacer(Modifier.width(6.dp)); Text("Restore backup") }
        }
        Text("Restoring replaces the current local study data. Export a backup first if you want to keep it.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
        Text("Never share your API key. Anyone with access to your unlocked phone may be able to use the configured AI account.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 14.dp))
    }
}


@Composable
private fun PracticeScreen(cards: JSONArray, refresh: Int, generating: Boolean, status: String, onGenerate: () -> Unit, onAdd: (String, String) -> Unit, onDelete: (String) -> Unit, onSave: (String, Boolean) -> Unit, onKnown: (String, Boolean) -> Unit) {
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var savedOnly by remember { mutableStateOf(false) }
    var knownOnly by remember { mutableStateOf(false) }
    val allCards = (0 until cards.length()).mapNotNull { cards.optJSONObject(it) }
    val items = allCards.filter { (!savedOnly || it.optBoolean("saved")) && (!knownOnly || !it.optBoolean("known")) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("Flashcards", fontSize = 28.sp, fontWeight = FontWeight.SemiBold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !savedOnly && !knownOnly, onClick = { savedOnly = false; knownOnly = false; index = 0; revealed = false }, label = { Text("All cards") })
                FilterChip(selected = savedOnly, onClick = { savedOnly = true; knownOnly = false; index = 0; revealed = false }, label = { Text("Saved") }, leadingIcon = { Icon(Icons.Rounded.Bookmark, null) })
                FilterChip(selected = knownOnly, onClick = { knownOnly = !knownOnly; savedOnly = false; index = 0; revealed = false }, label = { Text("Still learning") })
            }
        }
        item {
            OutlinedButton(onClick = onGenerate, enabled = !generating,
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Accent.copy(alpha = .55f))) {
                if (generating) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = Accent)
                else Icon(Icons.Rounded.AutoAwesome, null, tint = Accent)
                Spacer(Modifier.width(8.dp))
                Text(if (generating) "Generating…" else "Generate from PDFs", color = Accent)
            }
        }
        if (status.isNotBlank()) item { Text(status, color = Mint, fontSize = 12.sp) }
        item { Text("Add card", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        item {
            OutlinedTextField(front, { front = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("Question") }, singleLine = true,
                shape = RoundedCornerShape(14.dp), colors = fieldColors())
        }
        item {
            OutlinedTextField(back, { back = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("Answer") }, minLines = 2, maxLines = 4,
                shape = RoundedCornerShape(14.dp), colors = fieldColors())
        }
        item {
            Button(onClick = { onAdd(front.trim(), back.trim()); front = ""; back = "" },
                enabled = front.isNotBlank() && back.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(7.dp))
                Text("Add card")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Your cards", color = Color(0xFF17191D), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(items.size.toString(), color = Muted, fontSize = 13.sp)
            }
        }
        if (items.isEmpty()) {
            item { EmptyState("No cards yet", "Add a card or generate from a PDF.", Icons.Rounded.Style) }
        } else {
            val card = items[index.coerceIn(0, items.lastIndex)]
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("CARD ${index + 1} OF ${items.size}", color = Violet, fontSize = 10.sp, letterSpacing = 1.2.sp)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onSave(card.optString("id"), !card.optBoolean("saved")) }) {
                        Icon(if (card.optBoolean("saved")) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "Save card", tint = if (card.optBoolean("saved")) Gold else Muted)
                    }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 190.dp).clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = .07f)).border(1.dp, Color.White.copy(alpha = .2f), RoundedCornerShape(20.dp))
                        .clickable { revealed = !revealed }.padding(22.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Crossfade(targetState = revealed, animationSpec = tween(220), label = "cardFlip") { showAnswer ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (showAnswer) "ANSWER" else "QUESTION", color = Muted, fontSize = 11.sp)
                            Text(if (showAnswer) card.optString("body") else card.optString("title"),
                                fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp,
                                modifier = Modifier.padding(top = 14.dp))
                            Text("Tap to flip", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp))
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { index = if (index <= 0) items.lastIndex else index - 1; revealed = false }) { Text("Previous") }
                    Text("${index + 1} / ${items.size}", color = Muted, fontSize = 12.sp)
                    TextButton(onClick = { index = (index + 1) % items.size; revealed = false }) { Text("Next", color = Accent) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onKnown(card.optString("id"), !card.optBoolean("known")) }, modifier = Modifier.weight(1f)) {
                        Icon(if (card.optBoolean("known")) Icons.Rounded.CheckCircle else Icons.Rounded.Replay, null)
                        Spacer(Modifier.width(6.dp)); Text(if (card.optBoolean("known")) "Known" else "Mark known")
                    }
                    OutlinedButton(onClick = { index = (0 until items.size).random(); revealed = false }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle")
                    }
                }
            }
            item {
                TextButton(onClick = { onDelete(card.optString("id")); index = (index - 1).coerceAtLeast(0); revealed = false },
                    modifier = Modifier.fillMaxWidth()) { Text("Delete card", color = Muted) }
            }
        }
    }
}

@Composable
private fun PlannerScreen(tasks: JSONArray, onAdd: (String) -> Unit, onToggle: (String, Boolean) -> Unit, onDelete: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    val taskItems = (0 until tasks.length()).mapNotNull { tasks.optJSONObject(it) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Text("Revision planner", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), placeholder = { Text("e.g. Revise algebra") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
        Button(onClick = { onAdd(title.trim()); title = "" }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(7.dp)); Text("Add study task") }
        Text("YOUR TASKS", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
        if (taskItems.isEmpty()) EmptyState("No tasks yet", "Add a task to your planner.", Icons.Rounded.EventNote)

        else Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            taskItems.forEach { task ->
                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(15.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = task.optBoolean("done"), onCheckedChange = { onToggle(task.optString("id"), it) }, colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = Muted))
                    Text(task.optString("title"), Modifier.weight(1f), fontSize = 13.sp, color = if (task.optBoolean("done")) Muted else Color(0xFF17191D))
                    IconButton(onClick = { onDelete(task.optString("id")) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = Muted) }
                }
            }
        }
    }
}

@Composable
private fun SyllabusScreen(topics: JSONArray, onAdd: (String) -> Unit, onToggle: (String, Boolean) -> Unit, onDelete: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    val items = (0 until topics.length()).mapNotNull { topics.optJSONObject(it) }
    val completed = items.count { it.optBoolean("done") }
    val progress = if (items.isEmpty()) 0f else completed.toFloat() / items.size
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text("Syllabus tracker", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Text("Add chapters or individual topics, then mark each one complete as you study.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 5.dp))
        Column(Modifier.fillMaxWidth().padding(top = 16.dp).background(Panel, RoundedCornerShape(16.dp)).padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Coverage", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("$completed / ${items.size}", color = Mint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(8.dp).clip(CircleShape).background(Panel2)) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(Mint))
            }
            Text("${(progress * 100).toInt()}% completed", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
        }
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().padding(top = 16.dp), placeholder = { Text("Add a chapter or topic") }, singleLine = true, shape = RoundedCornerShape(13.dp), colors = fieldColors())
        Button(onClick = { onAdd(title.trim()); title = "" }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(7.dp)); Text("Add to syllabus")
        }
        if (items.isEmpty()) EmptyState("Syllabus is empty", "Add chapters or topics to start tracking coverage.", Icons.Rounded.MenuBook)
        else Column(Modifier.padding(top = 17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { topic ->
                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(13.dp)).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = topic.optBoolean("done"), onCheckedChange = { onToggle(topic.optString("id"), it) })
                    Text(topic.optString("title"), Modifier.weight(1f), fontSize = 13.sp, color = if (topic.optBoolean("done")) Muted else Accent)
                    IconButton(onClick = { onDelete(topic.optString("id")) }) { Icon(Icons.Rounded.DeleteOutline, "Remove topic", tint = Muted) }
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(taskCount: Int, onFlashcards: () -> Unit, onQuizzes: () -> Unit, onPlanner: () -> Unit, onSyllabus: () -> Unit, onSettings: () -> Unit, onProgress: () -> Unit, onExams: () -> Unit, onTools: () -> Unit, onSearch: () -> Unit, onReadiness: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Text("More", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        QuickRow("Flashcards", "Review your saved cards", Icons.Rounded.Style, onFlashcards)
        Spacer(Modifier.height(9.dp))
        QuickRow("MCQ practice", "AI-generated multiple-choice tests", Icons.Rounded.Quiz, onQuizzes)
        Spacer(Modifier.height(9.dp))
        QuickRow("Exams", "Three-hour full-chapter mock exam and PDF paper", Icons.Rounded.Description, onExams)
        Spacer(Modifier.height(9.dp))
        QuickRow("Revision planner", "$taskCount study tasks", Icons.Rounded.EventNote, onPlanner)
        Spacer(Modifier.height(9.dp))
        QuickRow("Syllabus tracker", "Track chapters and topics", Icons.Rounded.MenuBook, onSyllabus)
        Spacer(Modifier.height(9.dp))
        QuickRow("Study progress", "See your saved study activity", Icons.Rounded.Insights, onProgress)
        Spacer(Modifier.height(9.dp))
        QuickRow("Exam readiness", "Coverage and practice indicators", Icons.Rounded.Assessment) { onReadiness() }
        Spacer(Modifier.height(9.dp))
        QuickRow("Learning tools", "Ask PDFs, mind maps, formulas and diagrams", Icons.Rounded.AutoAwesome) { onTools() }
        Spacer(Modifier.height(9.dp))
        QuickRow("Global search", "Find anything in your study library", Icons.Rounded.Search) { onSearch() }
        Spacer(Modifier.height(9.dp))
        QuickRow("AI & privacy settings", "Manage your provider key and data", Icons.Rounded.Tune, onSettings)
    }
}

private fun pendulumBoard(): JSONObject = JSONObject()
    .put("title", "Simple pendulum")
    .put("elements", JSONArray()
        .put(JSONObject().put("type", "line").put("x1", 180).put("y1", 180).put("x2", 820).put("y2", 180))
        .put(JSONObject().put("type", "circle").put("cx", 500).put("cy", 180).put("r", 9))
        .put(JSONObject().put("type", "line").put("x1", 500).put("y1", 188).put("x2", 650).put("y2", 570))
        .put(JSONObject().put("type", "circle").put("cx", 650).put("cy", 570).put("r", 55))
        .put(JSONObject().put("type", "text").put("x", 520).put("y", 145).put("value", "Pivot"))
        .put(JSONObject().put("type", "text").put("x", 675).put("y", 360).put("value", "String"))
        .put(JSONObject().put("type", "text").put("x", 725).put("y", 590).put("value", "Bob"))
        .put(JSONObject().put("type", "line").put("x1", 650).put("y1", 650).put("x2", 650).put("y2", 820))
        .put(JSONObject().put("type", "text").put("x", 670).put("y", 790).put("value", "Gravity")))

private fun extractTutorResponse(raw: String): Pair<String, JSONObject?> {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return raw.trim() to null
    return runCatching {
        val result = JSONObject(raw.substring(start, end + 1))
        val explanation = result.optString("answer").trim()
        val board = result.optJSONObject("board")
        (explanation.ifBlank { raw.trim() }) to board?.takeIf { it.optJSONArray("elements")?.length()?.let { count -> count > 0 } == true }
    }.getOrElse { raw.trim() to null }
}

@Composable
private fun StudyWhiteboard(board: JSONObject) {
    val elements = board.optJSONArray("elements") ?: JSONArray()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Color(0xFFDDE2E8), RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text(board.optString("title").ifBlank { "Whiteboard" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Canvas(Modifier.fillMaxWidth().height(210.dp).padding(top = 8.dp)) {
            val scaleX = size.width / 1000f
            val scaleY = size.height / 1000f
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(35, 43, 54); textSize = 30f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            for (i in 0 until minOf(elements.length(), 24)) {
                val element = elements.optJSONObject(i) ?: continue
                when (element.optString("type").lowercase()) {
                    "line" -> drawLine(
                        Color(0xFF28313D),
                        Offset(element.optDouble("x1", 0.0).toFloat() * scaleX, element.optDouble("y1", 0.0).toFloat() * scaleY),
                        Offset(element.optDouble("x2", 0.0).toFloat() * scaleX, element.optDouble("y2", 0.0).toFloat() * scaleY),
                        strokeWidth = 2.5.dp.toPx()
                    )
                    "circle" -> drawCircle(
                        Color(0xFF6950A1),
                        radius = element.optDouble("r", 24.0).toFloat().coerceIn(5f, 180f) * minOf(scaleX, scaleY),
                        center = Offset(element.optDouble("cx", 0.0).toFloat() * scaleX, element.optDouble("cy", 0.0).toFloat() * scaleY),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
                    )
                    "text" -> {
                        val x = element.optDouble("x", 0.0).toFloat().coerceIn(0f, 1000f) * scaleX
                        val y = element.optDouble("y", 0.0).toFloat().coerceIn(25f, 980f) * scaleY
                        drawContext.canvas.nativeCanvas.drawText(element.optString("value").take(42), x, y, textPaint)
                    }
                }
            }
        }
    }
}

private fun readMcqChoices(detail: JSONObject): List<String> {
    val choices = detail.optJSONArray("choices") ?: detail.optJSONArray("options") ?: return emptyList()
    return (0 until choices.length()).map { index ->
        val option = choices.opt(index)
        when (option) {
            is JSONObject -> option.optString("text", option.optString("value", option.optString("option"))).trim()
            else -> option.toString().trim()
        }
    }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
}

private fun readMcqAnswer(detail: JSONObject, choices: List<String>): String {
    val answer = detail.optString("answer", detail.optString("correct_answer")).trim()
    val matched = choices.firstOrNull { it.equals(answer, true) }
    if (matched != null) return matched
    val label = answer.uppercase().removePrefix("OPTION ").trim().removeSuffix(".")
    return if (label.length == 1 && label[0] in 'A'..'D') choices.getOrNull(label[0] - 'A').orEmpty() else ""
}

@Composable
private fun QuizScreen(quizzes: JSONArray, refresh: Int, generating: Boolean, status: String, onGenerate: () -> Unit, onDelete: (String) -> Unit, onSave: (String, Boolean) -> Unit, onWrong: (String, Boolean) -> Unit, onResult: (String, Boolean) -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var selectedAnswer by remember { mutableStateOf("") }
    var score by remember { mutableIntStateOf(0) }
    var savedOnly by remember { mutableStateOf(false) }
    var wrongOnly by remember { mutableStateOf(false) }
    var celebration by remember { mutableIntStateOf(0) }
    val allQuestions = (0 until quizzes.length()).mapNotNull { quizzes.optJSONObject(it) }
    val questions = allQuestions.filter { (!savedOnly || it.optBoolean("saved")) && (!wrongOnly || it.optBoolean("wrong")) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 18.dp)) {
        Text("MCQ practice", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)) {
            FilterChip(selected = !savedOnly && !wrongOnly, onClick = { savedOnly = false; wrongOnly = false; index = 0; selectedAnswer = "" }, label = { Text("All") })
            FilterChip(selected = savedOnly, onClick = { savedOnly = true; wrongOnly = false; index = 0; selectedAnswer = "" }, label = { Text("Saved") }, leadingIcon = { Icon(Icons.Rounded.Bookmark, null) })
            FilterChip(selected = wrongOnly, onClick = { wrongOnly = !wrongOnly; savedOnly = false; index = 0; selectedAnswer = "" }, label = { Text("Retry mistakes") }, leadingIcon = { Icon(Icons.Rounded.Replay, null) })
        }
        Button(onClick = onGenerate, enabled = !generating, modifier = Modifier.fillMaxWidth().height(49.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) {
            if (generating) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = Ink) else Icon(Icons.Rounded.AutoAwesome, null)
            Spacer(Modifier.width(8.dp)); Text(if (generating) "Generating questions…" else "Generate quiz from PDFs")
        }
        if (status.isNotBlank()) Text(status, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 9.dp))
        if (questions.isEmpty()) {
            EmptyState(if (wrongOnly) "No mistakes to retry" else "No questions yet", if (wrongOnly) "Questions you answer incorrectly will appear here." else "Import a PDF and generate MCQs.", Icons.Rounded.Quiz)
        } else {
            val question = questions[index.coerceIn(0, questions.lastIndex)]
            val detail = runCatching { JSONObject(question.optString("body")) }.getOrElse { JSONObject() }
            val choices = readMcqChoices(detail)
            val correctAnswer = readMcqAnswer(detail, choices)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 9.dp)) {
                Text("QUESTION ${index + 1} OF ${questions.size}", color = Violet, fontSize = 10.sp, letterSpacing = 1.4.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onSave(question.optString("id"), !question.optBoolean("saved")) }) {
                    Icon(if (question.optBoolean("saved")) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "Save question", tint = if (question.optBoolean("saved")) Gold else Muted)
                }
            }
            Text(question.optString("title"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp, modifier = Modifier.padding(bottom = 14.dp))
            if (choices.size == 4 && correctAnswer.isNotBlank()) {
                choices.forEachIndexed { optionIndex, option ->
                    val chosen = selectedAnswer == option
                    val isCorrect = option == correctAnswer
                    val optionColor = when {
                        chosen && isCorrect -> Mint
                        chosen -> Coral
                        else -> Color(0xFF17191D)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(bottom = 9.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (chosen && isCorrect) Color(0xFFE4F5EF) else if (chosen) Color(0xFFFFF0EA) else Color.White)
                            .border(1.dp, if (chosen && isCorrect) Mint else if (chosen) Coral else Color(0xFF17191D).copy(alpha = .14f), RoundedCornerShape(14.dp))
                            .clickable(enabled = selectedAnswer.isBlank()) {
                                selectedAnswer = option
                                onWrong(question.optString("id"), !isCorrect)
                                onResult(question.optString("id"), isCorrect)
                                if (isCorrect) { score++; celebration++ }
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = chosen, onClick = null, enabled = selectedAnswer.isBlank(), colors = RadioButtonDefaults.colors(selectedColor = optionColor, unselectedColor = Muted))
                        Text(('A' + optionIndex).toString(), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 5.dp, end = 10.dp))
                        Text(option, color = optionColor, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                Text("The saved question has missing or invalid answer options. Generate a new quiz to replace it.", color = Coral, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF0EA), RoundedCornerShape(12.dp)).padding(12.dp))
            }
            if (selectedAnswer.isNotBlank()) {
                val correct = selectedAnswer == correctAnswer
                if (correct) {
                    ConfettiBurst(trigger = celebration)
                    Row(Modifier.fillMaxWidth().padding(top = 9.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFE4F5EF)).padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        listOf(Coral, Gold, Violet, Mint, Color(0xFF9CCBFF)).forEach { balloon ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(19.dp, 23.dp).clip(RoundedCornerShape(50)).background(balloon))
                                Box(Modifier.width(1.dp).height(8.dp).background(Color.White.copy(alpha = .6f)))
                            }
                        }
                        Text("Correct", color = Mint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else Text("Incorrect · Answer: $correctAnswer", color = Coral, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Text(detail.optString("explanation"), color = Muted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 5.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Score: $score", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Button(onClick = { if (index < questions.lastIndex) index++ else { index = 0; score = 0 }; selectedAnswer = "" }, enabled = selectedAnswer.isNotBlank() && choices.size == 4, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) {
                    Text(if (index == questions.lastIndex) "Restart quiz" else "Next question")
                }
            }
            TextButton(onClick = { onDelete(question.optString("id")) }, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Rounded.DeleteOutline, null, tint = Muted); Text("Remove question", color = Muted)
            }
        }
    }
}

@Composable
private fun ExamScreen(exam: JSONArray, generating: Boolean, status: String, onMake: () -> Unit, onExport: () -> Unit) {
    val paper = if (exam.length() > 0) runCatching { JSONObject(exam.optJSONObject(0)?.optString("body") ?: "{}") }.getOrElse { JSONObject() } else JSONObject()
    val questions = paper.optJSONArray("questions") ?: JSONArray()
    var answers by remember { mutableStateOf(mapOf<Int, String>()) }
    var started by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(10800) }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(started, submitted) {
        while (started && !submitted && secondsLeft > 0) { delay(1000); secondsLeft-- }
        if (secondsLeft == 0) { started = false; submitted = true }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text("Exams", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text("100-question exam paper", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Button(onClick = onMake, enabled = !generating, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)) {
            if (generating) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Rounded.Description, null)
            Spacer(Modifier.width(8.dp)); Text(if (generating) "Creating 100 questions…" else "Make exam paper")
        }
        if (status.isNotBlank()) Text(status, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 9.dp))
        if (questions.length() == 0) EmptyState("No exam paper yet", "Import a PDF, then make an exam paper.", Icons.Rounded.Description)
        else {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${questions.length()} questions", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f)); Text("3 hours", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("MCQ · True/False · Short · Written · Long answer", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
            Button(onClick = { if (started) { started = false } else { started = true; submitted = false; secondsLeft = 10800 } }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color.White)) { Text(if (started) "Pause exam" else "Start exam") }
            if (started) Text("Time left  %02d:%02d:%02d".format(secondsLeft / 3600, (secondsLeft % 3600) / 60, secondsLeft % 60), color = Coral, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            if (submitted) Text("Exam submitted.", color = Mint, modifier = Modifier.padding(top = 8.dp))
            var lastSection = ""
            for (i in 0 until questions.length()) {
                val q = questions.optJSONObject(i) ?: continue
                val type = q.optString("type", "Short answer")
                if (type != lastSection) { lastSection = type; Text(type, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Violet, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)) }
                Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).background(Panel, RoundedCornerShape(14.dp)).border(1.dp, Color(0xFF17191D).copy(alpha = .08f), RoundedCornerShape(14.dp)).padding(13.dp)) {
                    Text("${i + 1}. ${q.optString("question")}", fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp)
                    val choices = q.optJSONArray("choices")
                    if (choices != null) for (j in 0 until choices.length()) {
                        val choice = choices.optString(j)
                        Row(Modifier.fillMaxWidth().clickable(enabled = !submitted) { answers = answers + (i to choice) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = answers[i] == choice, onClick = { answers = answers + (i to choice) }, enabled = !submitted)
                            Text(choice, fontSize = 12.sp)
                        }
                    } else OutlinedTextField(value = answers[i] ?: "", onValueChange = { answers = answers + (i to it) }, enabled = !submitted, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = if (type == "Long answer") 4 else 2, colors = fieldColors())
                }
            }
            Button(onClick = { submitted = true; started = false }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)) { Text("Submit answers") }
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Icon(Icons.Rounded.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("Export question paper PDF") }
        }
    }
}

@Composable
private fun ConfettiBurst(trigger: Int) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) { progress.snapTo(0f); progress.animateTo(1f, animationSpec = tween(1250)) }
    }
    if (trigger > 0 && progress.value < 1f) {
        Canvas(Modifier.fillMaxWidth().height(145.dp)) {
            val t = progress.value
            val colors = listOf(Coral, Gold, Violet, Mint, Color(0xFF5C9CE6), Color(0xFFEC75A8))
            for (i in 0 until 110) {
                val angle = (i * 137.5 * Math.PI / 180.0)
                val spread = 0.22f + (i % 9) * 0.095f
                val x = size.width * 0.5f + kotlin.math.cos(angle).toFloat() * size.width * spread * t
                val y = -8f + t * (size.height + (i % 5) * 15f) + kotlin.math.sin(angle).toFloat() * 22f * t
                if (y < size.height + 12f) drawCircle(colors[i % colors.size], radius = 2.5f + (i % 3), center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun ProgressScreen(documentCount: Int, cardCount: Int, quizCount: Int, tasks: JSONArray) {
    val total = tasks.length()
    var completed = 0
    for (i in 0 until total) if (tasks.optJSONObject(i)?.optBoolean("done") == true) completed++
    val ratio = if (total == 0) 0f else completed.toFloat() / total.toFloat()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Text("Progress", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Materials", documentCount.toString(), Icons.Rounded.FolderOpen, Modifier.weight(1f))
            StatCard("Flashcards", cardCount.toString(), Icons.Rounded.Style, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Tasks done", "$completed / $total", Icons.Rounded.TaskAlt, Modifier.weight(1f))
            StatCard("MCQs", quizCount.toString(), Icons.Rounded.Quiz, Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().padding(top = 18.dp).background(Panel, RoundedCornerShape(18.dp)).border(1.dp, Color(0xFF17191D).copy(alpha = .07f), RoundedCornerShape(18.dp)).padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tasks completed", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text("${(ratio * 100).toInt()}%", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(7.dp).background(Panel2, CircleShape)) {
                Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().background(Brush.horizontalGradient(listOf(Accent, Mint)), CircleShape))
            }
            Text(if (total == 0) "Add a revision task to start tracking completion." else "$completed of $total planned tasks completed.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        }
        Text("These numbers come from your imported PDFs, flashcards, MCQs and planner. They are not exam scores.", color = Muted, fontSize = 10.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.fillMaxWidth().padding(top = 35.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(54.dp).background(Panel2, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Accent, modifier = Modifier.size(25.dp)) }
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 13.dp))
        Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 5.dp))
    }
}
@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF17191D).copy(alpha = .10f),
    focusedTextColor = Color(0xFF17191D), unfocusedTextColor = Color(0xFF17191D),
    focusedPlaceholderColor = Muted, unfocusedPlaceholderColor = Muted
)

private suspend fun askWithFallback(provider: String, keys: List<String>, prompt: String, studyContext: String): String {
    if (keys.isEmpty()) throw Exception("Add at least one $provider API key in Settings.")
    var lastError: Exception? = null
    for (key in keys) {
        try { return askProvider(provider, key, prompt, studyContext) }
        catch (e: Exception) {
            lastError = e
            val message = e.message.orEmpty().lowercase()
            if (!message.contains("429") && !message.contains("rate") && !message.contains("quota") && !message.contains("temporar") && !message.contains("503")) throw e
        }
    }
    throw Exception("All saved $provider keys are currently unavailable or rate-limited. ${lastError?.message ?: ""}")
}

private suspend fun askProvider(provider: String, apiKey: String, prompt: String, studyContext: String): String = withContext(Dispatchers.IO) {
    val system = "You are Mochi, a friendly cat study tutor for school students in grades 1–12. Explain clearly and accurately in simple language. When a small diagram would genuinely help, include a lightweight whiteboard drawing made from vector elements, not an image. Return only a JSON object with answer (string) and board (object or null). A board object has title and elements. Each element is either {type:'line',x1:0,y1:0,x2:0,y2:0}, {type:'circle',cx:0,cy:0,r:30}, or {type:'text',x:0,y:0,value:'label'}. Coordinates use a 0 to 1000 canvas. Keep to 12 elements, label important parts, and use board:null when a drawing adds no value. For a pendulum, draw its support, string, bob, and labels. Use this study material when relevant:\n\n${studyContext.ifBlank { "No documents have been added yet." }}"
    val endpoint: String
    val body: JSONObject
    when (provider) {
        "OpenAI" -> {
            endpoint = "https://api.openai.com/v1/chat/completions"
            body = JSONObject().put("model", "gpt-4o-mini").put("temperature", 0.5)
                .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system)).put(JSONObject().put("role", "user").put("content", prompt)))
        }
        "Gemini" -> {
            endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
            body = JSONObject().put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        }
        else -> {
            endpoint = "https://api.groq.com/openai/v1/chat/completions"
            body = JSONObject().put("model", "openai/gpt-oss-20b").put("temperature", 0.5)
                .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system)).put(JSONObject().put("role", "user").put("content", prompt)))
        }
    }
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"; connectTimeout = 20000; readTimeout = 60000; doOutput = true
        if (provider != "Gemini") setRequestProperty("Authorization", "Bearer $apiKey")
        setRequestProperty("Content-Type", "application/json")
    }
    try {
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) throw Exception("$provider request failed (${connection.responseCode}): " + (runCatching { JSONObject(response).optJSONObject("error")?.optString("message") }.getOrNull() ?: response.take(180)))
        val json = JSONObject(response)
        if (provider == "Gemini") json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        else json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    } finally { connection.disconnect() }
}
