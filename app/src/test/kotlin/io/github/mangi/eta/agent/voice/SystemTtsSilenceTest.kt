package io.github.mangi.eta.agent.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** 句尾停顿整形：统一成固定时长，让句号处的停顿长于句内逗号。 */
class SystemTtsSilenceTest {
    private val rate = 24_000
    private val targetFrames = rate * 300 / 1000

    @Test
    fun `尾部静音过长时裁到目标时长`() {
        val voiced = pcm(frames = 2_400, amplitude = 8_000)
        val silence = pcm(frames = 12_000, amplitude = 0)
        val shaped = shapeTailSilence(voiced + silence, rate)
        assertEquals((2_400 + targetFrames) * 2, shaped.size)
    }

    @Test
    fun `没有尾部静音时补足目标时长`() {
        val voiced = pcm(frames = 2_400, amplitude = 8_000)
        val shaped = shapeTailSilence(voiced, rate)
        assertEquals((2_400 + targetFrames) * 2, shaped.size)
    }

    @Test
    fun `尾部静音偏短时补足目标时长`() {
        val voiced = pcm(frames = 2_400, amplitude = 8_000)
        val tail = pcm(frames = 600, amplitude = 0)
        val shaped = shapeTailSilence(voiced + tail, rate)
        assertEquals((2_400 + targetFrames) * 2, shaped.size)
    }

    @Test
    fun `尾部时长刚好时原样返回`() {
        val voiced = pcm(frames = 2_400, amplitude = 8_000)
        val tail = pcm(frames = targetFrames, amplitude = 0)
        assertArrayEquals(voiced + tail, shapeTailSilence(voiced + tail, rate))
    }

    @Test
    fun `整段静音时不做增删`() {
        val silence = pcm(frames = 4_800, amplitude = 0)
        assertArrayEquals(silence, shapeTailSilence(silence, rate))
    }

    private fun pcm(frames: Int, amplitude: Int): ByteArray {
        val out = ByteArray(frames * 2)
        for (index in 0 until frames) {
            out[index * 2] = (amplitude and 0xff).toByte()
            out[index * 2 + 1] = ((amplitude shr 8) and 0xff).toByte()
        }
        return out
    }
}
