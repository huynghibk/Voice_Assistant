# UI/UX Redesign Blueprint & Master Prompt: "VOICE ASSISTANT"

You are a Senior Mobile UI/UX Engineer and Jetpack Compose Expert. The core application logic, database, and AI engines of "NOI ONLINE" are already fully implemented and working. Your ONLY task is to refactor, polish, and redesign the existing presentation layer (UI/UX) to match a premium, high-end aesthetic.

Follow the instructions below to completely overhaul the application's interface.

---

## 1. DESIGN LANGUAGE & PREMIUM SYSTEM

- **Design Philosophy:** Premium Minimalist. Clean layouts, generous padding, clear typography hierarchy, and smooth, non-blocking motion animations. Absolutely NO cheap AI neon or cyberpunk effects.
- **Color Palette (Hex):**
  - Background: #121212 (Deep Charcoal) / Light Mode: #F5F5F0 (Soft Cream)
  - Surface: #1E1E1E (Surface Charcoal)
  - Primary / Accent Buttons: #FFBF00 (Amber Accent)
  - Text Primary: #F5F5F0 (Soft Cream)
- **Design References:** Leverage spacing and token layout rules from Trendyol/komposto, smooth chat animations from Google's compose-samples (Jetchat), and functional timeline flows from Jetcaster.

---

## 2. UI TARGET TARGETS & REFACTORING SCOPE

You will only modify and rewrite files inside the `presentation/` package:
```text
presentation/
  ├── theme/         # Complete overhaul of Color.kt, Type.kt, Theme.kt
  ├── home/          # Quick TTS Screen: clean input boxes, high-contrast action sliders
  ├── editor/        # Project Editor & Multi-track Timeline UI (CapCut-style clip matrix)
  ├── library/       # Voice Library Screen: smooth listing grids, elegant tagging badges
  └── search/        # SearchAssistantScreen: premium chat prompt bubbles & smooth scroll
```

---

## 3. STEP-BY-STEP REFACTORING PLAN (EXECUTE ONE BY ONE)

### STEP 1: GLOBAL PREMIUM THEMING (presentation/theme/)
- Overhaul the Jetpack Compose `MaterialTheme` configuration.
- Define explicit typography scales with custom line-heights to eliminate layout text rigidity.
- Set up both Dark and Light color palettes ensuring that high-contrast Amber accents pop out cleanly without breaking visual harmony.

### STEP 2: QUICK TTS & TEXT INPUT SCREEN (presentation/home/)
- Redesign the text field area with smooth focus transitions and optimal anti-slop padding.
- Simplify layout clutter: reduce the perceived rigidity of multi-option elements (like voice selectors or settings lists) using clean modern rows or compact bottom sheets rather than dense select boxes.
- Implement responsive Play/Stop/Cancel visual indicators that respond to state changes elegantly.

### STEP 3: STUDIO TIMELINE & PROJECT EDITOR (presentation/editor/)
- Redesign the Project Editor workspace to balance empty space and stable structural framing.
- Implement a modern multi-track audio layout using horizontal composition layers. Represent each audio clip as a distinct visual metadata card with rounded corners, subtle shadows, and drag indicators.
- Ensure the playhead cursor tracks positions fluidly without dropping rendering frames.

### STEP 4: AI SEARCH ASSISTANT & CHAT PROMPT (presentation/search/)
- Refactor the search/chat interface based heavily on Google's `Jetchat` sample.
- Build clean chat bubbles using distinct border clip corners. Use `SurfaceCharcoal` for received text and `Amber Accent` for user prompts.
- Ensure the keyboard layout transitions (`windowSoftInputMode`) animate seamlessly without layout stuttering or jumping elements.

---

## 4. UI CODE GENERATION PRINCIPLES
- Write 100% complete UI Composable functions. Do not use shortcuts like `// keep existing UI here`.
- Strictly enforce the premium minimalist color palette throughout all components.
- Rely purely on stateless composables driven by the existing ViewModels' UI State.
- Pause after completing each screen's layout modification and request visual validation before moving to the next target.
