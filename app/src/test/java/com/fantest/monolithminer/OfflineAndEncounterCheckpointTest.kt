package com.fantest.monolithminer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CP15: regression coverage for mid/late progression, encounters, and
 * repeated short background sessions. These tests never reset device saves.
 */
class OfflineAndEncounterCheckpointTest {
    @Test fun shortResumesConsumeTheirOwnTimeInsteadOfBackdatingEarnings() {
        val state = GameState()
        state.minerLevels[0] = 10
        val start = 1_800_000_000_000L
        state.lastSaveMs = start

        assertEquals(0.0, state.applyOffline(start + 59_000L), 0.000001)
        assertEquals(start + 59_000L, state.lastSaveMs)
        assertEquals(0.0, state.applyOffline(start + 64_000L), 0.000001)
        assertEquals(start + 64_000L, state.lastSaveMs)

        val expected = state.baseDps() * state.offlineFraction() * 60.0
        assertEquals(expected, state.applyOffline(start + 124_000L), 0.000001)
        assertEquals(start + 124_000L, state.lastSaveMs)
        assertEquals(0.0, state.applyOffline(start + 124_000L), 0.000001)
    }

    @Test fun offlineDialogCanDisplayElapsedTimeBeforeClaimConsumesTimestamp() {
        val state = GameState()
        state.minerLevels[0] = 10
        val now = 1_800_000_000_000L
        state.lastSaveMs = now - 20L * 60_000L

        val elapsedForDialog = state.offlineElapsedSeconds(now)
        assertEquals(20L * 60L, elapsedForDialog)
        assertTrue(state.applyOffline(now) > 0.0)
        assertEquals(0L, state.offlineElapsedSeconds(now))
        assertEquals("20m", Fmt.duration(elapsedForDialog))

        state.lastSaveMs = now - 30L * 60L * 60_000L
        assertEquals(Defs.MAX_OFFLINE_SECONDS, state.offlineElapsedSeconds(now))
    }

    @Test fun midLateEchoAndGuardianEncountersAwardOnlyTheirOwnCurrencies() {
        val state = GameState()
        val initialMultiplier = state.globalMult()
        for (d in listOf(7, 10, 14, 20)) {
            state.depth = d
            state.hp = 1.0
            state.tap()
            assertEquals(d + 1, state.depth)
        }
        assertEquals(2, state.echoShards)
        assertEquals(2, state.guardianCores)
        assertEquals(2, state.guardiansDefeated)
        assertTrue(state.globalMult() > initialMultiplier)
        assertFalse(state.isEchoNode(70))
        assertTrue(state.isBoss(70))
        assertTrue(state.maxDepthEver >= 21)
    }

    @Test fun buyMaxMatchesRepeatedSingleMinerPurchasesAtMidGameBalance() {
        val bulk = GameState()
        val singles = GameState()
        bulk.ore = 5_000_000.0
        singles.ore = bulk.ore
        assertTrue(bulk.buyMiner(2, -1))
        var purchases = 0
        while (singles.buyMiner(2, 1)) {
            purchases++
            assertTrue("Single purchases should terminate", purchases <= 1000)
        }
        assertEquals(purchases, bulk.minerLevels[2])
        assertEquals(purchases, singles.minerLevels[2])
        assertEquals(singles.ore, bulk.ore, 0.00001)
        assertEquals(singles.baseDps(), bulk.baseDps(), 0.00001)
    }

    @Test fun ascensionKeepsEarnedEncounterCurrenciesAndPermanentMultiplier() {
        val state = GameState()
        state.depth = 30
        state.maxDepthRun = 30
        state.maxDepthEver = 30
        state.echoShards = 9
        state.guardianCores = 3
        state.guardiansDefeated = 3
        state.ore = 10_000.0
        state.minerLevels[2] = 4
        val before = state.globalMult()
        val gain = state.ascend()
        assertTrue(gain > 0)
        assertEquals(1, state.depth)
        assertEquals(0.0, state.ore, 0.000001)
        assertEquals(0, state.minerLevels[2])
        assertEquals(9, state.echoShards)
        assertEquals(3, state.guardianCores)
        assertEquals(3, state.guardiansDefeated)
        assertTrue(state.globalMult() > before)
    }
}
