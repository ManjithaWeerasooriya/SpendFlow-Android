package com.example.spendflow.domain.model

/** Choices from Settings, stored on the user document. Turning [streakEnabled] off hides streak UI but keeps all data. */
data class UserPreferences(
    val streakEnabled: Boolean = false,
    val lockScreenQuickEntry: Boolean = false,
    val capThresholds: CapThresholds = CapThresholds.DEFAULT,
)

/** Percentages of a category cap at which spending is flagged, strictly ascending. */
data class CapThresholds(val notice: Int, val warning: Int, val over: Int) {
    init {
        require(notice in RANGE && warning in RANGE && over in RANGE) { "Cap thresholds must be in $RANGE" }
        require(notice < warning && warning < over) { "Cap thresholds must be ascending" }
    }

    fun asList(): List<Int> = listOf(notice, warning, over)

    /** The highest threshold that [spentMinor] has reached against [limitMinor]. */
    fun levelFor(spentMinor: Long, limitMinor: Long): CapLevel {
        require(limitMinor > 0) { "Cap limit must be positive" }
        // Amounts are capped at 1e14, so * 100 can't overflow
        fun reached(percent: Int) = spentMinor * 100 >= percent * limitMinor
        return when {
            reached(over) -> CapLevel.OVER
            reached(warning) -> CapLevel.WARNING
            reached(notice) -> CapLevel.NOTICE
            else -> CapLevel.UNDER
        }
    }

    companion object {
        val RANGE = 1..100
        val DEFAULT = CapThresholds(75, 90, 100)

        fun fromList(values: List<Int>): CapThresholds {
            require(values.size == 3) { "Expected three cap thresholds" }
            return CapThresholds(values[0], values[1], values[2])
        }
    }
}

/** How far spending has reached into a cap, by [CapThresholds]. Declared in ascending order. */
enum class CapLevel {
    UNDER, NOTICE, WARNING, OVER;

    companion object {
        /** The levels a cap can alert at; every one is on for a new cap. */
        val ALERTS: Set<CapLevel> = setOf(NOTICE, WARNING, OVER)
    }
}
