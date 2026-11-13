package com.dontstudyy.carbeat.game

import org.json.JSONObject

/** 레인 4개. 순서가 JSON의 lane 번호(0~3)와 같다. */
enum class Lane {
    STEER_LEFT, BRAKE, STEER_RIGHT, WIPER;

    val isSteer: Boolean get() = this == STEER_LEFT || this == STEER_RIGHT
}

/** [t]는 곡 시작부터의 초, [holdSec]가 null이 아니면 롱노트(초). */
data class Note(val t: Double, val lane: Lane, val holdSec: Double? = null) {
    val isHold: Boolean get() = holdSec != null
    val endSec: Double get() = t + (holdSec ?: 0.0)
}

data class Song(
    val id: String,
    val title: String,
    val audio: String,
    val bpm: Double,
    val durationSec: Double,
    val notes: List<Note>,
) {
    companion object {
        /** assets의 songs 폴더에 있는 곡 JSON 형식(설계서 6장)을 읽는다. 노트는 시각순으로 정렬한다. */
        fun parse(json: String): Song {
            val o = JSONObject(json)
            val arr = o.getJSONArray("notes")
            val notes = (0 until arr.length()).map { i ->
                val n = arr.getJSONObject(i)
                val lane = n.getInt("lane")
                require(lane in 0..3) { "lane 범위 밖: $lane (노트 #$i)" }
                Note(
                    t = n.getDouble("t"),
                    lane = Lane.entries[lane],
                    holdSec = if (n.has("hold")) n.getDouble("hold") else null,
                )
            }.sortedBy { it.t }
            return Song(
                id = o.getString("id"),
                title = o.getString("title"),
                audio = o.getString("audio"),
                bpm = o.getDouble("bpm"),
                durationSec = o.getDouble("durationSec"),
                notes = notes,
            )
        }
    }
}
