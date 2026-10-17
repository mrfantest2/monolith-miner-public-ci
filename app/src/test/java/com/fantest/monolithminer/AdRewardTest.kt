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

    @Test fun rewardDoesNotChangeDepthOrRelics() {
        val s = GameState()
        s.depth = 12
        s.relics = 7
        s.claimRewardedOre()
        assertEquals(12, s.depth)
        assertEquals(7, s.relics)
    }
}
