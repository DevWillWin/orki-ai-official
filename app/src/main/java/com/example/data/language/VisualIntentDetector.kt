package com.example.data.language

/** Conservative local routing only; this is not a Bodo sentence translator. */
object VisualIntentDetector {
    data class VisualIntent(val type: String, val prompt: String)

    fun extract(rawText: String): VisualIntent? {
        val t = rawText.trim()
        if (t.isEmpty()) return null
        val lower = t.lowercase(java.util.Locale.ROOT)

        // 1. Explicit Slash Commands
        val slashImageRegex = Regex("^/(image|imagine|draw|img)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val slashImageMatch = slashImageRegex.find(t)
        if (slashImageMatch != null) {
            return VisualIntent("image", slashImageMatch.groupValues[2].trim())
        }

        // 2. Natural Image Request Intents (Handles 'create an image of', 'draw me a', 'picture of', etc.)
        val imagePattern = Regex(
            "^(?:hey\\s+orki\\s*,?\\s*|can\\s+you\\s+(?:please\\s+)?|could\\s+you\\s+(?:please\\s+)?|please\\s+|i\\s+want\\s+(?:you\\s+to\\s+|a\\s+)?|make\\s+me\\s+a\\s+|give\\s+me\\s+a\\s+|show\\s+me\\s+a\\s+)?(?:generate|create|ceeate|make|render|produce|draw|paint|illustrate|show|give)?\\s*(?:an?\\s+)?(?:ai\\s+)?(?:image|inage|picture|piture|photo|artwork|drawing|illustration|painting|wallpaper)\\s*(?:of|about|showing|with|for|depicting)?\\s*(.+)$",
            RegexOption.IGNORE_CASE
        )
        val imageMatch = imagePattern.find(t)
        if (imageMatch != null && imageMatch.groupValues[1].isNotBlank()) {
            val candidate = imageMatch.groupValues[1].trim()
            if (candidate.length >= 2) {
                return VisualIntent("image", candidate)
            }
        }

        // 3. Drawing Commands (e.g. 'draw a cat', 'draw me a girl in dokhona')
        val drawPattern = Regex(
            "^(?:can\\s+you\\s+(?:please\\s+)?|could\\s+you\\s+(?:please\\s+)?|please\\s+)?(?:draw|paint|illustrate)\\s*(?:me\\s+)?(?:an?\\s+)?(.+)$",
            RegexOption.IGNORE_CASE
        )
        val drawMatch = drawPattern.find(t)
        if (drawMatch != null && drawMatch.groupValues[1].isNotBlank()) {
            val candidate = drawMatch.groupValues[1].trim()
            if (candidate.length >= 2) {
                return VisualIntent("image", candidate)
            }
        }

        // 4. Bodo Language Triggers
        if (lower.contains("छबि बानाय") || lower.contains("बानाय छबि")) {
            // Preserve the entire sentence. Global deletion previously removed these
            // words even from subjects, quoted text, and negative constraints.
            return VisualIntent("image", t)
        }

        return null
    }

}
