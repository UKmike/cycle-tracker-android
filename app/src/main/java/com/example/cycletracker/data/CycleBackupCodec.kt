package com.example.cycletracker.data

import com.example.cycletracker.domain.CycleCalculator
import java.time.LocalDate

data class CycleBackup(
    val periodStarts: List<LocalDate>,
    val periodLength: Int = CycleCalculator.DEFAULT_PERIOD_LENGTH
)

/**
 * Lightweight, zero-dependency JSON codec for offline backup export and import.
 */
object CycleBackupCodec {

    private const val FORMAT_VERSION = 1

    fun encode(
        periodStarts: List<LocalDate>,
        periodLength: Int = CycleCalculator.DEFAULT_PERIOD_LENGTH,
        exportedAt: LocalDate = LocalDate.now()
    ): String {
        val sorted = periodStarts.distinct().sorted()
        val clampedLen = periodLength.coerceIn(
            CycleCalculator.MIN_PERIOD_LENGTH,
            CycleCalculator.MAX_PERIOD_LENGTH
        )
        return buildString {
            appendLine("{")
            appendLine("  \"version\": $FORMAT_VERSION,")
            appendLine("  \"exportedAt\": \"$exportedAt\",")
            appendLine("  \"periodLength\": $clampedLen,")
            if (sorted.isEmpty()) {
                appendLine("  \"periodStarts\": []")
            } else {
                appendLine("  \"periodStarts\": [")
                sorted.forEachIndexed { idx, date ->
                    val comma = if (idx < sorted.lastIndex) "," else ""
                    appendLine("    \"$date\"$comma")
                }
                appendLine("  ]")
            }
            append("}")
        }
    }

    fun decode(json: String): Result<CycleBackup> = runCatching {
        val trimmed = json.trim()
        require(trimmed.startsWith("{") && trimmed.endsWith("}")) {
            "Backup file must be a JSON object."
        }

        val startsRegex = Regex(""""periodStarts"\s*:\s*\[([^\]]*)]""")
        val startsMatch = startsRegex.find(trimmed)
            ?: throw IllegalArgumentException("Missing \"periodStarts\" array in backup file.")

        val arrayBody = startsMatch.groupValues[1].trim()
        val dates = if (arrayBody.isEmpty()) {
            emptyList()
        } else {
            val itemRegex = Regex(""""([^"]+)"""")
            val tokens = arrayBody.split(",").map { it.trim() }
            tokens.map { token ->
                val match = itemRegex.matchEntire(token)
                    ?: throw IllegalArgumentException("Invalid date entry: $token")
                LocalDate.parse(match.groupValues[1])
            }.distinct().sorted()
        }

        val lengthRegex = Regex(""""periodLength"\s*:\s*(\d+)""")
        val periodLength = lengthRegex.find(trimmed)
            ?.groupValues?.get(1)?.toIntOrNull()
            ?.coerceIn(CycleCalculator.MIN_PERIOD_LENGTH, CycleCalculator.MAX_PERIOD_LENGTH)
            ?: CycleCalculator.DEFAULT_PERIOD_LENGTH

        CycleBackup(periodStarts = dates, periodLength = periodLength)
    }
}
