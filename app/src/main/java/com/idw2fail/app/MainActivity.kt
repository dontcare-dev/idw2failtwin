package com.idw2fail.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import org.apache.pdfbox.android.PDFBoxResourceLoader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private val Ink = Color(0xFF090A0E)
private val Panel = Color(0xFF151820)
private val Panel2 = Color(0xFF1B202B)
private val Muted = Color(0xFF9299A8)
private val Accent = Color(0xFFB7A5FF)
private val Mint = Color(0xFF9BE7D2)

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
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; status = "Reading your PDF…"
            try {
                val extracted = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        PDDocument.load(input).use { pdf -> PDFTextStripper().getText(pdf) }
                    } ?: ""
                }
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Study material.pdf"
                data.addDocument(name, extracted); refresh++
                status = "PDF added · ${extracted.length} characters extracted"
            } catch (e: Exception) { status = "Couldn't read that PDF: ${e.message}" }
            busy = false
        }
    }
    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF10131A), contentColor = Color.White) {
                listOf("Home", "Library", "Study", "Practice", "More").forEach { item ->
                    val icon = when (item) {
                        "Home" -> Icons.Rounded.Home
                        "Library" -> Icons.Rounded.FolderOpen
                        "Study" -> Icons.Rounded.AutoAwesome
                        "Practice" -> Icons.Rounded.Style
                        else -> Icons.Rounded.MoreHoriz
                    }
                    NavigationBarItem(selected = page == item, onClick = { page = item },
                        icon = { Icon(icon, null) }, label = { Text(item, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent,
                            indicatorColor = Color(0xFF28243A), unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(Ink)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).background(Brush.linearGradient(listOf(Accent, Mint)), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Bolt, null, tint = Ink)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("idw2fail", fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                    Text("YOUR PERSONAL STUDY SPACE", fontSize = 8.sp, color = Muted, letterSpacing = 1.6.sp)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(36.dp).border(1.dp, Color.White.copy(alpha = .1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Person, null, tint = Muted, modifier = Modifier.size(19.dp))
                }
            }
            when (page) {
                "Home" -> HomeScreen(docs.length(), notes.isNotBlank()) { page = it }
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
                    cards = data.entries("flashcards"), refresh = refresh,
                    onAdd = { front, back -> data.addEntry("flashcards", front, back); refresh++ },
                    onDelete = { data.removeEntry("flashcards", it); refresh++ }
                )
                "More" -> MoreScreen(
                    taskCount = data.entries("tasks").length(),
                    onNotes = { page = "Notes" }, onPlanner = { page = "Planner" }, onSettings = { page = "Settings" }
                )
                "Notes" -> NotesScreen(notes, { notes = it }, { data.saveNotes(notes); status = "Saved on this device." }, status)
                "Planner" -> PlannerScreen(
                    tasks = data.entries("tasks"),
                    onAdd = { title -> data.addEntry("tasks", title, ""); refresh++ },
                    onToggle = { id, done -> data.setDone("tasks", id, done); refresh++ },
                    onDelete = { data.removeEntry("tasks", it); refresh++ }
                )
                else -> SettingsScreen(apiKey, { apiKey = it; vault.save(it); status = "API key saved securely on this device." }, status)
            }
        }
    }
}

@Composable
private fun HomeScreen(documentCount: Int, hasNotes: Boolean, navigate: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("YOUR STUDY SPACE", color = Muted, fontSize = 10.sp, letterSpacing = 1.8.sp)
            Spacer(Modifier.height(8.dp))
            Text("Let's make this\nexam feel easier.", fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp)
            Text("Your materials. Your pace. Your AI study partner.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        item {
            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF29243D), Color(0xFF171D29), Color(0xFF142A2B))), RoundedCornerShape(26.dp)).border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(26.dp)).padding(20.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = Accent); Spacer(Modifier.width(8.dp))
                        Text("AI STUDY ROOM", color = Accent, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Understand it.\nRemember it.", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, lineHeight = 29.sp, modifier = Modifier.padding(top = 15.dp))
                    Text("Ask questions using your own study material.", color = Color.White.copy(alpha = .68f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
                    Button(onClick = { navigate("Study") }, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink), shape = RoundedCornerShape(14.dp)) {
                        Text("Open study room", fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.ArrowForward, null, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
        item {
            Text("YOUR SPACE", color = Muted, fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Materials", documentCount.toString(), Icons.Rounded.FolderOpen, Modifier.weight(1f))
                StatCard("Notes", if (hasNotes) "Started" else "Empty", Icons.Rounded.EditNote, Modifier.weight(1f))
            }
        }
        item {
            Text("QUICK ACCESS", color = Muted, fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                QuickRow("Add your textbooks", "Import PDF files from your phone", Icons.Rounded.UploadFile) { navigate("Library") }
                QuickRow("Keep your notes", "Save revision points locally", Icons.Rounded.Notes) { navigate("Notes") }
                QuickRow("Connect your AI", "Add your personal API key", Icons.Rounded.Key) { navigate("Settings") }
            }
        }
        item { Text("Study data stays on this device unless you send content to your chosen AI provider.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 12.dp)) }
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.background(Panel, RoundedCornerShape(20.dp)).border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(20.dp)).padding(16.dp)) {
        Icon(icon, null, tint = Accent); Spacer(Modifier.height(15.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold); Text(title, color = Muted, fontSize = 11.sp)
    }
}
@Composable
private fun QuickRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(17.dp)).clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp); Text(subtitle, color = Muted, fontSize = 11.sp) }
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun LibraryScreen(docs: JSONArray, busy: Boolean, status: String, onAdd: () -> Unit, onDelete: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Your library", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Text("Keep your textbooks and revision material together.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp, bottom = 18.dp))
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
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("AI study room", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Text("Ask for explanations grounded in your imported PDFs.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
        OutlinedTextField(value = question, onValueChange = onQuestion, modifier = Modifier.fillMaxWidth(), placeholder = { Text("What topic are you stuck on?") }, minLines = 3, shape = RoundedCornerShape(18.dp), colors = fieldColors())
        Button(onClick = onAsk, enabled = !busy && question.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(50.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Ink) else Icon(Icons.Rounded.AutoAwesome, null)
            Spacer(Modifier.width(8.dp)); Text(if (busy) "Working…" else "Explain this to me")
        }
        if (status.isNotBlank()) Text(status, color = Mint, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        if (answer.isNotBlank()) {
            Text("YOUR EXPLANATION", color = Accent, fontSize = 10.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            LazyColumn(Modifier.fillMaxSize().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp)) { item { Text(answer, fontSize = 13.sp, lineHeight = 21.sp) } }
        } else EmptyState("A little help goes a long way", "Your AI tutor can explain concepts, simplify difficult ideas and help you revise.", Icons.Rounded.Lightbulb)
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
private fun SettingsScreen(apiKey: String, onSave: (String) -> Unit, status: String) {
    var key by remember(apiKey) { mutableStateOf(apiKey) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Settings", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Text("Your app, your device, your AI provider.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp, bottom = 20.dp))
        Text("AI PROVIDER", color = Accent, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        Text("Groq API key", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        Text("Get a key from console.groq.com. It is encrypted and saved only on this device.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 5.dp, bottom = 12.dp))
        OutlinedTextField(value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("gsk_…") }, singleLine = true, shape = RoundedCornerShape(15.dp), colors = fieldColors())
        Button(onClick = { onSave(key) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(50.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) { Icon(Icons.Rounded.Lock, null); Spacer(Modifier.width(8.dp)); Text("Save securely") }
        if (status.isNotBlank()) Text(status, color = Mint, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(24.dp))
        Text("LOCAL DATA", color = Accent, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        Text("Study data is stored in a private JSON file inside the app. Imported PDF text is stored locally so your tutor can reference it. When you ask the AI a question, relevant text is sent to Groq.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 10.dp))
        Text("Never share your API key. Anyone with access to your unlocked phone may be able to use the configured AI account.", color = Color(0xFFFFC78C), fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 14.dp))
    }
}


@Composable
private fun PracticeScreen(cards: JSONArray, refresh: Int, onAdd: (String, String) -> Unit, onDelete: (String) -> Unit) {
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var index by remember(refresh) { mutableIntStateOf(0) }
    var revealed by remember(refresh) { mutableStateOf(false) }
    val items = (0 until cards.length()).mapNotNull { cards.optJSONObject(it) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Practice", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Text("Build a revision deck and test what you remember.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
        Text("CREATE A FLASHCARD", color = Accent, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(front, { front = it }, Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = { Text("Question or term") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
        OutlinedTextField(back, { back = it }, Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = { Text("Answer or definition") }, minLines = 2, shape = RoundedCornerShape(14.dp), colors = fieldColors())
        Button(onClick = { onAdd(front.trim(), back.trim()); front = ""; back = "" }, enabled = front.isNotBlank() && back.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(7.dp)); Text("Add to deck")
        }
        Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR REVISION DECK", color = Muted, fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f)); Text("${items.size} cards", color = Accent, fontSize = 11.sp)
        }
        if (items.isEmpty()) {
            EmptyState("Your deck is empty", "Add terms, questions and answers above. Your cards stay on this phone.", Icons.Rounded.Style)
        } else {
            val card = items[index.coerceIn(0, items.lastIndex)]
            Column(Modifier.fillMaxWidth().weight(1f).background(Brush.linearGradient(listOf(Color(0xFF29243D), Panel2)), RoundedCornerShape(22.dp)).border(1.dp, Color.White.copy(alpha = .09f), RoundedCornerShape(22.dp)).clickable { revealed = !revealed }.padding(22.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (revealed) "ANSWER" else "QUESTION", color = Accent, fontSize = 10.sp, letterSpacing = 1.6.sp)
                Text(if (revealed) card.optString("body") else card.optString("title"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp, modifier = Modifier.padding(top = 18.dp))
                Text("Tap card to flip", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 22.dp))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { index = if (index <= 0) items.lastIndex else index - 1; revealed = false }) { Text("Previous", color = Muted) }
                Text("${index + 1} / ${items.size}", color = Muted, fontSize = 11.sp)
                TextButton(onClick = { index = (index + 1) % items.size; revealed = false }) { Text("Next", color = Accent) }
            }
            TextButton(onClick = { onDelete(card.optString("id")); index = 0; revealed = false }, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Rounded.DeleteOutline, null, tint = Muted); Text("Remove card", color = Muted) }
        }
    }
}

@Composable
private fun PlannerScreen(tasks: JSONArray, onAdd: (String) -> Unit, onToggle: (String, Boolean) -> Unit, onDelete: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    val items = (0 until tasks.length()).mapNotNull { tasks.optJSONObject(it) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Revision planner", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Text("Keep your next study steps clear and manageable.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp, bottom = 18.dp))
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), placeholder = { Text("e.g. Revise algebra") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
        Button(onClick = { onAdd(title.trim()); title = "" }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(7.dp)); Text("Add study task") }
        Text("YOUR TASKS", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
        if (items.isEmpty()) EmptyState("Nothing planned yet", "Add one small revision task to get started.", Icons.Rounded.EventNote)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(items, key = { it.optString("id") }) { task ->
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
private fun MoreScreen(taskCount: Int, onNotes: () -> Unit, onPlanner: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("More", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Text("Your study tools and app preferences.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp, bottom = 20.dp))
        QuickRow("Revision planner", "$taskCount study tasks", Icons.Rounded.EventNote, onPlanner)
        Spacer(Modifier.height(9.dp))
        QuickRow("Personal notes", "Write and save revision notes", Icons.Rounded.EditNote, onNotes)
        Spacer(Modifier.height(9.dp))
        QuickRow("AI & privacy settings", "Manage your provider key and data", Icons.Rounded.Tune, onSettings)
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
        val body = JSONObject().put("model", "llama-3.3-70b-versatile").put("temperature", 0.5)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", "You are a kind, clear school study tutor for grades 1–12. Explain in simple steps, use examples, and never pretend information is in the student's notes if it is not. Use this provided study material when relevant:\n\n${studyContext.ifBlank { "No documents have been added yet." }}"))
                .put(JSONObject().put("role", "user").put("content", prompt)))
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) throw Exception(JSONObject(response).optJSONObject("error")?.optString("message") ?: "AI request failed (${connection.responseCode}).")
        JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    } finally { connection.disconnect() }
}
