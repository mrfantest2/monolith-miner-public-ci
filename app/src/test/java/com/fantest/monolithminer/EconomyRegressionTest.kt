package com.fantest.monolithminer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomyRegressionTest {
    @Test fun zeroIncomeDowntimeCannotBeBackdatedAfterBuyingFirstMiner() {
        val state = GameState()
        val start = 1_800_000_000_000L
        state.lastSaveMs = start
        val now = start + 90L * 60_000L
        assertEquals(0.0, state.applyOffline(now), 0.000001)
        assertEquals(now, state.lastSaveMs)
        state.ore = 20.0
        assertTrue(state.buyMiner(0, 1))
        assertEquals(0.0, state.applyOffline(now), 0.000001)
        assertEquals(5.0, state.ore, 0.000001)
    }

    @Test fun offlineRewardIsCappedAtEightHours() {
        val state = GameState()
        state.minerLevels[0] = 10
        val now = 1_800_000_000_000L
        state.lastSaveMs = now - 30L * 60L * 60_000L
        val expected = state.baseDps() * state.offlineFraction() * Defs.MAX_OFFLINE_SECONDS
        assertEquals(expected, state.applyOffline(now), 0.000001)
        assertEquals(now, state.lastSaveMs)
    }

    @Test fun tenMinerPurchaseMatchesTenIndividualPurchases() {
        val bulk = GameState()
        val single = GameState()
        bulk.ore = 1_000_000.0
        single.ore = 1_000_000.0
        assertTrue(bulk.buyMiner(0, 10))
        repeat(10) { assertTrue(single.buyMiner(0, 1)) }
        assertEquals(10, bulk.minerLevels[0])
        assertEquals(single.minerLevels[0], bulk.minerLevels[0])
        assertEquals(single.ore, bulk.ore, 0.000001)
        assertEquals(single.baseDps(), bulk.baseDps(), 0.000001)
    }

    @Test fun maxUpgradePurchaseNeverExceedsLevelCap() {
        val state = GameState()
        val index = Defs.UP_CRIT
        state.upgradeLevels[index] = Defs.upgrades[index].maxLevel - 1
        state.ore = 1.0e30
        assertEquals(1, state.affordableUpgrades(index, -1))
        assertTrue(state.buyUpgrade(index, -1))
        assertEquals(Defs.upgrades[index].maxLevel, state.upgradeLevels[index])
        assertFalse(state.buyUpgrade(index, -1))
        assertEquals(0, state.affordableUpgrades(index, -1))
        assertEquals(0.40, state.critChance(), 0.000001)
    }

    @Test fun permanentRelicBonusSurvivesSpendingAndAscending() {
        val state = GameState()
        state.maxDepthRun = 20
        val earned = state.ascend()
        assertTrue(earned > 0)
        assertEquals(earned, state.totalRelicsEarned)
        val beforeSpend = state.globalMult()
        assertTrue(state.buyRelicUpgrade(0))
        assertEquals(earned, state.totalRelicsEarned)
        assertEquals(beforeSpend, state.globalMult(), 0.000001)
        assertEquals(1, state.ascensions)
        assertEquals(1, state.depth)
    }
}
