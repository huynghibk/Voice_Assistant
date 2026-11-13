# Role & Project Context
You are an expert Android developer (Kotlin / Java / Jetpack Compose / Gradle) working on Windows (`cmd.exe`).

## 1. Terminal, Gradle & RTK Token Optimization
- **Use IDE Tools First**: Prefer built-in file search, read, and symbol tools over running shell commands.
- **No Automatic Builds or Syncs**: Never execute `gradlew.bat` build, assemble, install, or sync commands automatically. The user will manually sync Gradle and build/run APKs inside Android Studio.
- **RTK Prefix**: Prefix standard CLI commands with `C:\rtk-ai\rtk.exe` (e.g., `C:\rtk-ai\rtk.exe git status`, `C:\rtk-ai\rtk.exe git diff`, `C:\rtk-ai\rtk.exe git log`).
- **Windows Commands**: Do **NOT** wrap `dir` or `cd` with `rtk.exe`. Always use Command Prompt (`cmd.exe`) syntax. Never call `powershell.exe` or PowerShell cmdlets (`Get-ChildItem`, etc.).

## 2. Code Style & Surgical Edits
- **Surgical Changes**: Modify only the lines required for the task. Do not refactor unrelated files or formatting.
- **Comments**: Never write verbose KDoc/Javadoc blocks (`/** ... */`) on new functions/classes. Remove existing `/** ... */` blocks in functions you modify, keeping only concise `//` inline comments for complex logic.
- **No Hardcoded UI Strings**: Place user-visible Android UI strings in `app/src/main/res/values/strings.xml` (and localized `values-vi`, `values-zh` if present) rather than hardcoding text in Compose/XML.

## 3. Android UI/UX & Async Operations
- **Consistent & Compact UI**: Maintain consistent Material theme, colors, and typography across screens. Keep layouts clean, intuitive, and responsive across different phone screen sizes and font scales.
- **Loading State**: For slow operations (voice processing, network API calls, file I/O), always display a clear loading indicator/overlay and disable duplicate button clicks to prevent overlapping requests or ANR.

## 4. End-User Documentation (Manuals / SOP)
- When updating `USER_MANUAL.md` or user guides, write strictly for non-technical end users. Never include code snippets, Gradle commands, API details, or developer jargon.
