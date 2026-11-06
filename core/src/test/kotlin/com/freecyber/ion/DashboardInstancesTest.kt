package com.freecyber.ion

import org.junit.Assert.*
import org.junit.Test

class DashboardInstancesTest {
    @Test fun repeatedSwitchesHaveIndependentAssignmentsAndSurviveRestart() {
        val layout = DashboardLayout(emptyList()).add(DashTile.TOGGLE).add(DashTile.TOGGLE)
        val a = layout.visible[0].id; val b = layout.visible[1].id
        assertNotEquals(a, b)
        val edited = layout.setToggle(a, DashToggle.TRANSPORT).setToggle(b, DashToggle.MUTE).resize(a)
        assertEquals(edited, DashboardLayout.decode(edited.encode()))
        assertEquals(DashToggle.TRANSPORT, edited.visible[0].toggleTarget)
        assertEquals(DashToggle.MUTE, edited.visible[1].toggleTarget)
        assertTrue(edited.visible[0].wide)
        assertFalse(edited.visible[1].wide)
        assertEquals(listOf(b, a), edited.moveVisible(b, -1).visible.map { it.id })
        assertEquals(listOf(b), edited.remove(a).visible.map { it.id })
    }

    @Test fun deletedTilesAndEmptyLayoutStayDeleted() {
        val empty = DashboardLayout.DEFAULT.slots.fold(DashboardLayout.DEFAULT) { l, s -> l.remove(s.id) }
        assertTrue(DashboardLayout.decode(empty.encode()).slots.isEmpty())
        val deleted = DashboardLayout.DEFAULT.remove(DashTile.SPEED.name)
        assertEquals(deleted, DashboardLayout.decode(deleted.encode()))
    }

    @Test fun legacyQuickTilesKeepPositionSizeAndAssignment() {
        val legacy = DashboardLayout.decode("QUICK_MUTE:1:1,QUICK_HIGH_BEAM:1:0,PLAY_SOUND:1:0:7")
        assertEquals(DashToggle.MUTE, legacy.visible[0].toggleTarget)
        assertEquals(DashToggle.HIGH_BEAM, legacy.visible[1].toggleTarget)
        assertTrue(legacy.visible[0].wide)
        assertEquals(7, legacy.visible[2].param)
        assertEquals(legacy, DashboardLayout.decode(legacy.encode()))
    }

    @Test fun beamWritesPreserveOtherBeamAndStationarySwitchesKeepTheirTier() {
        val t = V12Protocol.Telemetry(96.0, 0.0, 0.0, 80.0, 30, 30, 0.0, 0.0, false, true, true)
        assertEquals(V12Protocol.Write(V12Protocol.Control.LIGHTS, 0, 1), DashToggle.LOW_BEAM.write(false, t))
        assertEquals(V12Protocol.Write(V12Protocol.Control.LIGHTS, 1, 0), DashToggle.HIGH_BEAM.write(false, t))
        for(target in listOf(DashToggle.TRANSPORT, DashToggle.OFF_ROAD, DashToggle.LOCK, DashToggle.HANDLE)) {
            assertEquals(V12Protocol.Tier.STATIONARY, target.write(true, t).kind.tier)
        }
    }
}
