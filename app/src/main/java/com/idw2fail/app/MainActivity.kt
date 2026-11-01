package com.idw2fail.app

import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.request.ImageRequest
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private val Ink = Color(0xFF050505)
private val Panel = Color(0xFF141414)
private val Panel2 = Color(0xFF202020)
private val Muted = Color(0xFFA3A3A3)
private val Accent = Color(0xFFFFFFFF)
private val Mint = Color(0xFFD0D0D0)
private const val BG_GIF = "https://i.ibb.co/VWgztGwR/d895c222e5fe92cb334de04e6d8f6828.gif"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        setContent { IdwTheme { IdwApp() } }
    }
}

@Composable
private fun IdwTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, primary = Accent, secondary = Mint,
        onBackground = Color.White, onSurface = Color.White
    ), content = content)
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
    fun notes(): String = read().optString("notes", "")
    fun saveNotes(value: String) { val d = read(); d.put("notes", value); write(d) }
    fun addDocument(name: String, text: String) {
        val d = read(); val list = d.optJSONArray("documents") ?: JSONArray()
        list.put(JSONObject().put("id", UUID.randomUUID().toString()).put("name", name).put("text", text).put("createdAt", System.currentTimeMillis()))
        d.put("documents", list); write(d)
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
    fun get(): String = prefs.getString("groq_key", "") ?: ""
    fun save(value: String) { prefs.edit().putString("groq_key", value.trim()).apply() }
}

@Composable
private fun IdwApp() {
    val context = LocalContext.current
    val data = remember { LocalData(context) }
    val vault = remember { KeyVault(context) }
    var page by remember { mutableStateOf("Home") }
    var refresh by remember { mutableIntStateOf(0) }
    var notes by remember { mutableStateOf(data.notes()) }
    var apiKey by remember { mutableStateOf(vault.get()) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
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
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val raw = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw Exception("Could not read backup file.")
                }
                data.restoreJson(raw); notes = data.notes(); refresh++; status = "Backup restored on this device."
            } catch (e: Exception) { status = e.message ?: "Backup restore failed." }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; status = "Reading your PDF…"
            try {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "Study material.pdf"
                val extracted = withContext(Dispatchers.IO) {
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
                        PDDocument.load(temp).use { pdf ->
                            if (pdf.numberOfPages > 100) throw Exception("This PDF has \${pdf.numberOfPages} pages. Please use a file with 100 pages or fewer.")
                            val stripper = PDFTextStripper().apply { startPage = 1; endPage = pdf.numberOfPages }
                            stripper.getText(pdf).take(120000)
                        }
                    } finally { temp.delete() }
                }
                if (extracted.isBlank()) throw Exception("No selectable text was found. This may be a scanned PDF.")
                data.addDocument(name, extracted); refresh++
                status = "PDF added · \${extracted.length} characters extracted"
            } catch (e: Exception) { status = "Couldn't read that PDF: \${e.message ?: "Unsupported or damaged PDF."}" }
            finally { busy = false }
        }
    }
    val gifLoader = remember {
        ImageLoader.Builder(context).components { add(GifDecoder.Factory()) }.build()
    }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(BG_GIF).crossfade(true).build(),
            imageLoader = gifLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.32f)
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f)))
    }
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xD9141414), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(24.dp)),
                containerColor = Color.Transparent,
                contentColor = Color.White
            ) {
                listOf("Home", "Library", "Study", "Practice", "More").forEach { item ->
                    val icon = when (item) {
                        "Home" -> Icons.Rounded.Home
                        "Library" -> Icons.Rounded.FolderOpen
                        "Study" -> Icons.Rounded.AutoAwesome
                        "Practice" -> Icons.Rounded.Style
                        else -> Icons.Rounded.MoreHoriz
                    }
                    NavigationBarItem(selected = page == item, onClick = { page = item },
                        icon = { Icon(icon, null) }, label = { Text(if (item == "Practice") "Flashcards" else item, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent,
                            indicatorColor = Color.White.copy(alpha = .12f), unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("idw2failtwin", fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
            }
            Crossfade(targetState = page, animationSpec = tween(260), label = "page") { currentPage ->
            when (currentPage) {
                "Home" -> HomeScreen(docs.length(), data.entries("flashcards").length()) { page = it }
                "Library" -> LibraryScreen(docs, busy, status, onAdd = { picker.launch(arrayOf("application/pdf")) }, onDelete = { data.removeDocument(it); refresh++ })
                "Study" -> StudyScreen(question, { question = it }, answer, busy, status, onAsk = {
                    if (apiKey.isBlank()) { page = "Settings"; status = "Add your Groq API key first." }
                    else scope.launch {
                        busy = true; answer = ""; status = "Thinking…"
                        try {
                            val contextText = buildString { for (i in 0 until docs.length()) append(docs.optJSONObject(i)?.optString("text", "")).append("\n") }.take(14000)
                            answer = askGroq(apiKey, question, contextText); status = ""
                        } catch (e: Exception) { status = e.message ?: "AI request failed." }
                        busy = false
                    }
                })
                "Practice" -> PracticeScreen(
                    cards = data.entries("flashcards"), refresh = refresh, generating = busy, status = status,
                    onGenerate = {
                        if (apiKey.isBlank()) { page = "Settings"; status = "Add your Groq API key first." }
                        else if (docs.length() == 0) status = "Import a PDF first so the AI has material to study."
                        else scope.launch {
                            busy = true; status = "Creating flashcards from your PDFs…"
                            try {
                                val contextText = buildString { for (i in 0 until docs.length()) append(docs.optJSONObject(i)?.optString("text", "")).append("\n") }.take(14000)
                                val raw = askGroq(apiKey, "Create 8 useful school revision flashcards from the study material. Return ONLY a valid JSON array. Each item must have exactly two string fields: question and answer. Do not include markdown fences or any text outside the JSON array.", contextText)
                                val start = raw.indexOf('['); val end = raw.lastIndexOf(']')
                                if (start < 0 || end <= start) throw Exception("The AI returned an invalid flashcard format. Try again.")
                                val generated = JSONArray(raw.substring(start, end + 1))
                                var added = 0
                                for (i in 0 until generated.length()) {
                                    val item = generated.optJSONObject(i) ?: continue
                                    val q = item.optString("question").trim(); val a = item.optString("answer").trim()
                                    if (q.isNotBlank() && a.isNotBlank()) { data.addEntry("flashcards", q, a); added++ }
                                }
                                refresh++; status = if (added > 0) "$added flashcards added to your deck." else "No usable cards were returned. Try again."
                            } catch (e: Exception) { status = e.message ?: "Flashcard generation failed." }
                            busy = false
                        }
                    },
                    onAdd = { front, back -> data.addEntry("flashcards", front, back); refresh++ },
                    onDelete = { data.removeEntry("flashcards", it); refresh++ }
                )
                "More" -> MoreScreen(
                    taskCount = data.entries("tasks").length(),
                    onNotes = { page = "Notes" }, onPlanner = { page = "Planner" }, onSettings = { page = "Settings" }, onProgress = { page = "Progress" }, onQuizzes = { page = "Quizzes" }
                )
                "Quizzes" -> QuizScreen(
                    quizzes = data.entries("quizzes"), refresh = refresh, generating = busy, status = status,
                    onGenerate = {
                        if (apiKey.isBlank()) { page = "Settings"; status = "Add your Groq API key first." }
                        else if (docs.length() == 0) status = "Import a PDF first so the AI has material to study."
                        else scope.launch {
                            busy = true; status = "Generating your practice quiz…"
                            try {
                                val contextText = buildString { for (i in 0 until docs.length()) append(docs.optJSONObject(i)?.optString("text", "")).append("\n") }.take(14000)
                                val raw = askGroq(apiKey, "Create 10 school-level multiple-choice questions from the study material. Return ONLY a valid JSON array. Each item must contain question (string), choices (array of exactly four short strings), answer (one exact choice string), and explanation (string). Exactly one correct answer per question. No markdown or text outside JSON.", contextText)
                                val start = raw.indexOf('['); val end = raw.lastIndexOf(']')
                                if (start < 0 || end <= start) throw Exception("The AI returned an invalid quiz format. Try again.")
                                val generated = JSONArray(raw.substring(start, end + 1))
                                var added = 0
                                for (i in 0 until generated.length()) {
                                    val item = generated.optJSONObject(i) ?: continue
                                    if (item.optString("question").isNotBlank() && item.optJSONArray("choices")?.length() == 4 && item.optString("answer").isNotBlank()) {
                                        data.addEntry("quizzes", item.optString("question"), item.toString()); added++
                                    }
                                }
                                refresh++; status = if (added > 0) "$added quiz questions added." else "No usable questions were returned. Try again."
                            } catch (e: Exception) { status = e.message ?: "Quiz generation failed." }
                            busy = false
                        }
                    },
                    onDelete = { data.removeEntry("quizzes", it); refresh++ }
                )
                "Progress" -> ProgressScreen(
                    documentCount = docs.length(),
                    cardCount = data.entries("flashcards").length(),
                    tasks = data.entries("tasks"),
                    notesLength = notes.length
                )
                "Notes" -> NotesScreen(notes, { notes = it }, { data.saveNotes(notes); status = "Saved on this device." }, status)
                "Planner" -> PlannerScreen(
                    tasks = data.entries("tasks"),
                    onAdd = { title -> data.addEntry("tasks", title, ""); refresh++ },
                    onToggle = { id, done -> data.setDone("tasks", id, done); refresh++ },
                    onDelete = { data.removeEntry("tasks", it); refresh++ }
                )
                else -> SettingsScreen(
                    apiKey,
                    { apiKey = it; vault.save(it); status = "API key saved securely on this device." },
                    status,
                    onExport = { exportPicker.launch("idw2fail-backup.json") },
                    onImport = { backupPicker.launch(arrayOf("application/json", "text/json")) }
                )
            }
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
                    .background(Color.White.copy(alpha = .07f), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = .2f), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Text("Study", fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
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
    Column(modifier.background(Color.White.copy(alpha = .055f), RoundedCornerShape(20.dp)).border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(20.dp)).padding(16.dp)) {
        Icon(icon, null, tint = Accent); Spacer(Modifier.height(15.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold); Text(title, color = Muted, fontSize = 11.sp)
    }
}
@Composable
private fun QuickRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .045f), RoundedCornerShape(17.dp)).border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(17.dp)).clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp); Text(subtitle, color = Muted, fontSize = 11.sp) }
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun LibraryScreen(docs: JSONArray, busy: Boolean, status: String, onAdd: () -> Unit, onDelete: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Text("Library", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Button(onClick = onAdd, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Import a PDF", fontWeight = FontWeight.SemiBold)
        }
        if (status.isNotBlank()) Text(status, color = Mint, fontSize = 11.sp, modifier = Modifier.padding(vertical = 10.dp))
        if (docs.length() == 0) EmptyState("Your library is empty", "Add a textbook or class PDF to start building your study space.", Icons.Rounded.FolderOpen)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            items((0 until docs.length()).mapNotNull { docs.optJSONObject(it) }) { doc ->
                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PictureAsPdf, null, tint = Accent); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(doc.optString("name"), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("${doc.optString("text").length} characters extracted", color = Muted, fontSize = 10.sp)
                    }
                    IconButton(onClick = { onDelete(doc.optString("id")) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = Muted) }
                }
            }
        }
    }
}

@Composable
private fun StudyScreen(question: String, onQuestion: (String) -> Unit, answer: String, busy: Boolean, status: String, onAsk: () -> Unit) {
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
                Text("Answer", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(answer, fontSize = 14.sp, lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp))
            }
        }
    }
}

@Composable
private fun NotesScreen(notes: String, onNotes: (String) -> Unit, onSave: () -> Unit, status: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Your notes", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Text("A quiet place for everything worth remembering.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
        OutlinedTextField(value = notes, onValueChange = onNotes, modifier = Modifier.weight(1f).fillMaxWidth(), placeholder = { Text("Write your revision notes here…") }, shape = RoundedCornerShape(18.dp), colors = fieldColors())
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(50.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) { Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text("Save notes") }
        if (status.isNotBlank()) Text(status, color = Mint, fontSize = 11.sp)
    }
}

@Composable
private fun SettingsScreen(apiKey: String, onSave: (String) -> Unit, status: String, onExport: () -> Unit, onImport: () -> Unit) {
    var key by remember(apiKey) { mutableStateOf(apiKey) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 18.dp)) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Text("AI provider", color = Muted, fontSize = 13.sp)
        Text("Groq API key", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        Text("Get a key from console.groq.com. It is encrypted and saved only on this device.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 5.dp, bottom = 12.dp))
        OutlinedTextField(value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("gsk_…") }, singleLine = true, shape = RoundedCornerShape(15.dp), colors = fieldColors())
        Button(onClick = { onSave(key) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(50.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) { Icon(Icons.Rounded.Lock, null); Spacer(Modifier.width(8.dp)); Text("Save securely") }
        if (status.isNotBlank()) Text(status, color = Mint, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(24.dp))
        Text("Data", color = Muted, fontSize = 13.sp)
        Text("Study data is stored in a private JSON file inside the app. Imported PDF text is stored locally so your tutor can reference it. When you ask the AI a question, relevant text is sent to Groq.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Rounded.FileDownload, null); Spacer(Modifier.width(6.dp)); Text("Export backup") }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Rounded.FileUpload, null); Spacer(Modifier.width(6.dp)); Text("Restore backup") }
        }
        Text("Restoring replaces the current local study data. Export a backup first if you want to keep it.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
        Text("Never share your API key. Anyone with access to your unlocked phone may be able to use the configured AI account.", color = Color.White.copy(alpha = .78f), fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 14.dp))
    }
}


@Composable
private fun PracticeScreen(cards: JSONArray, refresh: Int, generating: Boolean, status: String, onGenerate: () -> Unit, onAdd: (String, String) -> Unit, onDelete: (String) -> Unit) {
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var index by remember(refresh) { mutableIntStateOf(0) }
    var revealed by remember(refresh) { mutableStateOf(false) }
    val items = (0 until cards.length()).mapNotNull { cards.optJSONObject(it) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("Flashcards", fontSize = 28.sp, fontWeight = FontWeight.SemiBold) }
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
                Text("Your cards", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(items.size.toString(), color = Muted, fontSize = 13.sp)
            }
        }
        if (items.isEmpty()) {
            item { EmptyState("No cards yet", "Add a card or generate from a PDF.", Icons.Rounded.Style) }
        } else {
            val card = items[index.coerceIn(0, items.lastIndex)]
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
                TextButton(onClick = { onDelete(card.optString("id")); index = 0; revealed = false },
                    modifier = Modifier.fillMaxWidth()) { Text("Delete card", color = Muted) }
            }
        }
    }
}

@Composable
private fun PlannerScreen(tasks: JSONArray, onAdd: (String) -> Unit, onToggle: (String, Boolean) -> Unit, onDelete: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    val taskItems = (0 until tasks.length()).mapNotNull { tasks.optJSONObject(it) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Revision planner", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Text("Keep your next study steps clear and manageable.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp, bottom = 18.dp))
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), placeholder = { Text("e.g. Revise algebra") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
        Button(onClick = { onAdd(title.trim()); title = "" }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(7.dp)); Text("Add study task") }
        Text("YOUR TASKS", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
        if (taskItems.isEmpty()) EmptyState("Nothing planned yet", "Add one small revision task to get started.", Icons.Rounded.EventNote)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(taskItems, key = { it.optString("id") }) { task ->
                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(15.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = task.optBoolean("done"), onCheckedChange = { onToggle(task.optString("id"), it) }, colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = Muted))
                    Text(task.optString("title"), Modifier.weight(1f), fontSize = 13.sp, color = if (task.optBoolean("done")) Muted else Color.White)
                    IconButton(onClick = { onDelete(task.optString("id")) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = Muted) }
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(taskCount: Int, onNotes: () -> Unit, onPlanner: () -> Unit, onSettings: () -> Unit, onProgress: () -> Unit, onQuizzes: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Text("More", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        QuickRow("Practice quizzes", "AI-generated multiple-choice tests", Icons.Rounded.Quiz, onQuizzes)
        Spacer(Modifier.height(9.dp))
        QuickRow("Revision planner", "$taskCount study tasks", Icons.Rounded.EventNote, onPlanner)
        Spacer(Modifier.height(9.dp))
        QuickRow("Study progress", "See your saved study activity", Icons.Rounded.Insights, onProgress)
        Spacer(Modifier.height(9.dp))
        QuickRow("AI & privacy settings", "Manage your provider key and data", Icons.Rounded.Tune, onSettings)
    }
}

@Composable
private fun QuizScreen(quizzes: JSONArray, refresh: Int, generating: Boolean, status: String, onGenerate: () -> Unit, onDelete: (String) -> Unit) {
    var index by remember(refresh) { mutableIntStateOf(0) }
    var selectedAnswer by remember(refresh) { mutableStateOf("") }
    var score by remember(refresh) { mutableIntStateOf(0) }
    val questions = (0 until quizzes.length()).mapNotNull { quizzes.optJSONObject(it) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 18.dp)) {
        Text("Quizzes", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        
        Button(onClick = onGenerate, enabled = !generating, modifier = Modifier.fillMaxWidth().height(49.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) {
            if (generating) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = Ink) else Icon(Icons.Rounded.AutoAwesome, null)
            Spacer(Modifier.width(8.dp)); Text(if (generating) "Generating questions…" else "Generate quiz from PDFs")
        }
        if (status.isNotBlank()) Text(status, color = Mint, fontSize = 11.sp, modifier = Modifier.padding(top = 9.dp))
        if (questions.isEmpty()) {
            EmptyState("No quiz questions yet", "Import a PDF, then generate a multiple-choice quiz from its content.", Icons.Rounded.Quiz)
        } else {
            val question = questions[index.coerceIn(0, questions.lastIndex)]
            val detail = runCatching { JSONObject(question.optString("body")) }.getOrElse { JSONObject() }
            val choices = detail.optJSONArray("choices") ?: JSONArray()
            val correctAnswer = detail.optString("answer")
            Text("QUESTION ${index + 1} OF ${questions.size}", color = Accent, fontSize = 10.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
            Text(question.optString("title"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp, modifier = Modifier.padding(bottom = 14.dp))
            for (i in 0 until choices.length()) {
                val option = choices.optString(i)
                val chosen = selectedAnswer == option
                val isCorrect = option == correctAnswer
                OutlinedButton(
                    onClick = { if (selectedAnswer.isBlank()) { selectedAnswer = option; if (isCorrect) score++ } },
                    enabled = selectedAnswer.isBlank(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (chosen && isCorrect) Mint else if (chosen) Color(0xFFFFC78C) else Color.White.copy(alpha = .12f))
                ) {
                    Text(option, color = if (chosen && isCorrect) Mint else if (chosen) Color(0xFFFFC78C) else Color.White)
                }
            }
            if (selectedAnswer.isNotBlank()) {
                val correct = selectedAnswer == correctAnswer
                Text(if (correct) "Correct" else "Not quite. Correct answer: $correctAnswer", color = if (correct) Mint else Color(0xFFFFC78C), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Text(detail.optString("explanation"), color = Muted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 5.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Score: $score", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Button(onClick = { if (index < questions.lastIndex) index++ else { index = 0; score = 0 }; selectedAnswer = "" }, enabled = selectedAnswer.isNotBlank(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) {
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
private fun ProgressScreen(documentCount: Int, cardCount: Int, tasks: JSONArray, notesLength: Int) {
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
            StatCard("Note length", "$notesLength", Icons.Rounded.Notes, Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().padding(top = 18.dp).background(Panel, RoundedCornerShape(18.dp)).border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(18.dp)).padding(17.dp)) {
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
        Text("These numbers come from your local notes, flashcards, imported PDFs and planner. They are not exam scores.", color = Muted, fontSize = 10.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 16.dp))
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
    focusedBorderColor = Accent, unfocusedBorderColor = Color.White.copy(alpha = .12f),
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedPlaceholderColor = Muted, unfocusedPlaceholderColor = Muted
)

private suspend fun askGroq(apiKey: String, prompt: String, studyContext: String): String = withContext(Dispatchers.IO) {
    val connection = (URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"; connectTimeout = 20000; readTimeout = 60000; doOutput = true
        setRequestProperty("Authorization", "Bearer $apiKey"); setRequestProperty("Content-Type", "application/json")
    }
    try {
        val body = JSONObject().put("model", "openai/gpt-oss-20b").put("temperature", 0.5)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", "You are a kind, clear school study tutor for grades 1–12. Explain in simple steps, use examples, and never pretend information is in the student's notes if it is not. Use this provided study material when relevant:\n\n${studyContext.ifBlank { "No documents have been added yet." }}"))
                .put(JSONObject().put("role", "user").put("content", prompt)))
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) throw Exception(JSONObject(response).optJSONObject("error")?.optString("message") ?: "AI request failed (${connection.responseCode}).")
        JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    } finally { connection.disconnect() }
}
