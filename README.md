# idw2fail — Android

Native Android study companion with a dark liquid-glass interface.

## Stack
- Kotlin + Jetpack Compose
- Local JSON file for study data
- Android EncryptedSharedPreferences for the user's Groq API key
- PDFBox-Android for local PDF text extraction
- Groq Chat Completions API for AI tutoring

## Open and build
1. Open this repository in Android Studio.
2. Let Gradle sync complete (JDK 17, Android SDK 35).
3. Run the `app` configuration on an Android device or emulator.
4. In Settings, add your own Groq API key.
5. Build an APK with **Build → Build APK(s)**.

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Local-first data
Study notes and imported document text are stored in the app's private `filesDir/study-data.json`. The Groq key is stored separately using Android encrypted preferences. Nothing is uploaded automatically. When the student asks the AI tutor a question, the prompt and a limited excerpt of imported document text are sent to Groq.

## Current implementation
- Dark glass-inspired Compose UI
- Home dashboard and navigation
- Local PDF import and text extraction
- Local notes
- Encrypted on-device Groq API key settings
- AI tutor with imported-document context

Quiz generation, flashcards, mock exams, planner, progress analytics, DOCX import, and backup/export are planned next. The app does not display fake completion data for these features.

## Privacy
The API key is not written to the JSON study file. Keep the device locked and do not share your key. Removing app data or uninstalling the app removes its local study data and stored key.
