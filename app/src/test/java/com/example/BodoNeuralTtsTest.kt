package com.example

import com.example.data.audio.BodoNeuralTtsEngine
import com.example.data.audio.BodoTtsNormalizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BodoNeuralTtsTest {

    @Test
    fun testOfflineBodoNeuralTtsSynthesis() = runBlocking {
        val modelCandidates = listOf(
            File("model.onnx"),
            File("../model.onnx"),
            File("/app/applet/model.onnx")
        )
        val modelFile = modelCandidates.firstOrNull { it.exists() } ?: File("model.onnx")

        val tokenCandidates = listOf(
            File("tokens.txt"),
            File("../tokens.txt"),
            File("/app/applet/tokens.txt")
        )
        val tokensFile = tokenCandidates.firstOrNull { it.exists() } ?: File("tokens.txt")

        assertTrue("model.onnx must exist for offline verification at ${modelFile.absolutePath}", modelFile.exists())
        assertTrue("tokens.txt must exist for offline verification at ${tokensFile.absolutePath}", tokensFile.exists())

        val engine = BodoNeuralTtsEngine(modelFile, tokensFile)
        val initialized = engine.initialize()
        assertTrue("BodoNeuralTtsEngine must initialize successfully", initialized)

        // 1. Test tokenization of Bodo sentence with tone modifier
        val rawInput = "बर' हारिनि गोदान सावरायनाय मोजां"
        val normalized = BodoTtsNormalizer.normalizeForTts(rawInput)
        val tokens = engine.tokenizeText(normalized)
        assertTrue("Tokens array must not be empty", tokens.isNotEmpty())
        assertTrue("Must include space token (16)", tokens.contains(16))

        // 2. Synthesize Female Voice (Mainao / BRX_F / Speaker 4)
        val femaleWav = File("build/test_female_output.wav")
        val resultFemale = engine.synthesizeToWav(
            text = normalized,
            voice = "female_mainao",
            speed = 1.0f,
            outputFile = femaleWav
        )
        assertTrue("Female synthesis must succeed: ${resultFemale.exceptionOrNull()?.message}", resultFemale.isSuccess)
        assertTrue("Generated WAV file must exist", femaleWav.exists())
        assertTrue("Generated WAV file must be > 10KB", femaleWav.length() > 10000L)

        // Verify WAV Header
        val header = ByteArray(44)
        FileInputStream(femaleWav).use { it.read(header) }
        val riff = String(header, 0, 4)
        val wave = String(header, 8, 4)
        assertEquals("RIFF", riff)
        assertEquals("WAVE", wave)

        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val sampleRate = bb.getInt(24)
        val bitsPerSample = bb.getShort(34).toInt()
        val channels = bb.getShort(22).toInt()

        assertEquals("Sample rate must be 22,050 Hz", 22050, sampleRate)
        assertEquals("Bits per sample must be 16-bit PCM", 16, bitsPerSample)
        assertEquals("Channels must be 1 (mono)", 1, channels)

        // 3. Synthesize Male Voice (Birphung / BRX_M / Speaker 5) with speed test
        val maleWav = File("build/test_male_output.wav")
        val resultMale = engine.synthesizeToWav(
            text = normalized,
            voice = "male_birphung",
            speed = 1.1f,
            outputFile = maleWav
        )
        assertTrue("Male synthesis must succeed: ${resultMale.exceptionOrNull()?.message}", resultMale.isSuccess)
        assertTrue("Male WAV must exist", maleWav.exists())
        assertTrue("Male WAV must be > 10KB", maleWav.length() > 10000L)

        // Clean up
        engine.close()
    }
}
