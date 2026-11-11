# Piper voice models

Place Piper ONNX voice packs here, for example:

- `en_US-amy-low.onnx`
- `en_US-amy-low.onnx.json`
- `en_US-ryan-low.onnx`
- `vi_VN-*.onnx` (when available)

Download community Piper voices from the Piper / rhasspy voice release pages,
then copy the `.onnx` + `.json` pair into this folder (or into the app
`filesDir/models/piper/` after install).

Until a model file is present, the app uses a demo tone synthesizer so that
TextSplitter + queue + AudioTrack streaming can still be tested end-to-end.
