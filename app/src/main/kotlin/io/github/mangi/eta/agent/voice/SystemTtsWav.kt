package io.github.mangi.eta.agent.voice

/** 系统引擎合成结果：单声道 16 位 PCM，以及引擎实际输出的采样率。 */
internal data class SystemTtsClip(val pcm: ByteArray, val sampleRate: Int)

/**
 * 解析系统 TTS 引擎通过 synthesizeToFile 写出的 WAV 文件。
 *
 * 采样率不能写死：不同引擎、不同音色会输出 16k / 22.05k / 24k / 48k，
 * 播放时必须按这里读到的实际值建音轨，否则会变调变速。
 * 只接受单声道 16 位 PCM，其余格式返回 null，由调用方回退到引擎直接播报。
 */
internal object SystemTtsWav {
    fun parse(bytes: ByteArray): SystemTtsClip? {
        if (bytes.size < 44) return null
        if (!bytes.hasAscii(0, "RIFF") || !bytes.hasAscii(8, "WAVE")) return null
        var offset = 12
        var format = -1
        var channels = 0
        var sampleRate = 0
        var bits = 0
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = bytes.intLe(offset + 4)
            val body = offset + 8
            when (id) {
                "fmt " -> {
                    if (size < 16 || body + 16 > bytes.size) return null
                    format = bytes.shortLe(body)
                    channels = bytes.shortLe(body + 2)
                    sampleRate = bytes.intLe(body + 4)
                    bits = bytes.shortLe(body + 14)
                }
                "data" -> {
                    if (format != 1 || channels != 1 || bits != 16 || sampleRate < MIN_SAMPLE_RATE) return null
                    // 部分引擎在流式写出时把长度字段留成 0 或 0xffffffff，按文件实际长度读。
                    val end = if (size <= 0 || body + size > bytes.size) bytes.size else body + size
                    val length = (end - body) and 1.inv()
                    if (length <= 0) return null
                    return SystemTtsClip(bytes.copyOfRange(body, body + length), sampleRate)
                }
            }
            if (size < 0) return null
            val advance = size + (size and 1)
            if (body + advance > bytes.size) return null
            offset = body + advance
        }
        return null
    }

    private const val MIN_SAMPLE_RATE = 8_000
}

private fun ByteArray.hasAscii(at: Int, ascii: String): Boolean {
    if (at + ascii.length > size) return false
    for (index in ascii.indices) if (this[at + index] != ascii[index].code.toByte()) return false
    return true
}

private fun ByteArray.shortLe(at: Int): Int =
    (this[at].toInt() and 0xff) or ((this[at + 1].toInt() and 0xff) shl 8)

private fun ByteArray.intLe(at: Int): Int =
    (this[at].toInt() and 0xff) or ((this[at + 1].toInt() and 0xff) shl 8) or
        ((this[at + 2].toInt() and 0xff) shl 16) or ((this[at + 3].toInt() and 0xff) shl 24)

/**
 * 把句尾静音整形成固定的 [targetMs]，短了补、长了裁。
 *
 * 逐句合成时，句末停顿原本由引擎各自决定：有的几乎不留，有的留很长。
 * 拼接后句号处的停顿可能比句内的逗号还短，听感破碎，所以这里统一标定成目标时长。
 * 整段静音时原样返回，不做增删。
 */
internal fun shapeTailSilence(
    pcm: ByteArray,
    sampleRate: Int,
    targetMs: Int = 300,
    threshold: Int = 200,
): ByteArray {
    if (sampleRate <= 0 || targetMs <= 0) return pcm
    val frames = pcm.size / 2
    if (frames == 0) return pcm
    var lastVoiced = frames - 1
    while (lastVoiced >= 0) {
        val sample = (pcm[lastVoiced * 2].toInt() and 0xff) or (pcm[lastVoiced * 2 + 1].toInt() shl 8)
        val amplitude = if (sample >= 0) sample else -sample
        if (amplitude >= threshold) break
        lastVoiced--
    }
    if (lastVoiced < 0) return pcm
    val voicedFrames = lastVoiced + 1
    val targetFrames = sampleRate.toLong() * targetMs / 1000
    val tail = frames - voicedFrames
    return when {
        tail > targetFrames -> pcm.copyOfRange(0, ((voicedFrames + targetFrames) * 2).toInt())
        tail < targetFrames -> pcm + ByteArray(((targetFrames - tail) * 2).toInt())
        else -> pcm
    }
}
