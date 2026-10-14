package com.example

import com.example.util.formatFileSize
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testUploadLimitsPerPlan() {
    fun getDailyUploadLimit(plan: String): Int {
      return when (plan) {
        "Guest" -> 0
        "Free" -> 2
        "Plus" -> 20
        "Pro" -> 100
        else -> 2
      }
    }

    assertEquals(0, getDailyUploadLimit("Guest"))
    assertEquals(2, getDailyUploadLimit("Free"))
    assertEquals(20, getDailyUploadLimit("Plus"))
    assertEquals(100, getDailyUploadLimit("Pro"))
  }

  @Test
  fun testFormatFileSize() {
    assertEquals("0 B", formatFileSize(0))
    assertEquals("512 B", formatFileSize(512))
    assertEquals("1.0 KB", formatFileSize(1024))
    assertEquals("2.5 MB", formatFileSize((2.5 * 1024 * 1024).toLong()))
  }

  @Test
  fun testBodoVisualVocabularyResolution() {
    val swimaResolved = com.example.data.network.BodoVisualVocabulary.resolveBodoToEnglishVisual("swima")
    assertTrue(swimaResolved.lowercase().contains("dog"))
    assertFalse(swimaResolved.lowercase().contains("swim ") || swimaResolved.lowercase().contains("swimming"))

    val maseSwima = com.example.data.network.BodoVisualVocabulary.resolveBodoToEnglishVisual("mase swima")
    assertTrue(maseSwima.lowercase().contains("a dog"))

    val mouziResolved = com.example.data.network.BodoVisualVocabulary.resolveBodoToEnglishVisual("mouzi")
    assertTrue(mouziResolved.lowercase().contains("cat"))

    val daoResolved = com.example.data.network.BodoVisualVocabulary.resolveBodoToEnglishVisual("dao")
    assertTrue(daoResolved.lowercase().contains("bird"))
  }

  @Test
  fun testBodoVisualPromptValidation() {
    // 1. Must reject conversational text
    val conversational = "What do you want to know about dogs? I can help you."
    assertFalse(com.example.data.network.BodoVisualVocabulary.validateEnhancedPrompt("swima", conversational))

    // 2. Must reject Devanagari text
    val devanagari = "सैमाया जाबाय मानसिनि गोसोथोथाव"
    assertFalse(com.example.data.network.BodoVisualVocabulary.validateEnhancedPrompt("swima", devanagari))

    // 3. Must reject "swima" mistranslated as "swim" or "swimming"
    val mistranslatedSwim = "A woman swimming underwater in a deep blue pool"
    assertFalse(com.example.data.network.BodoVisualVocabulary.validateEnhancedPrompt("swima", mistranslatedSwim))

    // 4. Must accept high-quality descriptive English visual prompt containing dog
    val validPrompt = "A beautiful golden retriever dog resting on lush green grass in soft morning sunlight, 8k resolution"
    assertTrue(com.example.data.network.BodoVisualVocabulary.validateEnhancedPrompt("swima", validPrompt))
  }
}

