# Offline TTS model packs

Download voices from **Settings → Model Manager**, or place files under the app `filesDir` mirror paths below.

## Vietnamese (recommended)

| Pack | Path (under `filesDir/`) | Source |
|------|---------------------------|--------|
| MMS HQ (default) | `models/sherpa/vits-mms-vie/model.onnx` + `tokens.txt` | Chưa có `vie/` trên [Symmetris-labs/tts-mms](https://huggingface.co/Symmetris-labs/tts-mms) — dùng Piper 25h trong app |
| Piper 25h | `models/piper/vi_VN-25hours_single-low.onnx` + `.json` | [vits-piper-vi_VN-25hours_single-low.tar.bz2](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-vi_VN-25hours_single-low.tar.bz2) |

## Chinese + English

`models/sherpa/vits-melo-tts-zh_en/` — `model.onnx`, `lexicon.txt`, `tokens.txt`, `dict/`, `*.fst`

Tarball: [vits-melo-tts-zh_en.tar.bz2](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2)

## English (Piper)

- `en_US-amy-low.onnx` + `.json`
- `en_US-ryan-low.onnx` + `.json`

Until a pack is present, the app falls back to Android system TTS.
