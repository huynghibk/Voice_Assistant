package com.voiceassistant.engine.resource

import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.repository.VoiceRepository
import com.voiceassistant.domain.resource.ResourcePackManager
import com.voiceassistant.domain.resource.TtsResourceGuard
import com.voiceassistant.domain.voice.ClonedVoiceSupport
import com.voiceassistant.engine.translation.LanguageCodes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsResourceGuardImpl @Inject constructor(
    private val resourcePackManager: ResourcePackManager,
    private val voiceRepository: VoiceRepository
) : TtsResourceGuard {

    override suspend fun advisePlay(voice: VoiceModel): TtsResourceGuard.PlayAdvice {
        val catalog = voiceRepository.listVoices()
        val ready = ClonedVoiceSupport.basePiperReady(voice, catalog, resourcePackManager)
        return if (ready) {
            TtsResourceGuard.PlayAdvice(usingSystemTts = false)
        } else {
            val message = if (ClonedVoiceSupport.isUserClone(voice)) {
                "Cần tải gói Piper (Tiếng Việt hoặc English) để đọc bằng giọng clone."
            } else {
                "Chưa có gói Piper — đang dùng giọng hệ thống."
            }
            TtsResourceGuard.PlayAdvice(
                usingSystemTts = true,
                message = message,
                suggestOpenModelManager = true
            )
        }
    }

    override suspend fun adviseTranslate(
        sourceLang: String,
        targetLang: String
    ): TtsResourceGuard.TranslateAdvice {
        val source = LanguageCodes.toMlKit(sourceLang)
        val target = LanguageCodes.toMlKit(targetLang)
        if (source == target) {
            return TtsResourceGuard.TranslateAdvice(canProceed = true, needsDownload = false)
        }
        val ready = resourcePackManager.isMlKitPairReady(source, target)
        if (ready) {
            return TtsResourceGuard.TranslateAdvice(canProceed = true, needsDownload = false)
        }
        val pair = resourcePackManager.listRecommendedPairs()
            .firstOrNull { it.sourceCode == source && it.targetCode == target }
            ?: resourcePackManager.listRecommendedPairs()
                .firstOrNull { it.sourceCode == target && it.targetCode == source }
        val label = pair?.displayLabel
            ?: "${source.uppercase()} ↔ ${target.uppercase()}"
        val size = pair?.estimatedSizeMb ?: 60
        return TtsResourceGuard.TranslateAdvice(
            canProceed = false,
            needsDownload = true,
            pairLabel = label,
            sourceCode = source,
            targetCode = target,
            estimatedSizeMb = size,
            message = "Cần tải gói dịch $label (~${size}MB) để dùng offline."
        )
    }
}
