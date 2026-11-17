package io.github.mangi.eta.agent.voice

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/** 系统引擎 WAV 解析：采样率必须按文件实际值取，不能假定 24k。 */
class SystemTtsWavTest {
    @Test
    fun `读出 24k 单声道 PCM`() {
        val pcm = ByteArray(96) { it.toByte() }
        val clip = SystemTtsWav.parse(wav(sampleRate = 24_000, pcm = pcm))
        assertNotNull(clip)
        assertEquals(24_000, clip!!.sampleRate)
        assertArrayEquals(pcm, clip.pcm)
    }

    @Test
    fun `读出 22050 而不是回落成 24k`() {
        val clip = SystemTtsWav.parse(wav(sampleRate = 22_050, pcm = ByteArray(64)))
        assertEquals(22_050, clip!!.sampleRate)
    }

    @Test
    fun `读出 48k`() {
        val clip = SystemTtsWav.parse(wav(sampleRate = 48_000, pcm = ByteArray(64)))
        assertEquals(48_000, clip!!.sampleRate)
    }

    @Test
    fun `立体声与 8 位输出按不支持处理`() {
        assertNull(SystemTtsWav.parse(wav(sampleRate = 24_000, channels = 2, pcm = ByteArray(64))))
        assertNull(SystemTtsWav.parse(wav(sampleRate = 24_000, bits = 8, pcm = ByteArray(64))))
    }

    @Test
    fun `长度字段写成未知时按文件实际长度读`() {
        val pcm = ByteArray(48) { 7 }
        val clip = SystemTtsWav.parse(wav(sampleRate = 24_000, pcm = pcm, dataSize = 0xffffffff.toInt()))
        assertNotNull(clip)
        assertEquals(48, clip!!.pcm.size)
    }

    @Test
    fun `奇数字节被截到完整取样点`() {
        val clip = SystemTtsWav.parse(wav(sampleRate = 24_000, pcm = byteArrayOf(1, 2, 3)))
        assertEquals(2, clip!!.pcm.size)
    }

    @Test
    fun `不是 WAV 时返回空`() {
        assertNull(SystemTtsWav.parse(ByteArray(0)))
        assertNull(SystemTtsWav.parse(ByteArray(200) { 0 }))
    }

    private fun wav(
        sampleRate: Int,
        channels: Int = 1,
        bits: Int = 16,
        pcm: ByteArray,
        dataSize: Int = pcm.size,
        format: Int = 1,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        fun ascii(text: String) = out.write(text.toByteArray(Charsets.US_ASCII))
        fun le16(value: Int) {
            out.write(value and 0xff)
            out.write((value shr 8) and 0xff)
        }
        fun le32(value: Int) {
            out.write(value and 0xff)
            out.write((value shr 8) and 0xff)
            out.write((value shr 16) and 0xff)
            out.write((value shr 24) and 0xff)
        }
        ascii("RIFF"); le32(36 + pcm.size); ascii("WAVE")
        ascii("fmt "); le32(16); le16(format); le16(channels); le32(sampleRate)
        le32(sampleRate * channels * bits / 8); le16(channels * bits / 8); le16(bits)
        ascii("data"); le32(dataSize); out.write(pcm)
        return out.toByteArray()
    }
}
