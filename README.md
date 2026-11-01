# idw2fail — Android

A local-first Android study companion for school students, built with Kotlin and Jetpack Compose.

## Included
- Dark, glass-inspired native interface
- Home dashboard
- PDF library with on-device text extraction
- AI tutor using imported study material as context
- Local revision notes
- Flashcard deck with flip-through practice
- AI-generated flashcards based on imported PDF text
- Revision planner with task completion tracking
- Local progress dashboard for materials, flashcards, notes and completed tasks
- JSON backup export and restore through Android's document picker
- Groq API key stored in encrypted Android preferences
- Study data stored in the app's private JSON file

## Build
- JDK 17
- Android SDK 35
- Gradle 8.9
- Minimum Android version: Android 8.0 (API 26)

Open the project in Android Studio and run the `app` configuration. A debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions also builds the debug APK and publishes it as an artifact named `idw2fail-debug-apk` when the workflow succeeds.

## Data and privacy
Notes, planner tasks, flashcards and extracted PDF text are stored locally in the app's private `study-data.json` file. The Groq API key is stored separately with Android encrypted preferences. No account or remote database is required.

When you send a question to the AI tutor, your question and up to 14,000 characters of imported study text are sent to Groq. Do not add sensitive personal information to study documents or prompts. API provider terms and retention policies apply to content sent to that provider.

## Current limitations
The current build is an early functional foundation, not the complete original exam-preparation platform. AI-generated quizzes, timed mock exams, spaced repetition scheduling, subject/chapter organization, progress analytics, DOCX/OCR support, source-page citations, additional AI providers are not implemented yet.
