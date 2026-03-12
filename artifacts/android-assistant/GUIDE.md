# Complete Beginner's Guide — Android Assistant App

## What Each File Does

```
android-assistant/
├── app/src/main/
│   ├── AndroidManifest.xml          ← App permissions + component registration
│   ├── java/com/example/assistant/
│   │   ├── MainActivity.kt          ← The screen: connects everything together
│   │   ├── CommandParser.kt         ← Offline: understands what you said
│   │   ├── VoiceInputHandler.kt     ← Handles the microphone
│   │   ├── ActionHandler.kt         ← Does the action (open app, call, alarm, search)
│   │   └── GeminiHelper.kt          ← Online: sends command to Gemini AI
│   └── res/
│       ├── layout/activity_main.xml ← The visual layout (what you see on screen)
│       ├── values/strings.xml       ← All text in the app
│       ├── values/colors.xml        ← Color definitions
│       └── values/themes.xml        ← App theme / style
├── build.gradle.kts                 ← App module build config
├── settings.gradle.kts              ← Project settings
├── gradle/libs.versions.toml        ← All library versions in one place
├── gradlew                          ← Build script for Linux/Mac
└── .github/workflows/build-apk.yml ← GitHub Actions: auto-build APK
```

---

## Step 1: Set Up Your Gemini API Key

1. Go to https://aistudio.google.com/
2. Click **Get API key** → **Create API key**
3. Copy the key
4. Open `app/src/main/java/com/example/assistant/GeminiHelper.kt`
5. Replace `YOUR_GEMINI_API_KEY` with your key:
   ```kotlin
   private val apiKey = "AIzaSy..."
   ```

> **Note:** The app works perfectly offline without a key. The key only enables smarter command understanding when you have internet.

---

## Step 2A: Build Using GitHub Actions (Recommended for Beginners)

This is the easiest way — GitHub builds the APK for you in the cloud.

1. **Create a GitHub account** at https://github.com if you don't have one
2. **Create a new repository** (click the + icon → New repository)
3. **Push this project:**
   ```bash
   cd android-assistant
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/android-assistant.git
   git push -u origin main
   ```
4. **Wait ~5 minutes** for the build to finish
5. Go to your repo → **Actions tab** → click the latest run
6. Scroll down to **Artifacts** → download **debug-apk**
7. Transfer the APK to your Android phone and install it
   - You may need to enable "Install from unknown sources" in Android Settings

---

## Step 2B: Build Using Termux (On Your Android Phone)

Termux lets you build Android apps directly on your phone!

### Install Termux
1. Install **Termux** from F-Droid (https://f-droid.org) — NOT from Play Store (outdated version)
2. Open Termux

### Set Up the Build Environment
```bash
# Update packages
pkg update && pkg upgrade

# Install OpenJDK 17
pkg install openjdk-17

# Verify Java is installed
java -version

# Install git
pkg install git

# Clone your project
git clone https://github.com/YOUR_USERNAME/android-assistant
cd android-assistant
```

### Build the APK
```bash
# Make the build script executable
chmod +x gradlew

# You need the Android SDK. The easiest option is:
# Install commandlinetools
pkg install android-tools

# Or download SDK manually (this takes ~500MB):
# mkdir -p ~/android-sdk/cmdline-tools
# wget https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip
# unzip commandlinetools-*.zip -d ~/android-sdk/cmdline-tools/
# mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest

# Set SDK path
echo "sdk.dir=$HOME/android-sdk" > local.properties

# Accept SDK licenses
yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses

# Build the debug APK
./gradlew assembleDebug

# The APK is at:
ls app/build/outputs/apk/debug/app-debug.apk
```

### Install Directly
```bash
# If your device has ADB debugging enabled:
adb install app/build/outputs/apk/debug/app-debug.apk

# Or copy to Downloads and open with a file manager:
cp app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/
```

---

## Step 2C: Build Using Android Studio (Desktop)

1. Download Android Studio from https://developer.android.com/studio
2. Open the `android-assistant` folder in Android Studio
3. Wait for Gradle sync to complete (first time takes ~5 minutes)
4. Click the green **Run ▶** button (or press Shift+F10)
5. Select your device or emulator

---

## Step 3: Test the App

Once installed, try these commands by typing or speaking:

| What to say/type | What happens |
|-----------------|--------------|
| `open camera` | Opens the camera app |
| `open settings` | Opens Android Settings |
| `open chrome` | Opens Chrome browser |
| `call 555-1234` | Dials that number |
| `set alarm 7am` | Creates an alarm for 7:00 AM |
| `alarm 6:30pm` | Creates an alarm for 6:30 PM |
| `search weather today` | Searches Google for weather |
| `find pizza recipes` | Searches Google for pizza recipes |

### Test Auto-Correct (Offline)
Type these misspelled commands — the app should suggest corrections:
- `opeen camera` → suggests "open camera"
- `serch weather` → suggests "search weather"
- `caal John` → suggests "call John"

### Test Online vs Offline
- Turn off WiFi and mobile data → the chip says "Offline" and CommandParser handles it
- Turn WiFi back on → the chip says "Online – Gemini" and Gemini handles it

---

## Common Issues

**"App not installed" error when sideloading the APK:**
- Go to Settings → Security → Install unknown apps
- Allow your file manager to install apps

**Voice recognition not working:**
- Make sure you granted the microphone permission
- The device needs Google's speech recognition service installed
- Try typing the command instead

**Gemini returns empty results:**
- Check that your API key is correct in GeminiHelper.kt
- Make sure you have internet connectivity
- The app automatically falls back to offline mode if Gemini fails

**App crashes on `call` command:**
- Make sure the CALL_PHONE permission is granted in Settings → Apps → Assistant → Permissions

---

## How to Customize

### Add a New App to the "open" Command
In `ActionHandler.kt`, find the `knownApps` map and add your app:
```kotlin
val knownApps = mapOf(
    "camera"    to "com.android.camera2",
    "myapp"     to "com.yourcompany.yourapp",  // ← add here
    // ...
)
```
To find an app's package name: go to Play Store → click the app → copy the `id=...` from the URL.

### Add a New Command Type
1. Add a new `data class` in `CommandParser.kt`:
   ```kotlin
   data class PlayMusic(val song: String) : ParsedCommand()
   ```
2. Add detection logic in `CommandParser.parse()`:
   ```kotlin
   text.startsWith("play ") -> ParsedCommand.PlayMusic(text.removePrefix("play "))
   ```
3. Handle it in `ActionHandler.execute()`:
   ```kotlin
   is ParsedCommand.PlayMusic -> playMusic(context, command.song)
   ```

### Change the App Theme Colors
Edit `app/src/main/res/values/colors.xml` and `themes.xml`.
