package com.dontstudyy.carbeat

import com.dontstudyy.carbeat.gate.Gear
import com.dontstudyy.carbeat.gate.ParkingGate
import com.dontstudyy.carbeat.gate.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingGateTest {
    private fun gate(gear: Gear, ux: Boolean?) = ParkingGate.evaluate(VehicleSnapshot(gear, ux))

    @Test
    fun opensOnlyWhenParkAndNotRestricted() {
        assertTrue(gate(Gear.PARK, false).open)
    }

    @Test
    fun closesWhenNotPark() {
        for (g in listOf(Gear.DRIVE, Gear.REVERSE, Gear.NEUTRAL)) {
            val s = gate(g, false)
            assertFalse("$g", s.open)
            assertFalse(s.gearOk)
        }
    }

    @Test
    fun closesWhenUxRestrictedEvenInPark() {
        val s = gate(Gear.PARK, true)
        assertFalse(s.open)
        assertTrue(s.gearOk)
        assertFalse(s.stopped)
    }

    @Test
    fun closesWhenUnknown() {
        assertFalse(ParkingGate.evaluate(VehicleSnapshot()).open)
        assertFalse(gate(Gear.PARK, null).open)
        assertFalse(gate(Gear.UNKNOWN, false).open)
    }

    @Test
    fun aaosGearValues() {
        assertEquals(Gear.PARK, Gear.fromAaos(4))
        assertEquals(Gear.DRIVE, Gear.fromAaos(8))
        assertEquals(Gear.REVERSE, Gear.fromAaos(2))
        assertEquals(Gear.NEUTRAL, Gear.fromAaos(1))
        assertEquals(Gear.UNKNOWN, Gear.fromAaos(0))
        assertEquals(Gear.UNKNOWN, Gear.fromAaos(16))
    }

    @Test
    fun reasonText() {
        assertEquals("", gate(Gear.PARK, false).reason)
        assertEquals("기어가 P가 아닙니다", gate(Gear.DRIVE, false).reason)
        assertEquals("주행 규제가 켜져 있습니다", gate(Gear.PARK, true).reason)
    }
}
