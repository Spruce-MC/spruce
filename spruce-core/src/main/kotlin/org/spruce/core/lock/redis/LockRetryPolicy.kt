package org.spruce.core.lock.redis

import java.time.Duration
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

data class LockRetryPolicy(
    val initialDelay: Duration = Duration.ofMillis(10),
    val maxDelay: Duration = Duration.ofMillis(250),
    val multiplier: Double = 2.0,
    val jitter: Double = 0.25
) {

    fun nextDelay(attempt: Int): Duration {
        val exponential = initialDelay.toMillis() * multiplier.pow(attempt.toDouble())
        val capped = min(exponential.toLong(), maxDelay.toMillis())

        if (jitter <= 0.0) {
            return Duration.ofMillis(capped)
        }

        val delta = (capped * jitter).toLong()
        val minValue = (capped - delta).coerceAtLeast(0)
        val maxValue = capped + delta

        return Duration.ofMillis(Random.nextLong(minValue, maxValue + 1))
    }
}