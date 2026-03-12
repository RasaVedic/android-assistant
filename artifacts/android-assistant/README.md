# Android Assistant App

A simple, beginner-friendly Android assistant app built in Kotlin.

## Features
- Voice commands via microphone button
- Keyboard text commands
- Works **offline** with a local command parser
- Uses **Gemini Flash API** when internet is available
- Auto-corrects misspelled commands

## Commands Supported
| Command | Example |
|---------|---------|
| Open app | "open camera", "open chrome" |
| Make call | "call John", "call 555-1234" |
| Set alarm | "set alarm 7am", "alarm 6:30" |
| Search | "search weather today", "search recipes" |

## Project Structure
```
app/src/main/java/com/example/assistant/
├── MainActivity.kt          # UI: text input + mic button
├── CommandParser.kt         # Offline: matches commands + auto-correct
├── VoiceInputHandler.kt     # Handles microphone / speech recognition
├── ActionHandler.kt         # Executes the matched command (open app, call, alarm, search)
└── GeminiHelper.kt          # Online: sends command to Gemini Flash API
```

## Build & Run

### From Android Studio
1. Open the project in Android Studio
2. Connect a device or start an emulator
3. Click Run ▶

### Build APK via GitHub Actions
Push to the `main` branch — the APK is automatically built and uploaded as a GitHub Actions artifact.

### Build in Termux
```bash
# Install JDK and required tools
pkg install openjdk-17 gradle

# Clone your repo
git clone https://github.com/YOUR_USERNAME/android-assistant
cd android-assistant

# Build debug APK
./gradlew assembleDebug

# APK location
ls app/build/outputs/apk/debug/
```

## Gemini API Setup
1. Get a free API key from https://aistudio.google.com/
2. Open `app/src/main/java/com/example/assistant/GeminiHelper.kt`
3. Replace `YOUR_GEMINI_API_KEY` with your key
4. Rebuild the app

## Permissions Required
- `RECORD_AUDIO` — for voice input
- `CALL_PHONE` — for making calls
- `INTERNET` — for Gemini API (optional, offline works without it)
