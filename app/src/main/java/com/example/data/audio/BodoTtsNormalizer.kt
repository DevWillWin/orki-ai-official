package com.example.data.audio

import java.util.regex.Pattern
import java.util.Locale
import com.example.data.language.BodoLexicon

/**
 * Speech-only cleanup and legacy Roman Bodo transliteration fallback.
 * Never use this to interpret/rewrite chat or image requests. Dictionary forms
 * and unknown-word phonetics are inherited/provisional, not verified Bodo orthography.
 * The fallback is heuristic; successful normalization does not prove intelligible TTS.
 */
object BodoTtsNormalizer {

    private val EMOJI_REGEX = Pattern.compile(
        "[\uD83C-\uDBFF\uDC00-\uDFFF]|[\u2600-\u27BF]|[\uE000-\uF8FF]|[\uFE00-\uFE0F]|[\u200D\u200C]"
    )

    private val MARKDOWN_REGEX = Regex("[#*_`~>\\[\\]()|{}]")

    // Common Bodo words in colloquial Roman script mapped directly to standard Bodo Devanagari (बर' हांखो)
    private val ROMAN_BODO_DICTIONARY = linkedMapOf(
        "khulumbai" to "खुलुमबाय",
        "hambai" to "हामबाय",
        "sabaykhar" to "साबायखर",
        "sabayzanai" to "साबायजानय",
        "mwjang" to "मोजां",
        "mwzang" to "मोजां",
        "mabwi" to "माबोरै",
        "mabrwi" to "माबोरै",
        "mabwrwi" to "माबोरै",
        "bwrwi" to "बोरै",
        "jwmwi" to "जोमै",
        "zomwi" to "जोमै",
        "dong" to "दं",
        "donga" to "दङ",
        "dongw" to "दङ",
        "gwiya" to "गैया",
        "gaiya" to "गैया",
        "nwng" to "नों",
        "nwngni" to "नोंनि",
        "nwngha" to "नोंहा",
        "nwngkhwo" to "नोंखौ",
        "nwngkhw" to "नोंखौ",
        "ang" to "आं",
        "angni" to "आंनि",
        "angha" to "आंहा",
        "angkhwo" to "आंखौ",
        "angkhw" to "आंखौ",
        "ma" to "मा",
        "khobor" to "खबर",
        "thang" to "थां",
        "thangdwng" to "थांदों",
        "thangnw" to "थांनो",
        "khalam" to "खालाम",
        "khalamdwng" to "खालामदों",
        "khalamnw" to "खालामनो",
        "swr" to "सोर",
        "swrni" to "सोरनि",
        "boha" to "बहा",
        "baha" to "बहा",
        "bobe" to "बबे",
        "bobo" to "बबे",
        "fai" to "फै",
        "phai" to "फै",
        "faidwng" to "फैदों",
        "phaidwng" to "फैदों",
        "onkham" to "ओंखाम",
        "wngkham" to "ओंखाम",
        "mung" to "मुं",
        "munga" to "मुंआ",
        "bodo" to "बर'",
        "borw" to "बर'",
        "harini" to "हारिनि",
        "saorai" to "सावराय",
        "saorainw" to "सावरायनो",
        "saoraidw" to "सावरायदो",
        "raylai" to "रायलाय",
        "raylainw" to "रायलायनो",
        "gwdan" to "गोदान",
        "gjam" to "गोजाम",
        "nama" to "नामा",
        "nagirdwng" to "नागिरदों",
        "salte" to "साल्थे",
        "ada" to "आदा",
        "abo" to "आब'",
        "aie" to "आइ",
        "afw" to "आफा",
        "aphw" to "आफा",
        "fwrwng" to "फोरों",
        "phwrwng" to "फोरों",
        "solong" to "सोलों",
        "mithi" to "मिथि",
        "mithiyw" to "मिथियो",
        "mithigo" to "मिथियो",
        "sanse" to "सानसे",
        "boraibai" to "बरायबाय",
        "bwraibai" to "बरायबाय"
    )

    // English loanwords transliterated into phonetic Devanagari so Indic TTS models pronounce them naturally
    private val ENGLISH_LOANWORDS = linkedMapOf(
        "spanish" to "स्पेनिस",
        "english" to "इंग्राजि",
        "phone" to "फोन",
        "mobile" to "मबाइल",
        "ai" to "एआइ",
        "orki" to "अरकि",
        "app" to "एप",
        "application" to "एप्लिकेसन",
        "chat" to "सेट",
        "message" to "मेसेज",
        "call" to "कल",
        "google" to "गुगोल",
        "youtube" to "इउथुब",
        "whatsapp" to "ह्वाट्सएप",
        "video" to "भिडिअ'",
        "photo" to "फथ'",
        "image" to "इमेज",
        "camera" to "केमेरा",
        "computer" to "कमपिउतार",
        "internet" to "इन्तारनेथ",
        "online" to "अनलाइन",
        "status" to "स्तेतास",
        "settings" to "सेतिं",
        "audio" to "अडिअ'",
        "music" to "म्युजिक",
        "voice" to "भयस",
        "friend" to "लोगो",
        "hello" to "हेल'",
        "hi" to "हाइ",
        "hey" to "हे",
        "ok" to "अके",
        "okay" to "अके",
        "sorry" to "सरि",
        "thanks" to "साबायखर",
        "thank you" to "साबायखर",
        "yes" to "नोंगो",
        "no" to "नोंङा",
        "battery" to "बेतारि",
        "link" to "लिंक",
        "help" to "हेलप",
        "problem" to "समस्या",
        "question" to "सोंनाय"
    )

    // Bodo numerals in speech
    private val BODO_NUMBERS = mapOf(
        "0" to "लथ'",
        "1" to "से",
        "2" to "नै",
        "3" to "थाम",
        "4" to "ब्रै",
        "5" to "बा",
        "6" to "द'",
        "7" to "स्नि",
        "8" to "दान्थि",
        "9" to "गु",
        "10" to "जि"
    )

    /**
     * Prepares raw LLM output or user text for clean, natural speech synthesis.
     */
    fun normalizeForTts(rawText: String): String {
        if (rawText.isBlank()) return ""

        // 1. Strip markdown syntax
        var text = MARKDOWN_REGEX.replace(rawText, " ")

        // 2. Strip emojis and special Unicode pictographs
        text = EMOJI_REGEX.matcher(text).replaceAll("")

        // 3. Normalize multiple whitespace and dashes
        text = text.replace(Regex("-{2,}"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // 4. Replace single numbers (0-10) with Bodo speech words
        for ((digit, word) in BODO_NUMBERS) {
            text = text.replace(Regex("\\b$digit\\b"), word)
        }

        // 5. Replace English loanwords with Devanagari phonetics
        for ((en, deva) in ENGLISH_LOANWORDS) {
            val pattern = Regex("(?i)\\b$en\\b")
            text = text.replace(pattern, deva)
        }

        // 6. Convert each Roman token independently. Devanagari elsewhere (including
        // a loanword replaced above) must not skip the remaining Roman Bodo words.
        // This is speech-only processing: never apply it to chat/image user input.
        val transliterated = Regex("[\\p{L}\\p{M}']+").replace(text) { match ->
            val word = match.value
            if (word.any { it in '\u0900'..'\u097F' }) word else {
                val key = word.lowercase(Locale.ROOT)
                ROMAN_BODO_DICTIONARY[key]
                    ?: BodoLexicon.lookup(key)?.devanagari?.firstOrNull()
                    ?: convertWord(key)
            }
        }

        return formatPunctuationPauses(cleanDevanagariArtifacts(transliterated))
    }

    private fun convertWord(w: String): String {
        var str = w

        // Specific Bodo digraphs
        str = str.replace("kh", "ख")
            .replace("ph", "फ")
            .replace("th", "थ")
            .replace("ch", "छ")
            .replace("sh", "श")
            .replace("ng", "ं")
            .replace("wi", "ुइ")
            .replace("ai", "ै")
            .replace("ao", "ौ")
            .replace("au", "ौ")
            .replace("jw", "जो")
            .replace("nw", "नो")
            .replace("bw", "बो")
            .replace("dw", "दो")
            .replace("gw", "गो")
            .replace("sw", "सो")
            .replace("fw", "फो")

        // Consonants
        val consonants = mapOf(
            "b" to "ब", "d" to "द", "g" to "ग", "h" to "ह",
            "j" to "ज", "k" to "क", "l" to "ल", "m" to "म",
            "n" to "न", "p" to "प", "r" to "र", "s" to "स",
            "t" to "त", "y" to "य", "z" to "ज", "v" to "भ",
            "w" to "व"
        )
        for ((c, dev) in consonants) {
            str = str.replace(c, dev)
        }

        // Standalone or dependent vowels
        str = str.replace(Regex("^a"), "आ")
            .replace(Regex("^i"), "इ")
            .replace(Regex("^u"), "उ")
            .replace(Regex("^e"), "ए")
            .replace(Regex("^o"), "ओ")
            .replace("a", "ा")
            .replace("i", "ि")
            .replace("u", "ु")
            .replace("e", "े")
            .replace("o", "ो")

        return str
    }

    /**
     * Fix dangling matras (e.g. standalone ' ा' without consonant) that distort neural vocoders.
     */
    private fun cleanDevanagariArtifacts(text: String): String {
        return text
            // Java \b treats these combining marks as word boundaries on some JVMs.
            // It changed valid सैमा into सऐमा. Only repair a genuinely orphaned matra:
            // it must not follow any Unicode letter OR combining mark.
            .replace(Regex("(?<![\\p{L}\\p{M}])ा"), "आ")
            .replace(Regex("(?<![\\p{L}\\p{M}])ि"), "इ")
            .replace(Regex("(?<![\\p{L}\\p{M}])ु"), "उ")
            .replace(Regex("(?<![\\p{L}\\p{M}])े"), "ए")
            .replace(Regex("(?<![\\p{L}\\p{M}])ो"), "ओ")
            .replace(Regex("(?<![\\p{L}\\p{M}])ौ"), "औ")
            .replace(Regex("(?<![\\p{L}\\p{M}])ै"), "ऐ")

    }

    /**
     * Normalize punctuation for natural acoustic pauses and pitch contours.
     */
    private fun formatPunctuationPauses(text: String): String {
        var res = text
            .replace(Regex("[?!.]+"), ".")
            .replace(".", "। ")
            .replace(",", ", ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (res.isNotEmpty() && !res.endsWith("।") && !res.endsWith("?")) {
            res += "।"
        }
        return res
    }
}
