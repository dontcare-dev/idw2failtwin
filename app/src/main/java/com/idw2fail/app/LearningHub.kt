package com.idw2fail.app

import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private val HubInk = Color(0xFF17191D)
private val HubMuted = Color(0xFF68717D)
private val HubPanel = Color(0xFFF5F6F8)
private val HubAccent = Color(0xFF6950A1)
private val HubMint = Color(0xFF147D67)

@Composable
fun GlobalSearchScreen(
    documents: JSONArray,
    quizzes: JSONArray,
    flashcards: JSONArray,
    syllabus: JSONArray,
    formulas: JSONArray,
    onNavigate: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val results = remember(q, documents, quizzes, flashcards, syllabus) {
        val found = mutableListOf<Triple<String, String, String>>()
        for (i in 0 until documents.length()) {
            val item = documents.optJSONObject(i) ?: continue
            if (q.isNotBlank() && (item.optString("name").contains(q, true) || item.optString("text").contains(q, true) || item.optString("subject").contains(q, true)))
                found.add(Triple("PDF · ${item.optString("subject", "General")}", item.optString("name"), "Library"))
        }
        for (i in 0 until quizzes.length()) {
            val item = quizzes.optJSONObject(i) ?: continue
            if (q.isNotBlank() && (item.optString("title").contains(q, true) || item.optString("body").contains(q, true)))
                found.add(Triple("MCQ", item.optString("title"), "Quizzes"))
        }
        for (i in 0 until flashcards.length()) {
            val item = flashcards.optJSONObject(i) ?: continue
            if (q.isNotBlank() && (item.optString("title").contains(q, true) || item.optString("body").contains(q, true)))
                found.add(Triple("Flashcard", item.optString("title"), "Practice"))
        }
        for (i in 0 until syllabus.length()) {
            val item = syllabus.optJSONObject(i) ?: continue
            if (q.isNotBlank() && item.optString("title").contains(q, true))
                found.add(Triple("Syllabus topic", item.optString("title"), "Syllabus"))
        }
        for (i in 0 until formulas.length()) {
            val item = formulas.optJSONObject(i) ?: continue
            if (q.isNotBlank() && (item.optString("title").contains(q, true) || item.optString("body").contains(q, true)))
                found.add(Triple("Formula sheet", item.optString("title"), "Tools"))
        }
        found.take(80)
    }
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text("Search everything", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Text("Search imported PDFs, extracted text, questions, flashcards and syllabus topics.", color = HubMuted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 5.dp, bottom = 14.dp))
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search your study library…") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(14.dp))
        if (q.isBlank()) {
            Text("Your search stays on this device.", color = HubMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
        } else if (results.isEmpty()) {
            Text("No results found.", color = HubMuted, modifier = Modifier.padding(top = 20.dp))
        } else {
            Text("${results.size} results", color = HubMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { item ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(HubPanel).clickable { onNavigate(item.third) }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.first, color = HubAccent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Text(item.second, color = HubInk, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = HubMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun LearningHubScreen(
    documents: JSONArray,
    result: String,
    board: JSONObject?,
    status: String,
    busy: Boolean,
    offline: Boolean,
    onOfflineChange: (Boolean) -> Unit,
    onGenerate: (String, String, String) -> Unit,
    onSetSubject: (String, String) -> Unit,
    onSaveFormula: (String, String) -> Unit
) {
    val docs = (0 until documents.length()).mapNotNull { documents.optJSONObject(it) }
    var mode by remember { mutableStateOf("Ask PDF") }
    var selectedId by remember { mutableStateOf(docs.firstOrNull()?.optString("id").orEmpty()) }
    var prompt by remember { mutableStateOf("") }
    var subjectName by remember { mutableStateOf("") }
    var calculator by remember { mutableStateOf("") }
    var converterValue by remember { mutableStateOf("1") }
    var converterUnit by remember { mutableStateOf("km → m") }
    val selectedDoc = docs.firstOrNull { it.optString("id") == selectedId } ?: docs.firstOrNull()
    val subjects = docs.map { it.optString("subject", "General").ifBlank { "General" } }.distinct().sorted()
    val modes = listOf("Ask PDF", "Mind map", "Past paper", "Diagram practice", "Formula generator", "Subjects", "Subject tools")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text("Learning tools", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Text("Use your own material to explore, practise and revise.", color = HubMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(HubPanel).padding(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Offline study mode", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(if (offline) "AI requests paused. Saved study content remains available." else "Saved study content is available offline; AI tools need internet.", color = HubMuted, fontSize = 10.sp, lineHeight = 15.sp)
            }
            Switch(checked = offline, onCheckedChange = onOfflineChange)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.chunked(2).forEach { rowModes ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    rowModes.forEach { label ->
                        FilterChip(selected = mode == label, onClick = { mode = label }, label = { Text(label, fontSize = 10.sp) })
                    }
                }
            }
        }
        if (mode != "Subject tools" && mode != "Subjects") {
            Text("Study material", color = HubMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp, bottom = 5.dp))
            if (docs.isEmpty()) Text("Import a selectable-text PDF in Library first.", color = HubMuted, fontSize = 12.sp)
            else {
                var menu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedDoc?.optString("name") ?: "Choose PDF", maxLines = 1)
                        Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        docs.forEach { doc ->
                            DropdownMenuItem(text = { Text(doc.optString("name")) }, onClick = { selectedId = doc.optString("id"); menu = false })
                        }
                    }
                }
            }
        }
        when (mode) {
            "Subjects" -> {
                Text("Subjects", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
                Text("Assign PDFs to subjects to keep study material organised. Existing PDFs start in General.", color = HubMuted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(vertical = 7.dp))
                OutlinedTextField(subjectName, { subjectName = it }, Modifier.fillMaxWidth(), placeholder = { Text("Subject name, e.g. Science") }, singleLine = true)
                if (selectedDoc != null) {
                    Text("Assign selected PDF", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                    Text(selectedDoc.optString("name"), color = HubMuted, fontSize = 11.sp)
                    Button(onClick = { if (subjectName.isNotBlank()) { onSetSubject(selectedDoc.optString("id"), subjectName.trim()); subjectName = "" } }, enabled = subjectName.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) { Text("Save subject") }
                }
                Text("Subjects in your library: ${subjects.joinToString(" · ")}", color = HubMuted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp))
            }
            "Subject tools" -> {
                Text("Calculator", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
                OutlinedTextField(calculator, { calculator = it }, Modifier.fillMaxWidth(), label = { Text("Arithmetic expression") }, placeholder = { Text("(12 + 8) * 3") }, singleLine = true)
                Text("For safety, the calculator accepts only numbers and + − × ÷ parentheses and decimal points.", color = HubMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                val calc = safeCalculate(calculator)
                Text(calc?.toString() ?: "Enter a valid expression", color = if (calc == null) HubMuted else HubMint, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Text("Unit converter", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 22.dp))
                OutlinedTextField(converterValue, { converterValue = it }, Modifier.fillMaxWidth(), label = { Text("Value") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 7.dp)) {
                    listOf("km → m", "m → cm", "kg → g", "h → min", "°C → °F").forEach { label -> FilterChip(selected = converterUnit == label, onClick = { converterUnit = label }, label = { Text(label, fontSize = 10.sp) }) }
                }
                val converted = converterValue.toDoubleOrNull()?.let { convertUnit(it, converterUnit) }
                Text(converted?.let { "$it $converterUnit" } ?: "Enter a valid number", color = HubMint, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Text("Quick science reference", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 22.dp))
                val elements = listOf("H · Hydrogen · 1", "He · Helium · 2", "C · Carbon · 6", "N · Nitrogen · 7", "O · Oxygen · 8", "Na · Sodium · 11", "Mg · Magnesium · 12", "Al · Aluminium · 13", "Si · Silicon · 14", "Cl · Chlorine · 17", "Ca · Calcium · 20", "Fe · Iron · 26")
                elements.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 6.dp)) {
                        pair.forEach { el -> Text(el, Modifier.weight(1f).background(HubPanel, RoundedCornerShape(10.dp)).padding(10.dp), fontSize = 11.sp) }
                    }
                }
            }
            else -> {
                val description = when (mode) {
                    "Ask PDF" -> "Ask a question about the selected PDF. Answers include page references only when the imported file has a page index."
                    "Mind map" -> "Create a visual concept map from the selected PDF."
                    "Past paper" -> "Analyse an imported past-paper PDF for question patterns, topic coverage and recurring concepts."
                    "Diagram practice" -> "Generate a labelled study diagram and a short self-check prompt."
                    else -> "Extract and organise key formulas from the selected PDF."
                }
                Text(description, color = HubMuted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), placeholder = { Text(when (mode) {
                    "Ask PDF" -> "What is the main idea of this chapter?"
                    "Mind map" -> "Chapter or topic (optional)"
                    "Past paper" -> "What should I focus on? (optional)"
                    "Diagram practice" -> "e.g. Human heart, pendulum, water cycle"
                    else -> "e.g. Motion and force"
                }) }, minLines = 2, maxLines = 4)
                Button(onClick = { if (!offline && selectedDoc != null) onGenerate(mode, prompt.trim(), selectedDoc.optString("id")) }, enabled = !busy && !offline && selectedDoc != null && (mode != "Ask PDF" || prompt.isNotBlank()), modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(48.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.AutoAwesome, null)
                    Spacer(Modifier.width(7.dp)); Text(if (busy) "Working…" else "Generate")
                }
                if (mode == "Formula generator" && result.isNotBlank()) {
                    OutlinedButton(onClick = { onSaveFormula("Formula sheet · ${selectedDoc?.optString("name") ?: "Study"}", result) }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) { Icon(Icons.Rounded.BookmarkAdd, null); Spacer(Modifier.width(6.dp)); Text("Save formula sheet") }
                }
            }
        }
        if (status.isNotBlank()) Text(status, color = HubMint, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 12.dp))
        if (result.isNotBlank()) {
            Text("Result", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            Text(result, Modifier.fillMaxWidth().background(HubPanel, RoundedCornerShape(15.dp)).padding(14.dp), fontSize = 12.sp, lineHeight = 19.sp)
        }
        if (board != null) StudyDiagram(board)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun ExamReadinessScreen(syllabus: JSONArray, quizzes: JSONArray, flashcards: JSONArray, documents: JSONArray) {
    val topics = (0 until syllabus.length()).mapNotNull { syllabus.optJSONObject(it) }
    val completed = topics.count { it.optBoolean("done") }
    val coverage = if (topics.isEmpty()) 0 else completed * 100 / topics.size
    val questions = (0 until quizzes.length()).mapNotNull { quizzes.optJSONObject(it) }
    val attempted = questions.filter { it.optBoolean("attempted") }
    val correct = attempted.count { it.optBoolean("lastCorrect") }
    val accuracy = if (attempted.isEmpty()) 0 else correct * 100 / attempted.size
    val learningCards = (0 until flashcards.length()).mapNotNull { flashcards.optJSONObject(it) }
    val known = learningCards.count { it.optBoolean("known") }
    val cardMastery = if (learningCards.isEmpty()) 0 else known * 100 / learningCards.size
    val scoreParts = mutableListOf<Int>()
    if (topics.isNotEmpty()) scoreParts.add(coverage)
    if (attempted.isNotEmpty()) scoreParts.add(accuracy)
    if (learningCards.isNotEmpty()) scoreParts.add(cardMastery)
    val readiness = if (scoreParts.isEmpty()) 0 else scoreParts.average().toInt()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text("Exam readiness", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Text("A progress snapshot based on the study data you've recorded, not a prediction of exam marks.", color = HubMuted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 5.dp))
        Column(Modifier.fillMaxWidth().padding(top = 16.dp).background(HubPanel, RoundedCornerShape(18.dp)).padding(17.dp)) {
            Text(if (scoreParts.isEmpty()) "Not enough activity yet" else "$readiness% study indicators", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { readiness / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), color = HubMint)
            Text("Based on ${scoreParts.size} available measures", color = HubMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 7.dp))
        }
        ReadinessMetric("Syllabus coverage", "$completed / ${topics.size} topics", coverage)
        ReadinessMetric("MCQ accuracy", "$correct / ${attempted.size} correct attempts", accuracy)
        ReadinessMetric("Flashcards marked known", "$known / ${learningCards.size} cards", cardMastery)
        Text("Study materials", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
        Text("${documents.length()} PDFs imported · ${questions.size} MCQs available · ${learningCards.size} flashcards saved", color = HubMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
        if (topics.isEmpty()) Text("Add syllabus topics to measure coverage.", color = HubMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
        if (attempted.isEmpty()) Text("Try some MCQs to measure your current practice accuracy.", color = HubMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        if (learningCards.isEmpty()) Text("Generate flashcards from your PDFs to track recall.", color = HubMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ReadinessMetric(title: String, detail: String, value: Int) {
    Column(Modifier.fillMaxWidth().padding(top = 15.dp).background(HubPanel, RoundedCornerShape(13.dp)).padding(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text("$value%", color = HubMint, fontWeight = FontWeight.Bold) }
        LinearProgressIndicator(progress = { value / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = HubAccent)
        Text(detail, color = HubMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
fun StudyDiagram(board: JSONObject) {
    val title = board.optString("title", "Study diagram").ifBlank { "Study diagram" }
    val center = board.optString("central", board.optString("center", title)).ifBlank { title }
    val branchArray = board.optJSONArray("branches")
    val branchNodes = if (branchArray != null) (0 until branchArray.length()).mapNotNull { i ->
        val item = branchArray.opt(i)
        when (item) {
            is JSONObject -> {
                val label = item.optString("title", item.optString("label", item.optString("name"))).trim()
                val detail = item.optString("detail", item.optString("description")).trim()
                if (label.isBlank() && detail.isBlank()) null else (label.ifBlank { "Key detail" }) to detail
            }
            else -> item.toString().trim().takeIf { it.isNotBlank() }?.let { it to "" }
        }
    } else emptyList()
    val elements = board.optJSONArray("elements") ?: JSONArray()
    val elementNodes = (0 until elements.length()).mapNotNull { i ->
        elements.optJSONObject(i)?.takeIf { it.optString("type") == "text" }?.optString("value")
            ?.trim()?.takeIf { it.isNotBlank() }?.let { it to "" }
    }
    val nodes = (branchNodes + elementNodes)
        .filterNot { it.first.equals(center, true) || it.first.equals(title, true) }
        .distinctBy { it.first.lowercase() }.take(8)
        .ifEmpty { listOf("Main idea" to "The central concept to remember.", "How it works" to "The process or relationship involved.", "Key detail" to "An important fact from the explanation.") }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)
        .clip(RoundedCornerShape(18.dp)).background(Color.White)
        .border(1.dp, Color(0xFFE0E4EA), RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(title, color = HubInk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text("VISUAL SUMMARY", color = HubMuted, fontSize = 9.sp, letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 3.dp, bottom = 13.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(center, Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF202938)).padding(horizontal = 14.dp, vertical = 15.dp),
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 19.sp)
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val x = size.width / 2f
            drawLine(Color(0xFF9AA5B5), androidx.compose.ui.geometry.Offset(x, 0f),
                androidx.compose.ui.geometry.Offset(x, size.height), 2.dp.toPx())
            drawLine(Color(0xFF9AA5B5), androidx.compose.ui.geometry.Offset(size.width * .25f, size.height),
                androidx.compose.ui.geometry.Offset(size.width * .75f, size.height), 2.dp.toPx())
        }
        nodes.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { col, node ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if ((row * 2 + col) % 2 == 0) Color(0xFFF2F0F8) else Color(0xFFEEF5F3))
                        .border(1.dp, Color(0xFFE1E5EB), RoundedCornerShape(12.dp)).padding(11.dp)) {
                        Text("%02d".format(row * 2 + col + 1), color = HubMuted,
                            fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(node.first, color = HubInk, fontSize = 12.sp, lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                        if (node.second.isNotBlank()) {
                            Text(node.second, color = HubMuted, fontSize = 10.sp, lineHeight = 15.sp,
                                modifier = Modifier.padding(top = 5.dp))
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            if (row < (nodes.size - 1) / 2) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(9.dp)) {
                    val x = size.width / 2f
                    drawLine(Color(0xFFB7BFCA), androidx.compose.ui.geometry.Offset(x, 0f),
                        androidx.compose.ui.geometry.Offset(x, size.height), 1.5.dp.toPx())
                }
            }
        }
    }
}

private fun safeCalculate(expression: String): Double? {
    if (expression.isBlank() || expression.length > 80 || expression.any { it !in "0123456789.+-*/() " }) return null
    return runCatching {
        val parser = ArithmeticParser(expression)
        parser.parse().takeIf { it.isFinite() }
    }.getOrNull()
}

private class ArithmeticParser(private val input: String) {
    private var position = 0
    fun parse(): Double { val value = expression(); skip(); require(position == input.length); return value }
    private fun expression(): Double {
        var value = term()
        while (true) { skip(); value = when (peek()) { '+' -> { position++; value + term() }; '-' -> { position++; value - term() }; else -> return value } }
    }
    private fun term(): Double {
        var value = factor()
        while (true) { skip(); value = when (peek()) { '*' -> { position++; value * factor() }; '/' -> { position++; val divisor=factor(); require(divisor != 0.0); value/divisor }; else -> return value } }
    }
    private fun factor(): Double {
        skip()
        if (peek() == '+') { position++; return factor() }
        if (peek() == '-') { position++; return -factor() }
        if (peek() == '(') { position++; val value=expression(); skip(); require(peek()==')'); position++; return value }
        val start=position
        while (peek()?.let { it.isDigit() || it=='.' } == true) position++
        require(position>start)
        return input.substring(start,position).toDouble()
    }
    private fun skip() { while (peek()==' ') position++ }
    private fun peek(): Char? = input.getOrNull(position)
}

private fun convertUnit(value: Double, unit: String): Double = when (unit) {
    "km → m" -> value * 1000
    "m → cm" -> value * 100
    "kg → g" -> value * 1000
    "h → min" -> value * 60
    "°C → °F" -> value * 9 / 5 + 32
    else -> value
}
