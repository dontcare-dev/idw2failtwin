package com.idw2fail.app

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray

private val WorldInk = Color(0xFF17191D)
private val WorldMuted = Color(0xFF68717D)
private val WorldPanel = Color(0xFFF5F6F8)
private val WorldMint = Color(0xFF147D67)
private val WorldViolet = Color(0xFF6950A1)

private data class WorldActivity(
    val id: String,
    val title: String,
    val kind: String,
    val duration: String,
    val intro: String,
    val steps: List<String>,
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String
)

private data class WorldRegion(
    val id: String,
    val name: String,
    val subtitle: String,
    val symbol: String,
    val activities: List<WorldActivity>
)

private fun worldRegions(): List<WorldRegion> = listOf(
    WorldRegion("math", "Math Island", "Patterns, numbers and visual problem-solving", "∑", listOf(
        WorldActivity("math_fractions", "Fraction Kitchen", "Interactive lesson", "6 min", "Fractions describe equal parts of a whole. Imagine sharing one pizza fairly.", listOf("The denominator tells how many equal parts make the whole.", "The numerator tells how many of those parts you have.", "Equivalent fractions name the same amount, such as 1/2 and 2/4."), "Which fraction is equal to 1/2?", listOf("2/3", "2/4", "3/4", "1/3"), 1, "2/4 simplifies to 1/2 because both numbers divide by 2."),
        WorldActivity("math_geometry", "Geometry Builder", "Visual challenge", "5 min", "A rectangle has four right angles. Its opposite sides are equal and parallel.", listOf("Length measures the longer side.", "Width measures the other side.", "Area counts the square units covering the inside: length × width."), "A rectangle is 6 cm long and 3 cm wide. What is its area?", listOf("9 cm²", "18 cm²", "18 cm", "12 cm²"), 1, "Area = 6 × 3 = 18 square centimetres."),
        WorldActivity("math_equations", "Equation Workshop", "Problem-solving", "7 min", "An equation is a balance: whatever you do to one side, do to the other.", listOf("Start with x + 5 = 12.", "Subtract 5 from both sides.", "You get x = 7. Check by substituting 7."), "Solve: x + 8 = 15", listOf("5", "6", "7", "8"), 2, "Subtract 8 from both sides: x = 15 − 8 = 7.")
    )),
    WorldRegion("science", "Science Lab", "Explore how the natural world works", "⚗", listOf(
        WorldActivity("science_force", "Force & Motion", "Physics lesson", "6 min", "A force is a push or pull. Forces can change an object's motion or shape.", listOf("Balanced forces do not change an object's motion.", "An unbalanced force changes speed or direction.", "Friction acts against sliding motion between surfaces."), "What is the SI unit of force?", listOf("Joule", "Newton", "Watt", "Metre"), 1, "Force is measured in newtons (N), named after Isaac Newton."),
        WorldActivity("science_cells", "Cell Explorer", "Biology lesson", "6 min", "Cells are the basic structural and functional units of living organisms.", listOf("The cell membrane controls what enters and leaves.", "The nucleus stores genetic information in many cells.", "Mitochondria release usable energy from food."), "Which structure controls what enters and leaves a cell?", listOf("Nucleus", "Cell membrane", "Ribosome", "Vacuole"), 1, "The cell membrane is a selectively permeable boundary."),
        WorldActivity("science_space", "Space Observatory", "Astronomy lesson", "5 min", "Our solar system contains the Sun and objects held by its gravity.", listOf("The Sun is a star at the centre of our solar system.", "Planets travel around the Sun in orbits.", "Earth takes about one year to complete one orbit."), "Which planet is known for its prominent ring system?", listOf("Mars", "Venus", "Saturn", "Mercury"), 2, "Saturn's extensive rings are made mostly of ice and rocky particles.")
    )),
    WorldRegion("language", "Language Library", "Words, reading and communication", "Aa", listOf(
        WorldActivity("language_grammar", "Grammar Garden", "Language lesson", "5 min", "A verb expresses an action, occurrence or state of being.", listOf("Action verbs include run, write and think.", "A sentence needs a subject and a predicate.", "Verb tense tells when an action happens."), "Which word is the verb in 'Birds fly south'?", listOf("Birds", "Fly", "South", "The"), 1, "Fly is the action performed by the birds."),
        WorldActivity("language_vocab", "Word Detective", "Vocabulary", "5 min", "Context clues help you infer the meaning of an unfamiliar word.", listOf("Read the whole sentence first.", "Look for examples, contrasts or explanations nearby.", "Replace the unfamiliar word with your best guess and reread."), "In 'The glass was fragile, so handle it gently', fragile most nearly means…", listOf("Very heavy", "Easily broken", "Colourful", "Expensive"), 1, "The instruction to handle it gently signals that it can break easily.")
    )),
    WorldRegion("history", "History Museum", "People, places and events through time", "⌛", listOf(
        WorldActivity("history_sources", "Source Detective", "Historical thinking", "6 min", "Historians use evidence to investigate the past rather than relying on guesses.", listOf("Primary sources come from the time being studied.", "Secondary sources interpret or analyse primary evidence.", "Compare sources because each may have a different perspective."), "Which is usually a primary source for studying an event?", listOf("A modern textbook", "A diary written at the time", "A recent documentary", "A modern encyclopedia"), 1, "A diary written at the time is a firsthand source.")
    )),
    WorldRegion("geography", "Geography World", "Maps, environments and our planet", "◎", listOf(
        WorldActivity("geography_maps", "Map Navigator", "Map skills", "5 min", "Maps represent places from above and use symbols to communicate information.", listOf("A map key explains symbols.", "A scale compares map distance with real distance.", "A compass rose shows directions."), "Which direction is opposite to east?", listOf("North", "South", "West", "North-east"), 2, "West is opposite to east on a compass.")
    )),
    WorldRegion("coding", "Coding Zone", "Logic, algorithms and programming ideas", "{ }", listOf(
        WorldActivity("coding_logic", "Logic Workshop", "Computational thinking", "6 min", "An algorithm is a clear sequence of steps for solving a problem.", listOf("Break a large problem into smaller parts.", "Describe each step in an unambiguous order.", "Test the steps with different inputs."), "What is an algorithm?", listOf("A computer screen", "A sequence of problem-solving steps", "A programming language only", "A type of hardware"), 1, "An algorithm is a finite, ordered set of steps to solve a task.")
    ))
)

@Composable
fun StudyWorldScreen(progress: JSONArray, onComplete: (String, String) -> Unit, onOpenMochi: () -> Unit) {
    val regions = remember { worldRegions() }
    var selectedRegion by remember { mutableStateOf<String?>(null) }
    var selectedActivity by remember { mutableStateOf<WorldActivity?>(null) }
    var chosen by remember { mutableIntStateOf(-1) }
    var checked by remember { mutableStateOf(false) }
    var completedPulse by remember { mutableIntStateOf(0) }
    val completed = remember(progress, completedPulse) {
        (0 until progress.length()).mapNotNull { progress.optJSONObject(it)?.optString("activityId") }.toSet()
    }
    val total = regions.sumOf { it.activities.size }
    val done = regions.sumOf { region -> region.activities.count { it.id in completed } }
    val region = regions.firstOrNull { it.id == selectedRegion }
    val activity = selectedActivity

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp)) {
        if (activity != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedActivity = null; chosen = -1; checked = false }) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(activity.kind.uppercase(), color = WorldViolet, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    Text(activity.title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
                Text(activity.duration, color = WorldMuted, fontSize = 11.sp)
            }
            Text(activity.intro, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 14.dp))
            activity.steps.forEachIndexed { index, step ->
                Row(Modifier.fillMaxWidth().padding(top = 9.dp).background(WorldPanel, RoundedCornerShape(13.dp)).padding(13.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(25.dp).clip(CircleShape).background(WorldInk), contentAlignment = Alignment.Center) { Text("${index + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    Text(step, Modifier.weight(1f).padding(start = 10.dp), fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            Text("Quick check", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
            Text(activity.question, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
            activity.options.forEachIndexed { index, option ->
                val isChosen = chosen == index
                val isAnswer = index == activity.answer
                val borderColor = if (checked && isAnswer) WorldMint else if (isChosen) WorldViolet else WorldInk.copy(alpha = .12f)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(if (isChosen) WorldViolet.copy(alpha = .08f) else WorldPanel).border(1.dp, borderColor, RoundedCornerShape(12.dp)).clickable(enabled = !checked) { chosen = index }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isChosen, onClick = null, enabled = !checked)
                    Text(option, Modifier.weight(1f), fontSize = 13.sp)
                    if (checked && isAnswer) Icon(Icons.Rounded.CheckCircle, null, tint = WorldMint)
                }
            }
            if (checked) {
                Text(if (chosen == activity.answer) "That's right." else "Not quite — here's why.", color = if (chosen == activity.answer) WorldMint else WorldViolet, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text(activity.explanation, color = WorldMuted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                if (chosen == activity.answer) {
                    Button(onClick = {
                        if (activity.id !in completed) onComplete(activity.id, region?.id ?: regions.firstOrNull { r -> r.activities.any { it.id == activity.id } }?.id.orEmpty())
                        completedPulse++
                        selectedActivity = null; chosen = -1; checked = false
                    }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), colors = ButtonDefaults.buttonColors(containerColor = WorldInk)) { Text("Complete activity", color = Color.White) }
                } else {
                    OutlinedButton(onClick = { chosen = -1; checked = false }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("Try again") }
                }
            } else {
                Button(onClick = { checked = true }, enabled = chosen >= 0, modifier = Modifier.fillMaxWidth().padding(top = 15.dp), colors = ButtonDefaults.buttonColors(containerColor = WorldInk)) { Text("Check answer", color = Color.White) }
            }
            return@Column
        }

        if (region != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedRegion = null }) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Column {
                    Text("WORLD REGION", color = WorldViolet, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text(region.name, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(region.subtitle, color = WorldMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            region.activities.forEach { item ->
                val isDone = item.id in completed
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(15.dp)).background(WorldPanel).clickable { selectedActivity = item }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(39.dp).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) { Icon(if (isDone) Icons.Rounded.CheckCircle else Icons.Rounded.PlayCircleOutline, null, tint = if (isDone) WorldMint else WorldInk, modifier = Modifier.size(23.dp)) }
                    Column(Modifier.weight(1f).padding(start = 11.dp)) {
                        Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${item.kind} · ${item.duration}", color = WorldMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = WorldMuted)
                }
            }
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SDS STUDY / WORLD", color = WorldViolet, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text("Study World", fontSize = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp)
                Text("A place to explore what you're learning.", color = WorldMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
            }
            IconButton(onClick = onOpenMochi) { Icon(Icons.Rounded.AutoAwesome, "Ask Mochi", tint = WorldViolet) }
        }
        Column(Modifier.fillMaxWidth().padding(top = 17.dp).clip(RoundedCornerShape(18.dp)).background(WorldInk).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR EXPLORATION", color = Color.White.copy(alpha = .65f), fontSize = 9.sp, letterSpacing = 1.2.sp)
                    Text("$done of $total activities", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
                Text("${if (total == 0) 0 else done * 100 / total}%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp).clip(CircleShape), color = Color(0xFF9AD9C6), trackColor = Color.White.copy(alpha = .18f))
            Text("Progress is saved on this device. No points or streak pressure.", color = Color.White.copy(alpha = .68f), fontSize = 10.sp, modifier = Modifier.padding(top = 9.dp))
        }
        Text("Explore regions", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
        regions.forEachIndexed { index, item ->
            val count = item.activities.count { it.id in completed }
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(WorldPanel).clickable { selectedRegion = item.id }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White), contentAlignment = Alignment.Center) { Text(item.symbol, color = WorldViolet, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(item.subtitle, color = WorldMuted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 3.dp))
                    Text("$count / ${item.activities.size} activities complete", color = WorldMint, fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 5.dp))
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = WorldMuted)
            }
        }
        OutlinedButton(onClick = onOpenMochi, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp)) { Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(7.dp)); Text("Ask Mochi about a topic") }
    }
}
