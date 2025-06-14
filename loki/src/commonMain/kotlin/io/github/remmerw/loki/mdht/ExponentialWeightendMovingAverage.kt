package io.github.remmerw.loki.mdht

internal class ExponentialWeightendMovingAverage {
    private var weight = 0.3
    var average: Double = Double.NaN
        private set

    fun updateAverage(value: Double) {
        average = if (average.isNaN()) value
        else value * weight + average * (1.0 - weight)
    }

    fun setWeight(weight: Double): ExponentialWeightendMovingAverage {
        this.weight = weight
        return this
    }

    fun getAverage(defaultValue: Double): Double {
        return if (average.isNaN()) defaultValue else average
    }

}
