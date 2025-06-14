package io.github.remmerw.loki.mdht

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

internal class ResponseTimeoutFilter {
    private val bins = FloatArray(NUM_BINS)

    @OptIn(ExperimentalAtomicApi::class)
    private val updateCount = AtomicLong(0L)

    init {
        reset()
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun reset() {
        updateCount.store(0)
        bins.fill(1.0f / bins.size)
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun updateAndRecalc(newRTT: Long) {
        update(newRTT)
        if ((updateCount.incrementAndFetch() and 0x0fL) == 0L) {
            decay()
        }
    }

    private fun update(newRTT: Long) {
        var bin = (newRTT - MIN_BIN).toInt() / BIN_SIZE
        bin = max(min(bin.toDouble(), (bins.size - 1).toDouble()), 0.0).toInt()

        bins[bin] += 1.0f
    }

    private fun decay() {
        for (i in bins.indices) {
            bins[i] *= 0.95f
        }
    }


}

private const val MIN_BIN = 0
private const val MAX_BIN = RPC_CALL_TIMEOUT_MAX
private const val BIN_SIZE = 50
private val NUM_BINS = ceil(((MAX_BIN - MIN_BIN) * 1.0f / BIN_SIZE).toDouble()).toInt()