package com.fantest.monolithminer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdRewardTest {
    @Test fun newPlayerRewardGrantsCurrencyAndPersists() {
        val s = GameState()
        assertEquals(500.0, s.rewardedOreAmount(), 0.0)
        val initial = s.lifetimeOre
        val granted = s.claimRewardedOre()
        assertEquals(500.0, granted, 0.0)
        assertEquals(granted, s.ore, 0.0)
        assertEquals(initial + granted, s.lifetimeOre, 0.0)
        val recovered = GameState.fromJson(s.toJson())
        assertEquals(s.ore, recovered.ore, 0.0)
        assertEquals(s.lifetimeOre, recovered.lifetimeOre, 0.0)
    }

    @Test fun rewardScalesWithDepthWithoutOverflow() {
        val s = GameState()
        s.depth = 35
        val expected = (s.bounty() * 2.0).coerceIn(500.0, 1.0e12)
        assertEquals(expected, s.rewardedOreAmount(), 0.000001)
        s.depth = 100000
        val amount = s.rewardedOreAmount()
        assertTrue(amount.isFinite() && amount > 0.0)
    }

    @Test fun earnedGoldenShiftIsFreeAndPersistsUntilDeadline() {
        val s = GameState()
        val now = 1_000_000L
        s.ore = 100.0
        assertTrue(s.grantRewardedBoost(BoostType.DOUBLE_ORE, now))
        assertEquals(100.0, s.ore, 0.0)
        assertEquals(15L * 60_000L, s.boostRemainingMs(BoostType.DOUBLE_ORE, now))
        assertEquals(false, s.grantRewardedBoost(BoostType.DOUBLE_ORE, now + 1000L))
        assertEquals(15L * 60_000L, s.boostRemainingMs(BoostType.DOUBLE_ORE, now))
        val restored = GameState.fromJson(s.toJson())
        assertEquals(15L * 60_000L, restored.boostRemainingMs(BoostType.DOUBLE_ORE, now))
    }

    @Test fun earnedDrillOverdriveIsFreeNonStackingAndExpires() {
        val s = GameState()
        val now = 2_000_000L
        assertTrue(s.grantRewardedBoost(BoostType.OVERDRIVE, now))
        assertEquals(10L * 60_000L, s.boostRemainingMs(BoostType.OVERDRIVE, now))
        assertEquals(false, s.grantRewardedBoost(BoostType.OVERDRIVE, now + 500L))
        assertEquals(10L * 60_000L, s.boostRemainingMs(BoostType.OVERDRIVE, now))
        assertEquals(0L, s.boostRemainingMs(BoostType.OVERDRIVE, now + 600_000L))
        assertTrue(s.grantRewardedBoost(BoostType.OVERDRIVE, now + 600_000L))
        assertEquals(10L * 60_000L, s.boostRemainingMs(BoostType.OVERDRIVE, now + 600_000L))
    }

    @Test fun rewardDoesNotChangeDepthOrRelics() {
        val s = GameState()
        s.depth = 12
        s.relics = 7
        s.claimRewardedOre()
        assertEquals(12, s.depth)
        assertEquals(7, s.relics)
    }
}
