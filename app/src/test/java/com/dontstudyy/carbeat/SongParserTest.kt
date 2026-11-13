package com.dontstudyy.carbeat

import com.dontstudyy.carbeat.game.Lane
import com.dontstudyy.carbeat.game.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SongParserTest {
    private fun load(id: String) = Song.parse(File("src/main/assets/songs/$id.json").readText(Charsets.UTF_8))

    @Test
    fun parsesInlineJson() {
        val s = Song.parse(
            """{"id":"x","title":"테스트","audio":"x.ogg","bpm":120,"durationSec":10,
               "lanes":["STEER_LEFT","BRAKE","STEER_RIGHT","WIPER"],
               "notes":[{"t":2.0,"lane":1,"hold":1.5},{"t":1.0,"lane":0}]}""",
        )
        assertEquals("테스트", s.title)
        assertEquals(2, s.notes.size)
        // 시각순 정렬
        assertEquals(1.0, s.notes[0].t, 0.0)
        assertEquals(Lane.STEER_LEFT, s.notes[0].lane)
        assertNull(s.notes[0].holdSec)
        assertEquals(1.5, s.notes[1].holdSec!!, 0.0)
        assertEquals(3.5, s.notes[1].endSec, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBadLane() {
        Song.parse("""{"id":"x","title":"t","audio":"a","bpm":100,"durationSec":1,"notes":[{"t":1,"lane":4}]}""")
    }

    @Test
    fun realSongsMatchDesign() {
        val expected = mapOf("slow" to 86, "brake" to 86, "festival" to 89)
        for ((id, count) in expected) {
            val s = load(id)
            assertEquals(id, s.id)
            assertEquals("$id 노트 수", count, s.notes.size)
            assertEquals("$id 오디오", "$id.ogg", s.audio)
            assertTrue("$id 에 롱노트 있음", s.notes.any { it.isHold })
            assertTrue("$id 노트가 곡 길이 안", s.notes.all { it.endSec <= s.durationSec })
            assertTrue(File("src/main/assets/songs/${s.audio}").exists())
        }
        assertEquals(100.0, load("slow").bpm, 0.0)
        assertEquals(128.0, load("brake").bpm, 0.0)
        assertEquals(38.4, load("slow").durationSec, 1e-9)
    }

    @Test
    fun holdNotesAreBrakeOnly() {
        for (id in listOf("slow", "brake", "festival")) {
            assertTrue(load(id).notes.filter { it.isHold }.all { it.lane == Lane.BRAKE })
        }
    }
}
