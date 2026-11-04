package com.example.spendflow.core.time

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** A calendar month in [zone], expressed as an inclusive start and exclusive end. */
internal class MonthBounds(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()) {
    val startInclusive: Instant = month.atDay(1).atStartOfDay(zone).toInstant()
    val endExclusive: Instant = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()

    operator fun contains(instant: Instant): Boolean =
        instant >= startInclusive && instant < endExclusive
}
