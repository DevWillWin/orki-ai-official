package com.example

import com.example.data.network.VisualPromptClarificationException
import com.example.data.network.VisualPromptProtocol
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VisualPromptProtocolTest {
    private fun result(original: String, enhanced: String) = JSONObject().apply {
        put("task", "image_prompt_enhancement")
        put("originalPrompt", original)
        put("enhancedPrompt", enhanced)
        put("needsClarification", false)
        put("clarificationQuestion", "")
    }

    @Test fun dedicatedRouteDoesNotSendChatPersonaOrLatinScript() {
        val request = VisualPromptProtocol.request("  suima  ", false)
        assertEquals("image_prompt_enhancement", request.getString("task"))
        assertEquals("suima", request.getString("prompt"))
        assertEquals("image", request.getString("mediaType"))
        assertEquals("roman", request.getString("script"))
        assertFalse(request.has("contents"))
        assertFalse(request.has("userPersona"))
    }

    @Test fun devanagariChoiceReachesClarificationContract() {
        assertEquals("deva", VisualPromptProtocol.request("सैमा", false, "deva").getString("script"))
    }

    @Test fun videoUsesSameInterpretationRouteWithoutForcingStyle() {
        assertEquals("video", VisualPromptProtocol.request("suima", true).getString("mediaType"))
    }

    @Test fun validResponsePreservesCountsStyleAndExclusions() {
        val original = "2 suima, watercolor, no people"
        val enhanced = "2 dogs, watercolor, no people"
        assertEquals(enhanced, VisualPromptProtocol.parse(result(original, enhanced).toString(), original))
    }

    @Test fun clarificationMustStopBeforeImageRendering() {
        val body = result("na", "").put("needsClarification", true)
            .put("clarificationQuestion", "Do you mean a fish?")
        try {
            VisualPromptProtocol.parse(body.toString(), "na")
            fail("Should require clarification")
        } catch (e: VisualPromptClarificationException) {
            assertEquals("Do you mean a fish?", e.message)
        }
    }

    @Test fun malformedAndChatResponsesAreRejected() {
        for (body in listOf("{}", "data: {}", result("swima", "---TTS--- सैमा").toString(),
            result("swima", "RESPONSE: Hi").toString(), result("swima", "").toString(),
            result("another request", "A dog").toString(), result("swima", "A dog").removeStatus())) {
            assertTrue(runCatching { VisualPromptProtocol.parse(body, "swima") }.isFailure)
        }
    }

    @Test fun allStreamingTextPartsAndTtsDelimiterArePreserved() {
        val json = """{"candidates":[{"content":{"parts":[{"text":"Oi "},{"text":"internal","thought":true},{"text":"swima\n---TTS---\nसैमा"}]}}]}"""
        assertEquals(listOf("Oi ", "swima\n---TTS---\nसैमा"),
            com.example.data.network.OrkiApiService.streamChunks(json))
    }

    @Test fun emptyAndDoneEventsDoNotProduceText() {
        assertTrue(com.example.data.network.OrkiApiService.streamChunks("[DONE]").isEmpty())
        assertTrue(com.example.data.network.OrkiApiService.streamChunks("{}").isEmpty())
    }

    @Test fun explicitUpstreamErrorsAreNotSilentlySwallowed() {
        assertTrue(runCatching {
            com.example.data.network.OrkiApiService.streamChunks("""{"error":{"message":"Model unavailable"}}""")
        }.exceptionOrNull() is IllegalStateException)
    }

    @Test fun dualResponseKeepsDisplayAndSpeechSeparate() {
        val result = com.example.data.network.OrkiApiService.parseDualResponse("swima\n---TTS---\nसैमा")
        assertEquals("swima", result.displayText)
        assertEquals("सैमा", result.ttsText)
    }

    private fun JSONObject.removeStatus(): String {
        remove("needsClarification")
        return toString()
    }
}
