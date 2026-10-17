package com.guitarmagic.tuner.pitch

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 二阶 IIR 滤波器（RBJ Audio EQ Cookbook）。用于在音高检测前去掉
 * 低频杂音（手碰手机、风噪、桌面振动）和高频嘶声，提高小音量时的信噪比。
 * 滤波器只改变各频率成分的幅度和相位，不改变信号的周期，因此不影响音高精度。
 */
class Biquad private constructor(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    /** 转置直接 II 型，逐个采样处理。 */
    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    companion object {
        private const val BUTTERWORTH_Q = 0.7071067811865476

        fun highPass(sampleRate: Int, cutoff: Double, q: Double = BUTTERWORTH_Q): Biquad {
            val w0 = 2 * PI * cutoff / sampleRate
            val alpha = sin(w0) / (2 * q)
            val c = cos(w0)
            val a0 = 1 + alpha
            return Biquad(
                b0 = (1 + c) / 2 / a0,
                b1 = -(1 + c) / a0,
                b2 = (1 + c) / 2 / a0,
                a1 = -2 * c / a0,
                a2 = (1 - alpha) / a0,
            )
        }

        fun lowPass(sampleRate: Int, cutoff: Double, q: Double = BUTTERWORTH_Q): Biquad {
            val w0 = 2 * PI * cutoff / sampleRate
            val alpha = sin(w0) / (2 * q)
            val c = cos(w0)
            val a0 = 1 + alpha
            return Biquad(
                b0 = (1 - c) / 2 / a0,
                b1 = (1 - c) / a0,
                b2 = (1 - c) / 2 / a0,
                a1 = -2 * c / a0,
                a2 = (1 - alpha) / a0,
            )
        }
    }
}
