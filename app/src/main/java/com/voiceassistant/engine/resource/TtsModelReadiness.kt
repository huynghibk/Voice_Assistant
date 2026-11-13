package com.voiceassistant.engine.resource

import java.io.File

object TtsModelReadiness {

    private const val MIN_ONNX_BYTES = 512L * 1024L

    fun isOnnxReady(modelFile: File): Boolean {
        if (!modelFile.exists() || modelFile.length() < MIN_ONNX_BYTES) return false
        val parent = modelFile.parentFile ?: return false
        val name = modelFile.name.lowercase()
        val path = modelFile.path.lowercase()
        when {
            "melo" in name || "melo" in path -> {
                return File(parent, "lexicon.txt").let { it.exists() && it.length() > 100 } &&
                    File(parent, "tokens.txt").let { it.exists() && it.length() > 10 } &&
                    File(parent, "dict").isDirectory
            }
            "mms" in name || "mms" in path -> {
                return File(parent, "tokens.txt").let { it.exists() && it.length() > 10 }
            }
            else -> {
                val json = File("$modelFile.json")
                return json.exists() && json.length() > 100
            }
        }
    }
}
