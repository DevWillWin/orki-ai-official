import com.example.data.audio.BodoTtsNormalizer
import com.example.data.language.BodoLexicon
import com.example.data.language.VisualIntentDetector
import java.util.Locale

/** Runs real pure JVM code without Android, Gradle, mock transliterators or JUnit. */
fun main() {
    var checks = 0
    fun verify(condition: Boolean, description: String) {
        check(condition) { description }
        checks++
    }
    for (word in listOf("swima", "suima", "saima", "सैमा", "SWIMA")) {
        verify(BodoLexicon.lookup(word)?.id == "dog", "dog lookup: $word")
    }
    BodoLexicon.entries.forEach { entry ->
        (entry.roman + entry.devanagari).forEach { form ->
            verify(BodoLexicon.lookup(form)?.id == entry.id, "lookup identity: $form")
        }
    }
    verify(BodoLexicon.lookup("swimming") == null, "English collision")
    verify(BodoLexicon.lookup("swimafwr") == null, "no speculative stemming")
    verify(BodoLexicon.matches("2 suima in hagra, no people").map { it.id } == listOf("dog", "forest"), "Roman sentence matches")
    verify(BodoLexicon.matches("2 सैमा, no people").map { it.id } == listOf("dog"), "Devanagari sentence matches")
    val previousLocale = Locale.getDefault()
    try {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        verify(BodoLexicon.lookup("SUIMA")?.id == "dog", "locale independent lookup")
    } finally { Locale.setDefault(previousLocale) }
    val samples = mapOf(
        "swima" to "सैमा।",
        "suima" to "सैमा।",
        "saima" to "सैमा।",
        "सैमा" to "सैमा।",
        "सैमा।" to "सैमा।",
        "मावजि" to "मावजि।",
        "मोजां" to "मोजां।",
        "ैमा" to "ऐमा।",
        "phone swima" to "फोन सैमा।",
        "सैमा, suima!" to "सैमा, सैमा।",
        "swima,सैमा" to "सैमा, सैमा।",
        "swima,mousi" to "सैमा, मावजि।",
        "**2 swima** 🐕" to "नै सैमा।",
        "10 swima" to "जि सैमा।",
        "" to ""
    )
    for ((input, expected) in samples) {
        val result = BodoTtsNormalizer.normalizeForTts(input)
        verify(result == expected, "Speech normalization '$input': expected '$expected', actual '$result'")
        verify(BodoTtsNormalizer.normalizeForTts(result) == result, "Idempotent speech normalization: $input")
    }
    val intentCases = mapOf(
        "/IMAGE suima" to "suima",
        "create an image of 2 suima, watercolor, no people" to "2 suima, watercolor, no people",
        "draw me a cat" to "cat",
        "छबि बानाय सैमा, no people, text छबि" to "छबि बानाय सैमा, no people, text छबि",
        "/image 2 सैमा, no robots" to "2 सैमा, no robots"
    )
    for ((input, expected) in intentCases) {
        verify(VisualIntentDetector.extract(input)?.prompt == expected, "Image routing preserves intent: $input")
    }
    verify(VisualIntentDetector.extract("What does swima mean?") == null, "Vocabulary query stays chat")
    verify(VisualIntentDetector.extract("/image") == null, "Empty command does not render")
    println("PASS: $checks JVM assertions against actual lexicon/normalizer/intent code; not a speech-quality or model-language test.")
}
