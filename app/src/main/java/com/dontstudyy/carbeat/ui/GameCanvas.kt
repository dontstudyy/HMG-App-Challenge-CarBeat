package com.dontstudyy.carbeat.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dontstudyy.carbeat.game.Effect
import com.dontstudyy.carbeat.game.Lane
import com.dontstudyy.carbeat.game.NoteStatus
import com.dontstudyy.carbeat.game.PlaySession
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 노트가 화면 맨 위에서 판정선까지 내려오는 데 걸리는 시간(초) */
private const val FALL_SEC = 1.6f
private val MISS_GRAY = Color(0xFF6B7280)
private val KEY_LABELS = listOf("←", "↓", "→", "↑")

private fun laneColor(l: Lane) = LaneColors.all[l.ordinal]

/** 레인 4개, 노트, 판정선, 터짐/회색 낙하/흔들림, 콤보 숫자를 그린다. [t]는 곡 시각(초). */
fun DrawScope.drawGame(s: PlaySession, t: Double, nowNanos: Long, tm: TextMeasurer) {
    val w = size.width
    val h = size.height

    // MISS 흔들림
    var shakeX = 0f
    s.effects.lastOrNull { it.kind == Effect.Kind.MISS || it.kind == Effect.Kind.HOLD_BROKEN }?.let {
        val age = (nowNanos - it.bornNanos) / 1e9f
        if (age in 0f..0.25f) shakeX = sin(age * 90f) * 14f * (1f - age / 0.25f)
    }

    translate(left = shakeX) {
        val trackLeft = w * 0.04f
        val laneW = (w * 0.92f) / 4f
        val lineY = h * 0.80f
        val topY = 0f

        fun laneX(i: Int) = trackLeft + laneW * i
        fun yOf(time: Double) = lineY - ((time - t).toFloat() / FALL_SEC) * (lineY - topY)

        // 레인 바탕
        for (l in Lane.entries) {
            val i = l.ordinal
            val on = s.pressed[i]
            drawRect(laneColor(l).copy(alpha = if (on) 0.22f else 0.07f), Offset(laneX(i), 0f), Size(laneW, h))
            drawRect(Color.White.copy(alpha = 0.08f), Offset(laneX(i), 0f), Size(2f, h))
        }
        drawRect(Color.White.copy(alpha = 0.08f), Offset(laneX(4), 0f), Size(2f, h))

        // 노트
        val notes = s.engine.notes
        for (idx in notes.indices) {
            val n = notes[idx]
            val status = s.engine.status(idx)
            if (status == NoteStatus.HIT) continue
            val lane = n.lane
            val cx = laneX(lane.ordinal) + laneW / 2f
            val headY = if (status == NoteStatus.HOLDING) lineY else yOf(n.t)
            val tailY = yOf(n.endSec)
            if (headY < -60f || tailY > h + 60f) continue
            val base = if (status == NoteStatus.MISSED) MISS_GRAY else laneColor(lane)
            val alpha = if (status == NoteStatus.MISSED) 0.55f else 1f
            if (n.isHold) {
                val bodyW = laneW * 0.46f
                val top = min(tailY, headY)
                drawRoundRect(
                    base.copy(alpha = alpha * (if (status == NoteStatus.HOLDING) 0.85f else 0.5f)),
                    Offset(cx - bodyW / 2f, top), Size(bodyW, max(0f, headY - top)), CornerRadius(16f),
                )
            }
            val nw = laneW * 0.82f
            val nh = 40f
            drawRoundRect(base.copy(alpha = alpha), Offset(cx - nw / 2f, headY - nh / 2f), Size(nw, nh), CornerRadius(18f))
            if (status != NoteStatus.MISSED) {
                drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(cx - nw / 2f + 6f, headY - nh / 2f + 5f), Size(nw - 12f, 8f), CornerRadius(4f))
            }
        }

        // 판정선
        drawRect(Color.White.copy(alpha = 0.9f), Offset(trackLeft, lineY - 2f), Size(laneW * 4f, 4f))

        // 키 안내
        for (l in Lane.entries) {
            val on = s.pressed[l.ordinal]
            val r = tm.measure(KEY_LABELS[l.ordinal], TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else laneColor(l)))
            drawText(r, topLeft = Offset(laneX(l.ordinal) + (laneW - r.size.width) / 2f, lineY + 28f))
        }

        // 효과
        for (e in s.effects) {
            val age = (nowNanos - e.bornNanos) / 1e9f
            val cx = laneX(e.lane.ordinal) + laneW / 2f
            when (e.kind) {
                Effect.Kind.PERFECT, Effect.Kind.GOOD, Effect.Kind.HOLD_OK -> {
                    if (age > 0.45f) continue
                    val p = age / 0.45f
                    val col = laneColor(e.lane)
                    val a = (1f - p)
                    drawCircle(col.copy(alpha = a * 0.8f), radius = 30f + p * 120f, center = Offset(cx, lineY), style = Stroke(width = 8f * (1f - p) + 1f))
                    val shards = if (e.kind == Effect.Kind.GOOD) 6 else 10
                    for (k in 0 until shards) {
                        val ang = k * (6.2832f / shards)
                        val d = 20f + p * 150f
                        drawCircle(col.copy(alpha = a), radius = 9f * (1f - p) + 2f, center = Offset(cx + cos(ang) * d, lineY + sin(ang) * d))
                    }
                    if (e.kind != Effect.Kind.HOLD_OK) {
                        val label = if (e.kind == Effect.Kind.PERFECT) "PERFECT" else "GOOD"
                        val r = tm.measure(label, TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = col.copy(alpha = a)))
                        drawText(r, topLeft = Offset(cx - r.size.width / 2f, lineY - 90f - p * 30f))
                    }
                }
                Effect.Kind.MISS, Effect.Kind.HOLD_BROKEN -> {
                    if (age > 0.6f) continue
                    val a = 1f - age / 0.6f
                    val label = if (e.kind == Effect.Kind.MISS) "MISS" else "BREAK"
                    val r = tm.measure(label, TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MISS_GRAY.copy(alpha = a)))
                    drawText(r, topLeft = Offset(cx - r.size.width / 2f, lineY - 90f + age * 50f))
                }
            }
        }

        // 콤보
        val combo = s.engine.combo
        val centerX = trackLeft + laneW * 2f
        if (combo >= 2) {
            val age = (nowNanos - s.lastComboChangeNanos) / 1e9f
            val scale = 1f + 0.35f * (1f - min(age / 0.25f, 1f))
            val r = tm.measure(combo.toString(), TextStyle(fontSize = (64 * scale).sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.92f)))
            drawText(r, topLeft = Offset(centerX - r.size.width / 2f, h * 0.30f - r.size.height / 2f))
            val c = tm.measure("COMBO", TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f)))
            drawText(c, topLeft = Offset(centerX - c.size.width / 2f, h * 0.30f + 44f))
        } else if (s.lastBrokenCombo >= 5) {
            // 콤보가 끊기면 숫자가 갈라지며 사라진다
            val age = (nowNanos - s.lastBreakNanos) / 1e9f
            if (age in 0f..0.5f) {
                val p = age / 0.5f
                val str = s.lastBrokenCombo.toString()
                val style = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = MISS_GRAY.copy(alpha = 1f - p))
                val r = tm.measure(str, style)
                translate(left = -p * 40f, top = p * 30f) {
                    drawText(r, topLeft = Offset(centerX - r.size.width / 2f - 6f, h * 0.30f - r.size.height / 2f))
                }
                translate(left = p * 40f, top = p * 60f) {
                    drawText(r, topLeft = Offset(centerX - r.size.width / 2f + 6f, h * 0.30f - r.size.height / 2f))
                }
            }
        }
    }
}
