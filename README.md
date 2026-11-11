# Voice Assistant (Trợ lý Giọng nói)

Android offline TTS + voice clone + studio timeline + AI search.

## Features
- Quick TTS streaming (TextSplitter + queue + AudioTrack)
- FileProvider share (Bluetooth / Quick Share / Zalo…) — no `file://`
- AudioRoutingManager: Bluetooth buffer ×4, ACTION_AUDIO_BECOMING_NOISY, MediaRouter Cast
- Voice clone: Piper → Voice Conversion
- Multi-track Timeline + FFmpeg filtergraph
- Import TXT/DOCX/EPUB/PDF, ML Kit translate, export WAV/MP3/M4A/FLAC
- AI Search: Room `search_history` + FTS4 projects + DuckDuckGo + TTS đọc kết quả

## Run
Open in Android Studio → Sync → Run (API 26+).

## Models
- `assets/models/piper/*.onnx`
- `assets/models/vc/converter.onnx` (optional)
