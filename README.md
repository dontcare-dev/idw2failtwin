# SDS-IDW2FAILTWIN

Native Android study app built with Kotlin and Jetpack Compose.

## Features

- Import PDF study material and extract text on the device
- Ask questions using imported study material
- Generate flashcards and multiple-choice practice questions
- Save quiz questions and review answers
- Generate a 100-question exam paper in five formats
- Take an exam with a three-hour timer
- Export the generated exam paper as a PDF
- Create and track revision tasks
- View local study activity
- Export and restore a JSON backup
- Use Groq, OpenAI, or Gemini with API keys stored in encrypted preferences

## Build

Requirements:

- JDK 17
- Android SDK 35
- Gradle 8.9

Run `gradle assembleDebug` to build the debug APK. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds the APK on pushes to `main` and publishes `idw2fail-debug-apk` as a workflow artifact.

## Storage and privacy

Imported PDF text, flashcards, quiz questions, exam papers, and planner tasks are stored in the app's private local storage. API keys are stored using Android encrypted preferences.

When an AI feature is used, the relevant prompt and study text are sent to the selected provider. Provider privacy and retention policies apply. No account or remote database is used by this app.

## Limitations

- PDF text extraction does not include OCR for scanned pages.
- DOCX files are not supported.
- Exam answers are collected in the app but are not automatically graded.
- The generated paper depends on the quality and coverage of the imported material.
- API provider availability, model access, and usage limits depend on the configured account.
