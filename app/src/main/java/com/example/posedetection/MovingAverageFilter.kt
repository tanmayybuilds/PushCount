package com.example.posedetection

import java.util.ArrayDeque

/**
 * Applies a simple moving-average window to smooth frame-to-frame noise in landmark angles.
 */
class MovingAverageFilter(private val windowSize: Int = 5) {
    private val buffer = ArrayDeque<Float>(windowSize)

    fun add(value: Float): Float {
        if (buffer.size >= windowSize) {
            buffer.removeFirst()
        }
        buffer.addLast(value)
        return buffer.average().toFloat()
    }

    fun currentAverage(): Float {
        return if (buffer.isEmpty()) 0f else buffer.average().toFloat()
    }

    fun reset() {
        buffer.clear()
    }
}
