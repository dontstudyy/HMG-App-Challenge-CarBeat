package com.dontstudyy.carbeat.game

import android.content.Context

/** assets/songs의 곡 3개를 읽는다. 순서는 홈 화면 목록 순서와 같다. */
object SongRepository {
    val ids = listOf("slow", "brake", "festival")

    fun loadAll(ctx: Context): List<Song> = ids.map { id ->
        ctx.assets.open("songs/$id.json").bufferedReader(Charsets.UTF_8).use { Song.parse(it.readText()) }
    }
}
