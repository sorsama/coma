package com.zyphite.coma.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos

/**
 * What Hermes is doing, said in motion by the three-bar wing.
 *
 * Idle is still. Think folds the bars in sequence like a wingbeat. Run is a hard step chase: a tool
 * or command is executing. Wait is a slow amber breath: Hermes is blocked on the user. Fork splits
 * the bars apart: work went to subagents. Learn levels the bars into a block: a skill or memory was
 * kept. Tick lights one bar at a time: counting to the next run. Off is grey and still: the host
 * cannot be reached.
 */
enum class Signal { Idle, Think, Run, Wait, Fork, Learn, Tick, Off }

private val Heights = floatArrayOf(1f, .68f, .38f)
private val FlapEase = CubicBezierEasing(.4f, 0f, .2f, 1f)
private val LearnEase = CubicBezierEasing(.3f, 1.4f, .5f, 1f)

/** tan(-16 degrees): the wing leans forward. */
const val WingSkew = -0.2867f

/** A matrix that skews around the vertical middle of a box [height] tall. */
fun skewMatrix(height: Float): Matrix = Matrix().apply {
    values[Matrix.SkewX] = WingSkew
    values[Matrix.TranslateX] = -WingSkew * height / 2
}

/** [em] is the wing's height, the design's font-size. */
@Composable
fun Wing(signal: Signal, em: Dp, modifier: Modifier = Modifier, color: Color = C.Text, label: String? = null) {
    val clock = LocalClock.current
    val still = LocalReduceMotion.current
    val tint = when (signal) {
        Signal.Wait -> C.Amber
        Signal.Off -> C.Dim
        else -> color
    }
    val described = if (label != null) Modifier.semantics { contentDescription = label } else Modifier
    Canvas(modifier.size(em * .95f, em).then(described)) {
        val s = size.height
        val bar = s * .19f
        val t = clock.floatValue
        withTransform({ transform(skewMatrix(s)) }) {
            for (i in 0..2) {
                var scale = 1f
                var alpha = 1f
                var shift = 0f
                if (signal == Signal.Off && i > 0) alpha = .3f
                if (!still) when (signal) {
                    Signal.Think -> {
                        val p = frac((t - i * .14f) / 1.2f)
                        scale = if (p < .45f) mix(1f, .3f, FlapEase.transform(p / .45f))
                        else mix(.3f, 1f, FlapEase.transform((p - .45f) / .55f))
                    }
                    Signal.Run -> alpha = if (frac((t - i * .22f) / .66f) < .335f) 1f else .25f
                    Signal.Wait -> alpha = .7f + .3f * cos(t / 1.8f * 2f * PI.toFloat())
                    Signal.Fork -> {
                        val p = frac(t / 1.8f)
                        val k = when {
                            p < .2f -> 0f
                            p < .45f -> SpringEase.transform((p - .2f) / .25f)
                            p < .8f -> 1f
                            else -> 1f - SpringEase.transform((p - .8f) / .2f)
                        }
                        shift = (i - 1) * .3f * s * k
                    }
                    Signal.Learn -> {
                        val p = frac(t / 2.6f)
                        val k = when {
                            p < .3f -> 0f
                            p < .5f -> LearnEase.transform((p - .3f) / .2f)
                            p < .85f -> 1f
                            else -> 1f - LearnEase.transform((p - .85f) / .15f)
                        }
                        scale = 1f + (1f / Heights[i] - 1f) * k
                    }
                    Signal.Tick -> alpha = if (frac((t - (2 - i)) / 3f) in .04f..(.3f)) 1f else .25f
                    Signal.Idle, Signal.Off -> Unit
                }
                val h = s * Heights[i] * scale
                drawRoundRect(tint, Offset(i * bar * 2 + shift, s - h), Size(bar, h), CornerRadius(s * .06f), alpha = alpha)
            }
        }
    }
}
