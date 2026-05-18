package com.vaultguard.framework.heuristics

import kotlin.math.log2

/**
 * Shannon entropy calculator for detecting high-entropy strings that may be
 * cryptographic keys, API tokens, JWTs, or other secret material.
 *
 * Shannon entropy H(X) = -Σ p(x) * log₂(p(x)) for each unique character x.
 *
 * Entropy ranges for typical string types:
 *   - English text:           ~3.5 - 4.0 bits/char
 *   - Structured data (JSON): ~4.0 - 4.5 bits/char
 *   - Encoded secrets:        ~4.5 - 5.5 bits/char  (suspicious)
 *   - Random hex/base64:      ~5.5 - 6.0 bits/char  (likely secret)
 *   - Cryptographic keys:     ~6.0+ bits/char        (almost certainly secret)
 *
 * Thresholds used:
 *   ≥ 4.5  → SUSPICIOUS (flag for review)
 *   ≥ 5.5  → HIGH_ENTROPY (likely a secret or token)
 */
object EntropyAnalyzer {

    const val THRESHOLD_SUSPICIOUS   = 4.5
    const val THRESHOLD_HIGH_ENTROPY = 5.5

    enum class EntropyClassification {
        NORMAL,         // < 4.5 — typical structured text
        SUSPICIOUS,     // 4.5..5.5 — could be encoded data
        HIGH_ENTROPY    // > 5.5 — likely a key, token, or secret
    }

    /**
     * Calculates Shannon entropy of the given string.
     * Returns 0.0 for empty or single-character strings.
     */
    fun calculateEntropy(input: String): Double {
        if (input.length <= 1) return 0.0

        val frequencyMap = mutableMapOf<Char, Int>()
        for (c in input) {
            frequencyMap[c] = (frequencyMap[c] ?: 0) + 1
        }

        val length = input.length.toDouble()
        var entropy = 0.0

        for ((_, count) in frequencyMap) {
            val probability = count.toDouble() / length
            if (probability > 0) {
                entropy -= probability * log2(probability)
            }
        }

        return entropy
    }

    /**
     * Classifies a string based on its Shannon entropy.
     */
    fun classify(input: String): EntropyClassification {
        if (input.length < 8) return EntropyClassification.NORMAL // Too short to meaningfully classify

        val entropy = calculateEntropy(input)
        return when {
            entropy >= THRESHOLD_HIGH_ENTROPY -> EntropyClassification.HIGH_ENTROPY
            entropy >= THRESHOLD_SUSPICIOUS   -> EntropyClassification.SUSPICIOUS
            else                              -> EntropyClassification.NORMAL
        }
    }

    /**
     * Returns a human-readable report string for a value's entropy.
     */
    fun report(value: String): String {
        val entropy = calculateEntropy(value)
        val classification = classify(value)
        return "Entropy: %.2f bits/char [%s] (len=%d)".format(entropy, classification.name, value.length)
    }
}
