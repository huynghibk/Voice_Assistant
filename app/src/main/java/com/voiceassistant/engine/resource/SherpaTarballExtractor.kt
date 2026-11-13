package com.voiceassistant.engine.resource

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

// Extracts sherpa-onnx release tar.bz2 (vits-mms-vie, vits-melo-tts-zh_en, …) into filesDir.
object SherpaTarballExtractor {

    fun extract(tarBz2: File, destRoot: File) {
        require(tarBz2.exists() && tarBz2.length() > 1024) {
            "File tarball không hợp lệ: ${tarBz2.name}"
        }
        destRoot.mkdirs()
        FileInputStream(tarBz2).use { raw ->
            BZip2CompressorInputStream(BufferedInputStream(raw)).use { bz2 ->
                TarArchiveInputStream(bz2).use { tar ->
                    var entry: TarArchiveEntry? = tar.nextEntry
                    while (entry != null) {
                        val normalized = entry.name.replace('\\', '/').trimStart('/')
                        val slash = normalized.indexOf('/')
                        val relative = if (slash >= 0) normalized.substring(slash + 1) else normalized
                        if (relative.isNotEmpty() && !relative.endsWith("/")) {
                            val outFile = File(destRoot, relative)
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { output ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = tar.read(buf)
                                    if (n <= 0) break
                                    output.write(buf, 0, n)
                                }
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            }
        }
    }
}
