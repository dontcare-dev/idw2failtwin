package com.idw2fail.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private data class WeakTopic(val name: String, val mistakes: Int, val total: Int)

@Composable
fun StudyIntelligenceScreen(
    quizzes: JSONArray,
    flashcards: JSONArray,
    tasks: JSONArray,
    onCreatePlan: (List<String>) -> Unit,
    onTutor: (String) -> Unit,
    onOpenExams: () -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenPractice: () -> Unit
) {
    val questions = (0 until quizzes.length()).mapNotNull { quizzes.optJSONObject(it) }
    val attempted = questions.filter { it.optBoolean("attempted") || it.optInt("attemptCount") > 0 }
    val wrong = questions.filter { it.optBoolean("wrong") || (it.has("lastCorrect") && !it.optBoolean("lastCorrect")) }
    val correct = attempted.count { it.optBoolean("lastCorrect") }
    val accuracy = if (attempted.isEmpty()) 0 else (correct * 100 / attempted.size)
    val topicStats = questions.groupBy { q ->
        q.optString("topic").ifBlank { q.optString("chapter").ifBlank { q.optString("subject").ifBlank { "General practice" } } }
    }.map { (name, items) ->
        WeakTopic(name, items.count { it.optBoolean("wrong") || (it.has("lastCorrect") && !it.optBoolean("lastCorrect")) }, items.size)
    }.filter { it.mistakes > 0 }.sortedWith(compareByDescending<WeakTopic> { it.mistakes }.thenBy { it.name }).take(8)
    val dueCards = (0 until flashcards.length()).mapNotNull { flashcards.optJSONObject(it) }.filter { !it.optBoolean("known") }
    val pendingTasks = (0 until tasks.length()).mapNotNull { tasks.optJSONObject(it) }.count { !it.optBoolean("done") }
    val weakNames = topicStats.map { it.name }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text("Study intelligence", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Text("One place to find gaps, review mistakes and decide what to study next.", color = Color(0xFF68717D), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 5.dp, bottom = 15.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Accuracy", "$accuracy%", "${attempted.size} attempts", Modifier.weight(1f))
            MetricTile("Mistakes", wrong.size.toString(), "Need another look", Modifier.weight(1f))
            MetricTile("To review", dueCards.size.toString(), "Flashcards", Modifier.weight(1f))
        }
        SectionHeading("Your weakest topics", "Based on recorded incorrect answers")
        if (topicStats.isEmpty()) {
            EmptyPanel("No weak topics detected yet", "Answer some MCQs. Topics with repeated mistakes will appear here.")
        } else {
            topicStats.forEachIndexed { index, topic ->
                Column(Modifier.fillMaxWidth().padding(bottom = 7.dp).background(Color(0xFFF5F6F8), RoundedCornerShape(13.dp)).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(topic.name, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${topic.mistakes} / ${topic.total} wrong", color = Color(0xFFD85C4A), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    LinearProgressIndicator(progress = { topic.mistakes.toFloat() / topic.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = Color(0xFFD85C4A), trackColor = Color(0xFFE4E7EB))
                }
            }
            Button(onClick = { onCreatePlan(weakNames) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17191D), contentColor = Color.White), shape = RoundedCornerShape(13.dp)) {
                Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(7.dp)); Text("Create revision tasks for weak topics")
            }
        }
        SectionHeading("Mistake notebook", "Questions you have answered incorrectly")
        if (wrong.isEmpty()) EmptyPanel("Your mistake notebook is empty", "Incorrect answers will be saved here automatically.")
        else wrong.take(30).forEach { item ->
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).background(Color(0xFFF5F6F8), RoundedCornerShape(13.dp)).padding(12.dp)) {
                Text(item.optString("title").ifBlank { item.optString("question", "Practice question") }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp)
                val answer = item.optString("answer").ifBlank { item.optString("correctAnswer") }
                if (answer.isNotBlank()) Text("Correct answer: $answer", color = Color(0xFF147D67), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                val explanation = item.optString("explanation").ifBlank { item.optString("body") }
                if (explanation.isNotBlank()) Text(explanation, color = Color(0xFF68717D), fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
                TextButton(onClick = { onTutor(item.optString("title").ifBlank { item.optString("question") }) }, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Rounded.School, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text("Ask tutor to explain", fontSize = 11.sp)
                }
            }
        }
        SectionHeading("Smart revision", "Your unfinished review queue")
        Column(Modifier.fillMaxWidth().background(Color(0xFFF5F6F8), RoundedCornerShape(15.dp)).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Style, null, tint = Color(0xFF6950A1))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("${dueCards.size} flashcards waiting", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Unmastered cards stay in your practice queue.", color = Color(0xFF68717D), fontSize = 10.sp)
                }
                TextButton(onClick = onOpenPractice) { Text("Review") }
            }
            Divider(Modifier.padding(vertical = 8.dp), color = Color(0xFFE1E4E8))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EventNote, null, tint = Color(0xFF147D67))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("$pendingTasks tasks remaining", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Continue your planned study sessions.", color = Color(0xFF68717D), fontSize = 10.sp)
                }
                TextButton(onClick = onOpenPlanner) { Text("Open") }
            }
        }
        SectionHeading("Exam simulator", "Practise under exam conditions")
        Column(Modifier.fillMaxWidth().background(Color(0xFFF5F6F8), RoundedCornerShape(15.dp)).padding(14.dp)) {
            Text("Build a full mock paper from your imported study material, then review your performance.", color = Color(0xFF68717D), fontSize = 11.sp, lineHeight = 17.sp)
            Button(onClick = onOpenExams, modifier = Modifier.fillMaxWidth().padding(top = 9.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17191D), contentColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Rounded.Timer, null); Spacer(Modifier.width(7.dp)); Text("Open exam simulator")
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun MetricTile(title: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xFFF5F6F8), RoundedCornerShape(13.dp)).padding(10.dp)) {
        Text(title, color = Color(0xFF68717D), fontSize = 9.sp)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
        Text(detail, color = Color(0xFF68717D), fontSize = 8.sp, lineHeight = 11.sp)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(Modifier.padding(top = 20.dp, bottom = 9.dp)) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Color(0xFF68717D), fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun EmptyPanel(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFF5F6F8), RoundedCornerShape(13.dp)).padding(14.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Text(detail, color = Color(0xFF68717D), fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
