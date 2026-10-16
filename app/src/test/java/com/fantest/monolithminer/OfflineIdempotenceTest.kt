package com.fantest.monolithminer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineIdempotenceTest {
    @Test fun offlineRewardCannotBeCollectedTwice() {
        val state = GameState()
        state.minerLevels[0] = 10
        val now = 1_800_000_000_000L
        state.lastSaveMs = now - 120_000L
        val earned = state.applyOffline(now)
        assertTrue(earned > 0.0)
        assertEquals(0.0, state.applyOffline(now), 0.000001)
        assertEquals(earned, state.ore, 0.000001)
    }
}
