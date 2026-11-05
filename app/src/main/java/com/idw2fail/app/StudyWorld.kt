package com.idw2fail.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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

private fun validateWorldRegistry(regions: List<WorldRegion>) {
    require(regions.map { it.id }.distinct().size == regions.size) { "World region IDs must be unique." }
    val activities = regions.flatMap { it.activities + worldExtraActivities(it.id) }
    require(activities.map { it.id }.distinct().size == activities.size) { "World activity IDs must be unique." }
    activities.forEach { activity ->
        require(activity.title.isNotBlank() && activity.intro.isNotBlank()) { "World activities need a title and introduction." }
        require(activity.steps.isNotEmpty()) { "World activities need at least one learning step." }
        require(activity.options.size >= 2 && activity.answer in activity.options.indices) { "World activity answer is invalid." }
        require(activity.explanation.isNotBlank()) { "World activities need an explanation." }
    }
}

private fun worldRegions(): List<WorldRegion> = listOf(
    WorldRegion("math", "Math Island", "Patterns, numbers and visual problem-solving", "∑", listOf(
        WorldActivity("math_fractions", "Fraction Kitchen", "Interactive lesson", "6 min", "Fractions describe equal parts of a whole. Imagine sharing one pizza fairly.", listOf("The denominator tells how many equal parts make the whole.", "The numerator tells how many of those parts you have.", "Equivalent fractions name the same amount, such as 1/2 and 2/4."), "Which fraction is equal to 1/2?", listOf("2/3", "2/4", "3/4", "1/3"), 1, "2/4 simplifies to 1/2 because both numbers divide by 2."),
        WorldActivity("math_geometry", "Geometry Builder", "Visual challenge", "5 min", "A rectangle has four right angles. Its opposite sides are equal and parallel.", listOf("Length measures the longer side.", "Width measures the other side.", "Area counts the square units covering the inside: length × width."), "A rectangle is 6 cm long and 3 cm wide. What is its area?", listOf("9 cm²", "18 cm²", "18 cm", "12 cm²"), 1, "Area = 6 × 3 = 18 square centimetres."),
        WorldActivity("math_equations", "Equation Workshop", "Problem-solving", "7 min", "An equation is a balance: whatever you do to one side, do to the other.", listOf("Start with x + 5 = 12.", "Subtract 5 from both sides.", "You get x = 7. Check by substituting 7."), "Solve: x + 8 = 15", listOf("5", "6", "7", "8"), 2, "Subtract 8 from both sides: x = 15 − 8 = 7.")
    )),
    WorldRegion("science", "Science Lab", "Explore how the natural world works", "LAB", listOf(
        WorldActivity("science_force", "Force & Motion", "Physics lesson", "6 min", "A force is a push or pull. Forces can change an object's motion or shape.", listOf("Balanced forces do not change an object's motion.", "An unbalanced force changes speed or direction.", "Friction acts against sliding motion between surfaces."), "What is the SI unit of force?", listOf("Joule", "Newton", "Watt", "Metre"), 1, "Force is measured in newtons (N), named after Isaac Newton."),
        WorldActivity("science_cells", "Cell Explorer", "Biology lesson", "6 min", "Cells are the basic structural and functional units of living organisms.", listOf("The cell membrane controls what enters and leaves.", "The nucleus stores genetic information in many cells.", "Mitochondria release usable energy from food."), "Which structure controls what enters and leaves a cell?", listOf("Nucleus", "Cell membrane", "Ribosome", "Vacuole"), 1, "The cell membrane is a selectively permeable boundary."),
        WorldActivity("science_space", "Space Observatory", "Astronomy lesson", "5 min", "Our solar system contains the Sun and objects held by its gravity.", listOf("The Sun is a star at the centre of our solar system.", "Planets travel around the Sun in orbits.", "Earth takes about one year to complete one orbit."), "Which planet is known for its prominent ring system?", listOf("Mars", "Venus", "Saturn", "Mercury"), 2, "Saturn's extensive rings are made mostly of ice and rocky particles.")
    )),
    WorldRegion("language", "Language Library", "Words, reading and communication", "Aa", listOf(
        WorldActivity("language_grammar", "Grammar Garden", "Language lesson", "5 min", "A verb expresses an action, occurrence or state of being.", listOf("Action verbs include run, write and think.", "A sentence needs a subject and a predicate.", "Verb tense tells when an action happens."), "Which word is the verb in 'Birds fly south'?", listOf("Birds", "Fly", "South", "The"), 1, "Fly is the action performed by the birds."),
        WorldActivity("language_vocab", "Word Detective", "Vocabulary", "5 min", "Context clues help you infer the meaning of an unfamiliar word.", listOf("Read the whole sentence first.", "Look for examples, contrasts or explanations nearby.", "Replace the unfamiliar word with your best guess and reread."), "In 'The glass was fragile, so handle it gently', fragile most nearly means…", listOf("Very heavy", "Easily broken", "Colourful", "Expensive"), 1, "The instruction to handle it gently signals that it can break easily.")
    )),
    WorldRegion("history", "History Museum", "People, places and events through time", "ERA", listOf(
        WorldActivity("history_sources", "Source Detective", "Historical thinking", "6 min", "Historians use evidence to investigate the past rather than relying on guesses.", listOf("Primary sources come from the time being studied.", "Secondary sources interpret or analyse primary evidence.", "Compare sources because each may have a different perspective."), "Which is usually a primary source for studying an event?", listOf("A modern textbook", "A diary written at the time", "A recent documentary", "A modern encyclopedia"), 1, "A diary written at the time is a firsthand source.")
    )),
    WorldRegion("geography", "Geography World", "Maps, environments and our planet", "MAP", listOf(
        WorldActivity("geography_maps", "Map Navigator", "Map skills", "5 min", "Maps represent places from above and use symbols to communicate information.", listOf("A map key explains symbols.", "A scale compares map distance with real distance.", "A compass rose shows directions."), "Which direction is opposite to east?", listOf("North", "South", "West", "North-east"), 2, "West is opposite to east on a compass.")
    )),
    WorldRegion("coding", "Coding Zone", "Logic, algorithms and programming ideas", "{ }", listOf(
        WorldActivity("coding_logic", "Logic Workshop", "Computational thinking", "6 min", "An algorithm is a clear sequence of steps for solving a problem.", listOf("Break a large problem into smaller parts.", "Describe each step in an unambiguous order.", "Test the steps with different inputs."), "What is an algorithm?", listOf("A computer screen", "A sequence of problem-solving steps", "A programming language only", "A type of hardware"), 1, "An algorithm is a finite, ordered set of steps to solve a task.")
    ))
)

 
private fun worldExtraActivities(regionId: String): List<WorldActivity> = when (regionId) {
    "math" -> listOf(
        WorldActivity("math_percent", "Percentage Market", "Number challenge", "5 min", "Percent means per hundred. It is a useful way to compare parts of a whole.", listOf("10% means 10 out of every 100.", "To find 20% of 50, calculate 0.20 × 50.", "A percentage can be greater than 100%."), "What is 25% of 80?", listOf("10", "15", "20", "25"), 2, "25% is one quarter. 80 ÷ 4 = 20."),
        WorldActivity("math_patterns", "Pattern Observatory", "Logic puzzle", "4 min", "A sequence follows a rule. Find the rule to predict the next term.", listOf("Compare neighbouring terms.", "Check whether the same amount is added or multiplied.", "Apply the rule to the last term."), "What comes next: 3, 6, 12, 24, ...?", listOf("30", "36", "42", "48"), 3, "Each term doubles, so 24 × 2 = 48.")
    )
    "science" -> listOf(
        WorldActivity("science_energy", "Energy Transfer", "Physics lesson", "6 min", "Energy can be transferred between objects and transformed from one form to another.", listOf("Kinetic energy is associated with motion.", "Gravitational potential energy depends on position in a gravitational field.", "Energy is conserved in a closed system."), "Which energy store increases when a ball is lifted higher?", listOf("Chemical", "Gravitational potential", "Sound", "Nuclear"), 1, "Raising the ball increases its gravitational potential energy."),
        WorldActivity("science_ecosystem", "Ecosystem Quest", "Biology challenge", "5 min", "An ecosystem includes living organisms and the non-living parts of their environment.", listOf("Producers make their own food, usually using sunlight.", "Consumers obtain energy by eating other organisms.", "Decomposers recycle nutrients from dead matter."), "Which organism is a producer?", listOf("Grass", "Rabbit", "Fox", "Mushroom"), 0, "Grass makes its own food through photosynthesis.")
    )
    "language" -> listOf(
        WorldActivity("language_reading", "Reading Compass", "Reading skill", "5 min", "The main idea is the central point a passage communicates.", listOf("Read the complete passage.", "Notice repeated ideas and topic sentences.", "Separate the main idea from supporting details."), "What best describes a supporting detail?", listOf("The central message", "A fact that develops the main idea", "The title only", "An unrelated opinion"), 1, "Supporting details explain, illustrate or provide evidence for the main idea."),
        WorldActivity("language_punctuation", "Punctuation Studio", "Grammar challenge", "4 min", "Punctuation helps readers understand pauses, sentence structure and meaning.", listOf("A full stop ends a statement.", "A question mark ends a direct question.", "An apostrophe can show possession or a contraction."), "Which sentence uses punctuation correctly?", listOf("Where are you.", "Its raining!", "Where are you?", "I like apples,"), 2, "A direct question ends with a question mark.")
    )
    "history" -> listOf(
        WorldActivity("history_timeline", "Timeline Architect", "Chronology", "5 min", "Chronology means arranging events in the order they happened.", listOf("Record the date of each event.", "Place the earliest event first.", "Use a consistent scale when drawing a timeline."), "Which event belongs first on a timeline?", listOf("An event from 1900", "An event from 1750", "An event from 2000", "An event from 1950"), 1, "1750 is the earliest date listed."),
        WorldActivity("history_civilisations", "Civilisation Clues", "Historical inquiry", "6 min", "Archaeologists study material remains to learn about past societies.", listOf("Artifacts are objects made or used by people.", "Buildings can reveal technology and social organisation.", "Evidence needs context before drawing conclusions."), "What is an artifact?", listOf("A natural cloud", "An object made or used by people", "A modern prediction", "A weather pattern"), 1, "An artifact is a human-made or human-used object from the past.")
    )
    "geography" -> listOf(
        WorldActivity("geography_climate", "Climate Tracker", "Earth systems", "5 min", "Weather describes short-term atmospheric conditions; climate describes long-term patterns.", listOf("Weather can change from hour to hour.", "Climate is studied over many years.", "Latitude, altitude and oceans influence climate."), "Which describes climate?", listOf("Today's rainfall", "This afternoon's temperature", "A region's typical conditions over decades", "A single thunderstorm"), 2, "Climate describes long-term patterns, not a single day's weather."),
        WorldActivity("geography_landforms", "Landform Expedition", "Physical geography", "5 min", "Landforms are natural features of Earth's surface.", listOf("Mountains are elevated areas with steep slopes.", "Plains are broad areas of relatively level land.", "Valleys are low areas between hills or mountains."), "Which landform is generally broad and relatively flat?", listOf("Plain", "Mountain", "Cliff", "Volcano"), 0, "A plain is a large area of relatively level land.")
    )
    "coding" -> listOf(
        WorldActivity("coding_variables", "Variable Vault", "Programming concepts", "5 min", "A variable is a named place to store a value that a program can use.", listOf("A variable has a name.", "Its value can be read by the program.", "In many languages, a variable can be updated."), "If score = 12 and then score = score + 3, what is score?", listOf("9", "12", "15", "36"), 2, "The new value is the old value plus 3: 12 + 3 = 15."),
        WorldActivity("coding_debug", "Debugging Workshop", "Problem-solving", "5 min", "Debugging is the process of finding and correcting problems in a program.", listOf("Reproduce the problem reliably.", "Read the error and inspect the relevant code.", "Change one thing at a time and test again."), "What is the best first step when a bug appears?", listOf("Delete the project", "Reproduce and understand the problem", "Ignore the error", "Rewrite everything immediately"), 1, "First reproduce the issue so you can investigate its cause.")
    )
    else -> emptyList()
}

@Composable
private fun WorldMapPreview(regions: List<WorldRegion>, completed: Set<String>, onSelect: (String) -> Unit) {
    val nodes = listOf(
        Triple("math", .18f, .23f), Triple("science", .53f, .13f),
        Triple("history", .82f, .34f), Triple("language", .24f, .72f),
        Triple("geography", .58f, .78f), Triple("coding", .86f, .73f)
    )
    BoxWithConstraints(Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFE9EDF2))) {
        Canvas(Modifier.fillMaxSize()) {
            val pts = nodes.map { Offset(size.width * it.second, size.height * it.third) }
            val links = listOf(0 to 1, 1 to 2, 0 to 3, 3 to 4, 4 to 5, 1 to 4, 2 to 5)
            links.forEach { (a, b) ->
                drawLine(Color(0xFF9AA5B3), pts[a], pts[b], strokeWidth = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            drawCircle(Color.White.copy(alpha = .55f), radius = 75.dp.toPx(), center = Offset(size.width * .5f, size.height * .48f))
        }
        nodes.forEach { (id, x, y) ->
            val item = regions.first { it.id == id }
            val isDone = item.activities.isNotEmpty() && item.activities.all { it.id in completed }
            val short = when (id) { "geography" -> "Geo"; "language" -> "Words"; else -> item.name.substringBefore(" ") }
            Column(Modifier.offset(x = maxWidth * x - 34.dp, y = maxHeight * y - 24.dp).width(78.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { onSelect(id) }.border(1.dp, if (isDone) WorldMint else Color(0xFFD5DAE2), RoundedCornerShape(14.dp)).padding(vertical = 8.dp, horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item.symbol, color = WorldViolet, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(short, color = WorldInk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Text(if (isDone) "Complete" else "${item.activities.count { it.id in completed }}/${item.activities.size}", color = if (isDone) WorldMint else WorldMuted, fontSize = 8.sp)
            }
        }
        Text("TAP A REGION TO EXPLORE", color = WorldMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
    }
}

@Composable
private fun WorldSimulationStudio() {
    var mass by remember { mutableFloatStateOf(2f) }
    var acceleration by remember { mutableFloatStateOf(3f) }
    var voltage by remember { mutableFloatStateOf(6f) }
    var resistance by remember { mutableFloatStateOf(3f) }
    var length by remember { mutableFloatStateOf(8f) }
    var width by remember { mutableFloatStateOf(4f) }
    var slope by remember { mutableFloatStateOf(1f) }
    var intercept by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(WorldPanel).padding(15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tune, null, tint = WorldViolet)
            Text("Simulation Studio", Modifier.padding(start = 8.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Text("Change the values and see how the result changes.", color = WorldMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        Text("Physics · Newton's second law", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text("Force = mass × acceleration", color = WorldMuted, fontSize = 10.sp)
        Text("Mass: ${"%.1f".format(mass)} kg", fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        Slider(value = mass, onValueChange = { mass = it }, valueRange = 0.5f..10f)
        Text("Acceleration: ${"%.1f".format(acceleration)} m/s²", fontSize = 11.sp)
        Slider(value = acceleration, onValueChange = { acceleration = it }, valueRange = 0.5f..10f)
        Text("Result: ${"%.1f".format(mass * acceleration)} N", color = WorldMint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = WorldInk.copy(alpha = .10f))
        Text("Electricity · Ohm's law", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text("Current = voltage ÷ resistance", color = WorldMuted, fontSize = 10.sp)
        Text("Voltage: ${"%.1f".format(voltage)} V", fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        Slider(value = voltage, onValueChange = { voltage = it }, valueRange = 1f..24f)
        Text("Resistance: ${"%.1f".format(resistance)} Ω", fontSize = 11.sp)
        Slider(value = resistance, onValueChange = { resistance = it }, valueRange = 1f..12f)
        Text("Result: ${"%.2f".format(voltage / resistance)} A", color = WorldMint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = WorldInk.copy(alpha = .10f))
        Text("Geometry · Rectangle area", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text("Area = length × width", color = WorldMuted, fontSize = 10.sp)
        Text("Length: ${"%.1f".format(length)} units", fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        Slider(value = length, onValueChange = { length = it }, valueRange = 1f..20f)
        Text("Width: ${"%.1f".format(width)} units", fontSize = 11.sp)
        Slider(value = width, onValueChange = { width = it }, valueRange = 1f..20f)
        Text("Result: ${"%.1f".format(length * width)} square units", color = WorldMint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = WorldInk.copy(alpha = .10f))
        Text("Mathematics · Graph Explorer", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text("Explore y = mx + c. Change the slope and intercept to reshape the line.", color = WorldMuted, fontSize = 10.sp, lineHeight = 15.sp)
        Text("Slope (m): ${"%.1f".format(slope)}", fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        Slider(value = slope, onValueChange = { slope = it }, valueRange = -2f..2f)
        Text("Intercept (c): ${"%.1f".format(intercept)}", fontSize = 11.sp)
        Slider(value = intercept, onValueChange = { intercept = it }, valueRange = -5f..5f)
        Canvas(Modifier.fillMaxWidth().height(170.dp).padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)) {
            val left = 20.dp.toPx()
            val right = size.width - 12.dp.toPx()
            val top = 10.dp.toPx()
            val bottom = size.height - 18.dp.toPx()
            val centerX = (left + right) / 2f
            val centerY = (top + bottom) / 2f
            val scaleX = (right - left) / 10f
            val scaleY = (bottom - top) / 12f
            drawLine(WorldMuted.copy(alpha = .55f), Offset(left, centerY), Offset(right, centerY), strokeWidth = 1.5.dp.toPx())
            drawLine(WorldMuted.copy(alpha = .55f), Offset(centerX, top), Offset(centerX, bottom), strokeWidth = 1.5.dp.toPx())
            for (tick in -5..5) {
                val x = centerX + tick * scaleX
                val y = centerY - tick * scaleY
                drawLine(WorldMuted.copy(alpha = .18f), Offset(x, top), Offset(x, bottom), strokeWidth = 1.dp.toPx())
                drawLine(WorldMuted.copy(alpha = .18f), Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            }
            val graph = Path()
            for (step in 0..100) {
                val xValue = -5f + step / 10f
                val yValue = slope * xValue + intercept
                val px = centerX + xValue * scaleX
                val py = centerY - yValue * scaleY
                if (step == 0) graph.moveTo(px, py) else graph.lineTo(px, py)
            }
            drawPath(graph, WorldViolet, style = Stroke(width = 3.dp.toPx()))
        }
        Text("At x = 0, y = ${"%.1f".format(intercept)}. The slope tells how much y changes when x increases by 1.", color = WorldMuted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun StudyWorldScreen(progress: JSONArray, attempts: JSONArray, onAttempt: (String, String, Boolean) -> Unit, onComplete: (String, String) -> Unit, onOpenMochi: (String) -> Unit) {
    val regions = remember {
        worldRegions().map { it.copy(activities = it.activities + worldExtraActivities(it.id)) }
            .also(::validateWorldRegistry)
    }
    var selectedRegion by remember { mutableStateOf<String?>(null) }
    var selectedActivity by remember { mutableStateOf<WorldActivity?>(null) }
    var chosen by remember { mutableIntStateOf(-1) }
    var checked by remember { mutableStateOf(false) }
    var hintVisible by remember { mutableStateOf(false) }
    var completedPulse by remember { mutableIntStateOf(0) }
    var worldSearch by remember { mutableStateOf("") }
    val completed = remember(progress, completedPulse) {
        (0 until progress.length()).mapNotNull { progress.optJSONObject(it)?.optString("activityId") }.toSet()
    }
    val total = regions.sumOf { it.activities.size }
    val done = regions.sumOf { region -> region.activities.count { it.id in completed } }
    val attemptCount = (0 until attempts.length()).sumOf { attempts.optJSONObject(it)?.optInt("attemptCount") ?: 0 }
    val correctCount = (0 until attempts.length()).sumOf { attempts.optJSONObject(it)?.optInt("correctCount") ?: 0 }
    val accuracy = if (attemptCount == 0) 0 else correctCount * 100 / attemptCount
    val recentAttempt = (0 until attempts.length()).mapNotNull { attempts.optJSONObject(it) }.maxByOrNull { it.optLong("updatedAt") }
    val recentActivity = recentAttempt?.let { record -> regions.flatMap { it.activities }.firstOrNull { it.id == record.optString("activityId") } }
    val region = regions.firstOrNull { it.id == selectedRegion }
    val activity = selectedActivity
    val activityRegionId = activity?.let { active -> regions.firstOrNull { r -> r.activities.any { it.id == active.id } }?.id }.orEmpty()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp)) {
        if (activity != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedActivity = null; chosen = -1; checked = false; hintVisible = false }) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(activity.kind.uppercase(), color = WorldViolet, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    Text(activity.title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
                Text(activity.duration, color = WorldMuted, fontSize = 11.sp)
            }
            Text(activity.intro, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 14.dp))
            OutlinedButton(onClick = { hintVisible = !hintVisible }, modifier = Modifier.padding(top = 8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)) { Icon(Icons.Rounded.Lightbulb, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(if (hintVisible) "Hide hint" else "Show a hint", fontSize = 11.sp) }
            if (hintVisible) Text("Hint: ${activity.steps.firstOrNull() ?: "Review the lesson above and look for the key idea."}", color = WorldViolet, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth().padding(top = 5.dp).background(WorldViolet.copy(alpha = .07f), RoundedCornerShape(10.dp)).padding(11.dp))
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
                Button(onClick = { checked = true; onAttempt(activity.id, activityRegionId, chosen == activity.answer) }, enabled = chosen >= 0, modifier = Modifier.fillMaxWidth().padding(top = 15.dp), colors = ButtonDefaults.buttonColors(containerColor = WorldInk)) { Text("Check answer", color = Color.White) }
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
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(15.dp)).background(WorldPanel).clickable { selectedActivity = item; chosen = -1; checked = false; hintVisible = false }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
            IconButton(onClick = { onOpenMochi("Help me choose a topic to study in SDS Study World. Explain the key ideas simply and give me a short practice question.") }) { Icon(Icons.Rounded.AutoAwesome, "Ask Mochi", tint = WorldViolet) }
        }
        OutlinedTextField(value = worldSearch, onValueChange = { worldSearch = it }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), placeholder = { Text("Find a topic or region…", fontSize = 12.sp) }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(14.dp))
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
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).background(WorldPanel).padding(12.dp)) {
                Text("QUESTIONS TRIED", color = WorldMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                Text("$attemptCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WorldInk)
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).background(WorldPanel).padding(12.dp)) {
                Text("ANSWER ACCURACY", color = WorldMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                Text(if (attemptCount == 0) "—" else "$accuracy%", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WorldMint)
            }
        }
        if (recentActivity != null && recentActivity.id !in completed) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEDEAF6)).clickable {
                selectedRegion = null; selectedActivity = recentActivity; chosen = -1; checked = false; hintVisible = false
            }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PlayArrow, null, tint = WorldViolet)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("PICK UP WHERE YOU LEFT OFF", color = WorldViolet, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                    Text(recentActivity.title, color = WorldInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = WorldViolet)
            }
        }
        Text("Your learning map", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
        WorldMapPreview(regions, completed) { selectedRegion = it }
        Text("Explore and experiment", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
        WorldSimulationStudio()
        Text("Explore regions", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
        val visibleRegions = regions.filter { regionItem -> worldSearch.isBlank() || regionItem.name.contains(worldSearch, true) || regionItem.subtitle.contains(worldSearch, true) || regionItem.activities.any { it.title.contains(worldSearch, true) || it.kind.contains(worldSearch, true) } }
        if (visibleRegions.isEmpty()) Text("No matching topics. Try another search.", color = WorldMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 18.dp))
        visibleRegions.forEach { item ->
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
        OutlinedButton(onClick = { onOpenMochi("Help me explore the topics in SDS Study World. Suggest a beginner-friendly learning path and explain the first concept.") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp)) { Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(7.dp)); Text("Ask Mochi about a topic") }
    }
}
