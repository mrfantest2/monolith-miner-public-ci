package com.fantest.monolithminer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RewardsBoostsTest {

    private fun noon(year: Int, monthZeroBased: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, monthZeroBased, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test fun dailyRewardCannotBeCollectedTwice() {
        val state = GameState()
        val today = noon(2026, Calendar.OCTOBER, 8)
        assertTrue(state.canClaimDaily(today))
        assertEquals(500.0, state.claimDaily(today), 0.001)
        assertFalse(state.canClaimDaily(today + 1_000L))
        assertEquals(0.0, state.claimDaily(today + 1_000L), 0.001)
        assertEquals(1, state.dailyClaims)
        assertEquals(1, state.dailyStreak)
    }

    @Test fun dailyStreakSurvivesNewYearButResetsAfterMissedDay() {
        val state = GameState()
        val yearEnd = noon(2026, Calendar.DECEMBER, 31)
        val newYear = noon(2027, Calendar.JANUARY, 1)
        val skippedDay = noon(2027, Calendar.JANUARY, 4)
        assertEquals(500.0, state.claimDaily(yearEnd), 0.001)
        assertEquals(750.0, state.claimDaily(newYear), 0.001)
        assertEquals(2, state.dailyStreak)
        assertEquals(500.0, state.claimDaily(skippedDay), 0.001)
        assertEquals(1, state.dailyStreak)
        assertEquals(3, state.dailyClaims)
    }

    @Test fun boostsCostOreExpireAndRejectStacking() {
        val state = GameState()
        state.ore = 5000.0
        state.minerLevels[0] = 10
        val at = noon(2026, Calendar.OCTOBER, 8)
        val base = state.baseDps()

        assertTrue(state.buyBoost(BoostType.DOUBLE_ORE, at))
        assertFalse(state.buyBoost(BoostType.DOUBLE_ORE, at + 2_000L))
        assertEquals(4650.0, state.ore, 0.001)
        assertTrue(state.buyBoost(BoostType.OVERDRIVE, at))
        assertEquals(3900.0, state.ore, 0.001)
        assertEquals(base * 3.0, state.dps(at + 30_000L), 0.001)
        assertEquals(base, state.dps(at + 11L * 60_000L), 0.001)
        assertEquals(0L, state.boostRemainingMs(BoostType.DOUBLE_ORE, at + 16L * 60_000L))
        assertTrue(state.buyBoost(BoostType.DOUBLE_ORE, at + 16L * 60_000L))
    }

    @Test fun offlineRewardOnlyMultipliesWithinOriginalBoostWindows() {
        val state = GameState()
        state.minerLevels[0] = 10
        val start = noon(2026, Calendar.OCTOBER, 8)
        state.lastSaveMs = start
        state.doubleOreUntilMs = start + 5L * 60_000L
        state.overdriveUntilMs = start + 10L * 60_000L
        val rate = state.baseDps() * state.offlineFraction()

        // First 5 min: x6, next 5 min: x3, remaining 10 min: x1.
        val gain = state.applyOffline(start + 20L * 60_000L)
        assertEquals(rate * (300.0 * 6.0 + 300.0 * 3.0 + 600.0), gain, 0.001)
        assertEquals(gain, state.ore, 0.001)
        assertEquals(gain, state.lifetimeOre, 0.001)
    }
    @Test fun echoNodesAndGuardianCoresEarnedOnlyOnTheirDepth() {
        val state = GameState()
        assertTrue(state.isEchoNode(7))
        assertFalse(state.isEchoNode(10))
        assertFalse(state.isEchoNode(8))
        state.depth = 7
        state.hp = 1.0
        state.tap()
        assertEquals(1, state.echoShards)
        assertEquals(8, state.depth)
        state.depth = 10
        state.hp = 1.0
        state.tap()
        assertEquals(1, state.guardianCores)
        assertEquals(1, state.guardiansDefeated)
        assertEquals(1, state.echoShards)
    }

    @Test fun permanentEncounterBonusesAreCapped() {
        val state = GameState()
        val original = state.globalMult()
        state.echoShards = 1
        assertTrue(state.globalMult() > original)
        state.echoShards = 200
        state.guardianCores = 100
        val capped = state.globalMult()
        state.echoShards = 300
        state.guardianCores = 200
        assertEquals(capped, state.globalMult(), 0.00001)
    }


    @Test fun notationChangesDisplayButNeverEconomy() {
        val s = GameState()
        s.ore = 12_345.0
        val before = s.ore
        try {
            Fmt.scientificNotation = true
            assertTrue(Fmt.num(s.ore).contains("e"))
            Fmt.scientificNotation = false
            assertTrue(Fmt.num(s.ore).contains("K"))
            assertEquals(before, s.ore, 0.001)
        } finally {
            Fmt.scientificNotation = false
        }
    }

    @Test fun firstMinerPurchaseProducesActualIdleIncome() {
        val state = GameState()
        state.ore = 200.0
        assertEquals(0.0, state.dps(), 0.0001)
        assertTrue(state.buyMiner(0, 1))
        assertEquals(1, state.minerLevels[0])
        assertTrue(state.dps() > 0.0)
        val before = state.lifetimeOre
        state.tick(1.0)
        assertTrue(state.lifetimeOre > before)
    }

    @Test fun offlineRewardDoesNotApplyBeforeOneMinute() {
        val state = GameState()
        state.minerLevels[0] = 10
        val now = 1_800_000_000_000L
        state.lastSaveMs = now - 45_000L
        assertEquals(0.0, state.applyOffline(now), 0.0001)
        assertEquals(0.0, state.ore, 0.0001)
    }

    @Test fun purchaseRejectionDoesNotMutateProgress() {
        val state = GameState()
        val beforeOre = state.ore
        assertFalse(state.buyMiner(6, 1))
        assertFalse(state.buyUpgrade(3, 1))
        assertEquals(beforeOre, state.ore, 0.0001)
        assertEquals(0, state.minerLevels[6])
    }

}
