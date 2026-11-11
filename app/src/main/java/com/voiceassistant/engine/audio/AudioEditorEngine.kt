package com.voiceassistant.engine.audio

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.voiceassistant.domain.model.AudioClip
import com.voiceassistant.domain.model.TimelineProject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a single FFmpeg complex filtergraph from AudioClip metadata.
 * Edits never rewrite intermediate clip files — only the final render does.
 */
@Singleton
class AudioEditorEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun renderTimeline(
        project: TimelineProject,
        outputFile: File,
        sampleRate: Int = 44_100
    ): File = withContext(Dispatchers.IO) {
        require(project.clips.isNotEmpty()) { "Timeline trống" }
        outputFile.parentFile?.mkdirs()

        val clips = project.clips.sortedWith(compareBy({ it.trackIndex }, { it.startOffsetInTimelineMs }))
        val inputs = mutableListOf<String>()
        val filterParts = mutableListOf<String>()
        val mixLabels = mutableListOf<String>()

        clips.forEachIndexed { index, clip ->
            inputs += "-i"
            inputs += clip.sourceFilePath

            val trimStartSec = clip.trimStartMs / 1000.0
            val trimEndSec = clip.trimEndMs?.let { it / 1000.0 }
            val delayMs = clip.startOffsetInTimelineMs
            val label = "c$index"

            val trim = buildString {
                append("[$index:a]atrim=start=$trimStartSec")
                if (trimEndSec != null) append(":end=$trimEndSec")
                append(",asetpts=PTS-STARTPTS")
                if (clip.speed != 1f) {
                    // atempo valid range 0.5–2.0; chain if needed
                    append(buildAtempoChain(clip.speed))
                }
                if (clip.pitch != 1f) {
                    val rate = (sampleRate * clip.pitch).toInt()
                    append(",asetrate=$rate,aresample=$sampleRate")
                }
                append(",volume=${clip.volume.coerceIn(0f, 2f)}")
                if (clip.fadeInMs > 0) append(",afade=t=in:st=0:d=${clip.fadeInMs / 1000.0}")
                if (clip.fadeOutMs > 0) {
                    val dur = ((clip.trimEndMs ?: (trimStartSec * 1000 + 3000).toLong()) - clip.trimStartMs) / 1000.0
                    val start = (dur - clip.fadeOutMs / 1000.0).coerceAtLeast(0.0)
                    append(",afade=t=out:st=$start:d=${clip.fadeOutMs / 1000.0}")
                }
                append(",adelay=${delayMs}|${delayMs}")
                append("[$label]")
            }
            filterParts += trim
            mixLabels += "[$label]"
        }

        val mix = if (mixLabels.size == 1) {
            "${mixLabels[0]}anull[outa]"
        } else {
            "${mixLabels.joinToString("")}amix=inputs=${mixLabels.size}:dropout_transition=0:normalize=0[outa]"
        }
        filterParts += mix
        val filterComplex = filterParts.joinToString(";")

        val cmd = buildString {
            append(inputs.joinToString(" "))
            append(" -filter_complex \"$filterComplex\"")
            append(" -map \"[outa]\" -ar $sampleRate -ac 1 -y \"${outputFile.absolutePath}\"")
        }

        val session = FFmpegKit.execute(cmd)
        if (!ReturnCode.isSuccess(session.returnCode)) {
            // Fallback: simple concat copy when filtergraph fails
            fallbackConcat(clips, outputFile)
        }
        outputFile
    }

    private fun buildAtempoChain(speed: Float): String {
        var remaining = speed.coerceIn(0.25f, 4f)
        val parts = mutableListOf<String>()
        while (remaining > 2f) {
            parts += "atempo=2.0"
            remaining /= 2f
        }
        while (remaining < 0.5f) {
            parts += "atempo=0.5"
            remaining /= 0.5f
        }
        parts += "atempo=${"%.3f".format(remaining)}"
        return parts.joinToString(prefix = ",", separator = ",")
    }

    private fun fallbackConcat(clips: List<AudioClip>, outputFile: File) {
        val listFile = File(context.cacheDir, "concat_${System.currentTimeMillis()}.txt")
        listFile.writeText(clips.joinToString("\n") { "file '${it.sourceFilePath}'" })
        val session = FFmpegKit.execute(
            "-f concat -safe 0 -i \"${listFile.absolutePath}\" -c copy -y \"${outputFile.absolutePath}\""
        )
        if (!ReturnCode.isSuccess(session.returnCode)) {
            // Last resort: copy first clip
            File(clips.first().sourceFilePath).copyTo(outputFile, overwrite = true)
        }
        listFile.delete()
    }
}
