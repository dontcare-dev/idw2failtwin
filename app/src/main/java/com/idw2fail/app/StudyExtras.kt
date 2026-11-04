package com.idw2fail.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private val ExtraInk = Color(0xFF17191D)
private val ExtraMuted = Color(0xFF68717D)
private val ExtraPanel = Color(0xFFF5F6F8)
private val ExtraMint = Color(0xFF147D67)

@Composable
fun StudyExtrasScreen(documents: JSONArray, onNavigate: (String) -> Unit) {
    var selected by remember { mutableStateOf("Focus") }
    val tabs = listOf("Focus", "Reader", "Learning lab", "Vocabulary")
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text("Study workspace", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Text("A calmer place to focus, read and practise.", color = ExtraMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEach { tab -> FilterChip(selected == tab, { selected = tab }, label = { Text(tab, fontSize = 10.sp) }) }
        }
        when (selected) {
            "Focus" -> FocusTimer()
            "Reader" -> TextbookReader(documents)
            "Learning lab" -> LearningLab()
            else -> VocabularyBuilder(documents, onNavigate)
        }
    }
}

@Composable
private fun FocusTimer() {
    var minutes by remember { mutableIntStateOf(25) }
    var secondsLeft by remember { mutableIntStateOf(25 * 60) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running && secondsLeft > 0) { delay(1000); secondsLeft-- }
        if (secondsLeft == 0) running = false
    }
    val display = "%02d:%02d".format(secondsLeft / 60, secondsLeft % 60)
    Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Focus session", color = ExtraMuted, fontSize = 12.sp)
        Box(Modifier.padding(top = 20.dp).size(220.dp).clip(RoundedCornerShape(110.dp)).background(ExtraPanel).border(8.dp, ExtraMint.copy(alpha = .22f), RoundedCornerShape(110.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(display, fontSize = 42.sp, fontWeight = FontWeight.Bold, color = ExtraInk)
                Text(if (running) "Stay with one task" else if (secondsLeft == 0) "Session complete" else "Ready when you are", color = ExtraMuted, fontSize = 11.sp)
            }
        }
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(15, 25, 45, 60).forEach { value -> FilterChip(selected = minutes == value, onClick = { minutes = value; secondsLeft = value * 60; running = false }, label = { Text("${value}m") }, enabled = !running) }
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Button(onClick = { if (secondsLeft == 0) secondsLeft = minutes * 60; running = !running }, colors = ButtonDefaults.buttonColors(containerColor = ExtraInk)) { Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (running) "Pause" else "Start") }
            OutlinedButton(onClick = { running = false; secondsLeft = minutes * 60 }) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(5.dp)); Text("Reset") }
        }
        Text("Choose one small goal and take a short break when you're done.", color = ExtraMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp))
    }
}

@Composable
private fun TextbookReader(documents: JSONArray) {
    val docs = (0 until documents.length()).mapNotNull { documents.optJSONObject(it) }
    var selectedId by remember { mutableStateOf(docs.firstOrNull()?.optString("id").orEmpty()) }
    var query by remember { mutableStateOf("") }
    val doc = docs.firstOrNull { it.optString("id") == selectedId } ?: docs.firstOrNull()
    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        if (doc == null) {
            Text("No textbook yet", fontWeight = FontWeight.SemiBold)
            Text("Import a PDF from Library to read its extracted text here.", color = ExtraMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
        } else {
            var menu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(doc.optString("name"), maxLines = 1, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ExpandMore, null)
                }
                DropdownMenu(menu, { menu = false }) {
                    docs.forEach { item -> DropdownMenuItem(text = { Text(item.optString("name")) }, onClick = { selectedId = item.optString("id"); menu = false }) }
                }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, placeholder = { Text("Find a word in this textbook") }, leadingIcon = { Icon(Icons.Rounded.Search, null) })
            val pages = doc.optJSONArray("pages")
            val pageItems = if (pages != null && pages.length() > 0) (0 until pages.length()).mapNotNull { pages.optJSONObject(it) }.map { "Page ${it.optInt("number")}" to it.optString("text") } else doc.optString("text").split(Regex("(?=\\[Page \\d+\\])")).filter { it.isNotBlank() }.mapIndexed { index, text -> "Page ${index + 1}" to text }
            val filtered = pageItems.filter { query.isBlank() || it.second.contains(query, true) }
            Text("${filtered.size} sections", color = ExtraMuted, fontSize = 10.sp, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(filtered) { item ->
                    Column(Modifier.fillMaxWidth().background(ExtraPanel, RoundedCornerShape(13.dp)).padding(13.dp)) {
                        Text(item.first, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(item.second.ifBlank { "No selectable text on this page." }, fontSize = 12.sp, lineHeight = 19.sp, color = ExtraInk, modifier = Modifier.padding(top = 7.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningLab() {
    var topic by remember { mutableStateOf("Speed") }
    var distance by remember { mutableStateOf("120") }
    var time by remember { mutableStateOf("2") }
    var mass by remember { mutableStateOf("5") }
    var acceleration by remember { mutableStateOf("3") }
    val speed = (distance.toDoubleOrNull() ?: 0.0) / (time.toDoubleOrNull()?.takeIf { it != 0.0 } ?: 1.0)
    val force = (mass.toDoubleOrNull() ?: 0.0) * (acceleration.toDoubleOrNull() ?: 0.0)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
        Text("Physics mini-lab", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("Change the values and see how the result changes.", color = ExtraMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 12.dp)) {
            listOf("Speed", "Force").forEach { FilterChip(topic == it, { topic = it }, label = { Text(it) }) }
        }
        if (topic == "Speed") {
            OutlinedTextField(distance, { distance = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Distance (metres)") }, singleLine = true)
            OutlinedTextField(time, { time = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Time (seconds)") }, singleLine = true)
            LabResult("Speed = distance ÷ time", "${"%.2f".format(speed)} m/s")
        } else {
            OutlinedTextField(mass, { mass = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Mass (kg)") }, singleLine = true)
            OutlinedTextField(acceleration, { acceleration = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Acceleration (m/s²)") }, singleLine = true)
            LabResult("Force = mass × acceleration", "${"%.2f".format(force)} N")
        }
        Text("Try doubling one value. Notice how the result changes.", color = ExtraMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun LabResult(formula: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp).background(ExtraPanel, RoundedCornerShape(14.dp)).padding(15.dp)) {
        Text(formula, color = ExtraMuted, fontSize = 11.sp)
        Text(value, color = ExtraMint, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun VocabularyBuilder(documents: JSONArray, onNavigate: (String) -> Unit) {
    val docs = (0 until documents.length()).mapNotNull { documents.optJSONObject(it) }
    var search by remember { mutableStateOf("") }
    val words = remember(docs, search) {
        docs.flatMap { doc ->
            Regex("[A-Za-z]{7,}").findAll(doc.optString("text")).map { it.value.lowercase() }.toList()
        }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.filter { search.isBlank() || it.key.contains(search, true) }.take(60)
    }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text("Vocabulary from your PDFs", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("Find longer or repeated terms in your imported material, then ask Mochi what they mean.", color = ExtraMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 9.dp))
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Filter words") })
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(top = 9.dp, bottom = 20.dp)) {
            items(words) { item ->
                Row(Modifier.fillMaxWidth().background(ExtraPanel, RoundedCornerShape(11.dp)).clickable { onNavigate("Study") }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.key, Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("Found ${item.value}×", color = ExtraMuted, fontSize = 10.sp)
                    Icon(Icons.Rounded.ChevronRight, null, tint = ExtraMuted)
                }
            }
        }
    }
}
