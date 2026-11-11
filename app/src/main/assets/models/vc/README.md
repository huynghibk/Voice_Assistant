# Voice Conversion models (optional)

Place OpenVoice / FreeVC-lite ONNX here:

- `converter.onnx`

Pipeline:
`Text → Piper base PCM → converter.onnx (+ speaker embedding) → target PCM`

If this file is missing, the app uses embedding-guided spectral timbre transfer
so clone UX still works on-device.
