package com.example

import com.example.data.audio.BodoTtsNormalizer
import com.example.data.language.BodoLexicon
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

/** Index and speech preprocessing regressions, not a claim of model language competence. */
class BodoLanguageRegressionTest {
    @Test fun dogVariantsShareEntry() {
        for (word in listOf("swima", "suima", "saima", "सैमा", "SWIMA")) {
            assertEquals("dog", BodoLexicon.lookup(word)?.id)
        }
        assertEquals("user_confirmed", BodoLexicon.lookup("swima")?.meaningStatus)
        assertEquals("provisional", BodoLexicon.lookup("सैमा")?.formStatus)
    }

    @Test fun everyStoredFormResolvesAndProvisionalIsNotVerified() {
        BodoLexicon.entries.forEach { entry ->
            (entry.roman + entry.devanagari).forEach { form ->
                assertEquals(entry.id, BodoLexicon.lookup(form)?.id)
            }
        }
        assertEquals("provisional", BodoLexicon.lookup("mousi")?.meaningStatus)
    }

    @Test fun lookupDoesNotRewriteOrStem() {
        assertNull(BodoLexicon.lookup("swimming"))
        assertNull(BodoLexicon.lookup("swimathon"))
        assertNull(BodoLexicon.lookup("swim"))
        assertNull(BodoLexicon.lookup("dog"))
        assertNull(BodoLexicon.lookup("swimafwr")) // suffix analysis is not implemented
    }

    @Test fun sentenceMatchesKeepContextAndDoNotChangeInput() {
        val roman = "2 suima in hagra, no people, watercolor"
        assertEquals(listOf("dog", "forest"), BodoLexicon.matches(roman).map { it.id })
        assertEquals("2 suima in hagra, no people, watercolor", roman)
        val deva = "2 सैमा, watercolor, no people"
        assertEquals(listOf("dog"), BodoLexicon.matches(deva).map { it.id })
        assertEquals("2 सैमा, watercolor, no people", deva)
    }

    @Test fun lookupIsIndependentOfDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("dog", BodoLexicon.lookup("SUIMA")?.id)
        } finally { Locale.setDefault(previous) }
    }

    @Test fun romanDogNormalization() {
        for (word in listOf("swima", "suima", "saima")) {
            assertEquals("सैमा।", BodoTtsNormalizer.normalizeForTts(word))
        }
    }

    @Test fun devanagariDogNormalization() {
        assertEquals("सैमा।", BodoTtsNormalizer.normalizeForTts("सैमा"))
        assertEquals("सैमा।", BodoTtsNormalizer.normalizeForTts("सैमा।"))
    }

    @Test fun validCombiningMarksAreNotMistakenForOrphanedMatras() {
        assertEquals("सैमा।", BodoTtsNormalizer.normalizeForTts("सैमा"))
        assertEquals("मावजि।", BodoTtsNormalizer.normalizeForTts("मावजि"))
        assertEquals("मोजां।", BodoTtsNormalizer.normalizeForTts("मोजां"))
        assertEquals("ऐमा।", BodoTtsNormalizer.normalizeForTts("ैमा"))
    }

    @Test fun loanwordDoesNotSkipRemainingRomanBodo() {
        val result = BodoTtsNormalizer.normalizeForTts("phone swima")
        assertEquals("फोन सैमा।", result)
        assertFalse(result.any { it in 'a'..'z' || it in 'A'..'Z' })
    }

    @Test fun mixedScriptsAndPunctuationDoNotSkipTokens() {
        assertEquals("सैमा, सैमा।", BodoTtsNormalizer.normalizeForTts("सैमा, suima!"))
        assertEquals("सैमा, सैमा।", BodoTtsNormalizer.normalizeForTts("swima,सैमा"))
    }

    @Test fun punctuationAdjacentRomanWordsStillUseDictionary() {
        assertEquals("सैमा, मावजि।", BodoTtsNormalizer.normalizeForTts("swima,mousi"))
    }

    @Test fun markdownEmojiAndNumbersRetainSpeechBehavior() {
        assertEquals("नै सैमा।", BodoTtsNormalizer.normalizeForTts("**2 swima** 🐕"))
        assertEquals("जि सैमा।", BodoTtsNormalizer.normalizeForTts("10 swima"))
        assertEquals("", BodoTtsNormalizer.normalizeForTts(""))
    }

    @Test fun speechNormalizationIsIdempotentForKnownOutput() {
        val once = BodoTtsNormalizer.normalizeForTts("phone 2 suima!")
        assertEquals(once, BodoTtsNormalizer.normalizeForTts(once))
    }
}
