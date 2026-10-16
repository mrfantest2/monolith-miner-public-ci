package com.fantest.monolithminer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveIntegrityTest {
    @Test fun legacySaveWithoutOptionalKeysStillLoads() {
        val state = GameState.fromJson(
            """{"ore":85,"depth":7,"miners":[2,0,1],"upgrades":[1],"savedAt":1234}"""
        )
        assertEquals(85.0, state.ore, 0.00001)
        assertEquals(85.0, state.lifetimeOre, 0.00001)
        assertEquals(7, state.depth)
        assertEquals(7, state.maxDepthRun)
        assertEquals(7, state.maxDepthEver)
        assertEquals(2, state.minerLevels[0])
        assertEquals(1, state.minerLevels[2])
        assertEquals(1, state.upgradeLevels[0])
    }

    @Test fun negativeOrImpossibleValuesAreClampedWithoutWipingSave() {
        val saved = """{"ore":-50,"lifetimeOre":-15,"depth":12,"maxDepthRun":3,"maxDepthEver":5,"hp":-30,"relics":-4,"ascensions":-9,"miners":[-200,3],"upgrades":[-4,10000],"savedAt":0}"""
        val state = GameState.fromJson(saved)
        assertEquals(0.0, state.ore, 0.00001)
        assertEquals(0.0, state.lifetimeOre, 0.00001)
        assertEquals(12, state.depth)
        assertEquals(12, state.maxDepthRun)
        assertEquals(12, state.maxDepthEver)
        assertEquals(0, state.relics)
        assertEquals(0, state.ascensions)
        assertEquals(0, state.minerLevels[0])
        assertEquals(3, state.minerLevels[1])
        assertEquals(0, state.upgradeLevels[0])
        assertEquals(Defs.upgrades[1].maxLevel, state.upgradeLevels[1])
        assertEquals(1e-6, state.hp, 0.000000001)
    }

    @Test fun futureSaveTimestampCannotBlockOfflineIncomeForever() {
        val state = GameState.fromJson("""{"savedAt":9223372036854775807,"miners":[5]}""")
        assertTrue(state.lastSaveMs <= System.currentTimeMillis())
    }

    @Test fun validProgressRoundTripPreservesBalancesAndUpgrades() {
        val original = GameState()
        original.ore = 85000.5
        original.lifetimeOre = 250000.5
        original.depth = 32
        original.maxDepthRun = 40
        original.maxDepthEver = 66
        original.hp = 200.0
        original.totalTaps = 900L
        original.minerLevels[0] = 12
        original.minerLevels[3] = 4
        original.upgradeLevels[Defs.UP_TAP] = 21
        original.upgradeLevels[Defs.UP_EFF] = 5
        val parsed = GameState.fromJson(original.toJson())
        assertEquals(original.ore, parsed.ore, 0.00001)
        assertEquals(original.lifetimeOre, parsed.lifetimeOre, 0.00001)
        assertEquals(original.depth, parsed.depth)
        assertEquals(original.maxDepthRun, parsed.maxDepthRun)
        assertEquals(original.maxDepthEver, parsed.maxDepthEver)
        assertEquals(original.hp, parsed.hp, 0.00001)
        assertEquals(original.totalTaps, parsed.totalTaps)
        assertEquals(original.minerLevels.toList(), parsed.minerLevels.toList())
        assertEquals(original.upgradeLevels.toList(), parsed.upgradeLevels.toList())
    }
}