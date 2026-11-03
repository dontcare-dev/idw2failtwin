# SDS Study

Native Android study app built with Kotlin and Jetpack Compose.

## Features

- Import PDF study material and extract text on the device
- Ask questions using imported study material
- Generate flashcards and multiple-choice practice questions
- Save quiz questions, retry previously missed questions, and review explanations
- Generate a 100-question exam paper in five formats
- Take an exam with a three-hour timer
- Export the generated exam paper as a PDF
- Create and track revision tasks
- Track syllabus chapters and topics with completion coverage
- Search imported PDF names and extracted text, and rename PDFs
- Organise PDFs by subject and filter the library by subject
- Ask questions about a selected PDF with page citations for newly imported page-indexed PDFs
- Generate visual AI mind maps and vector diagram-practice boards
- Analyse an imported past-paper PDF for question patterns and revision priorities
- Generate and save formula sheets with symbol and unit explanations
- Use Mochi voice input and read-aloud responses
- Track exam-readiness indicators from syllabus completion, MCQ attempts and flashcard mastery
- Search across PDFs, MCQs, flashcards, syllabus topics and saved formula sheets
- Use an offline mode that keeps saved study content available and pauses AI requests
- Use a local arithmetic calculator, common unit conversions and a quick element reference
- Mark flashcards as known or still learning, shuffle cards, and filter saved cards
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
- Syllabus coverage is entered and maintained manually; the app does not yet parse a syllabus document automatically.
- Page references are available for PDFs imported after page indexing was added; older imports need to be re-imported to build page indexes.
- Voice recognition depends on an installed Android speech recognition service. Read-aloud uses Android text-to-speech.
- Offline mode supports saved content and local tools; AI generation and PDF analysis still require internet access.
- Study data remains local; cloud account sync is not included.
- The generated paper depends on the quality and coverage of the imported material.
- API provider availability, model access, and usage limits depend on the configured account.
