package com.fantest.monolithminer

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

data class RelicUpgradeDef(val name: String, val desc: String, val cost: Int, val maxLevel: Int)
data class TrophyDef(val name: String, val desc: String)

data class MinerDef(val name: String, val desc: String, val baseCost: Double, val baseDps: Double)

data class UpgradeDef(
    val name: String,
    val desc: String,
    val baseCost: Double,
    val growth: Double,
    val maxLevel: Int
)

object Defs {
    const val MINER_GROWTH = 1.15
    const val ASCEND_MIN_DEPTH = 20
    const val MAX_OFFLINE_SECONDS = 8 * 3600L
    val relicUpgrades = listOf(
        RelicUpgradeDef("Rune Grip", "+15% tapping damage per level", 2, 20),
        RelicUpgradeDef("Eternal Crew", "+15% miner damage per level", 3, 20),
        RelicUpgradeDef("Golden Vein", "+10% obelisk bounty per level", 4, 20)
    )
    val trophies = listOf(
        TrophyDef("First Strike", "Tap the monolith once"),
        TrophyDef("Stone Breaker", "Tap 100 times"),
        TrophyDef("Guardian Hunter", "Defeat 5 guardians"),
        TrophyDef("Deep Diver", "Reach depth 50"),
        TrophyDef("Reborn Miner", "Ascend for the first time"),
        TrophyDef("Ore Tycoon", "Earn 1 million lifetime ore"),
        TrophyDef("Shard Seeker", "Collect 5 echo shards"),
        TrophyDef("Guardian Slayer", "Defeat 10 guardians")
    )

    val miners = listOf(
        MinerDef("Apprentice Digger", "Chips away at the stone", 15.0, 0.6),
        MinerDef("Dwarf Crew", "Hammers in rhythm", 110.0, 4.5),
        MinerDef("Steam Drill", "Loud, hot, effective", 1_200.0, 34.0),
        MinerDef("Blast Team", "Dynamite does the talking", 13_000.0, 270.0),
        MinerDef("Stone Golem", "Never tires, never talks", 150_000.0, 2_200.0),
        MinerDef("Rune Laser", "Cuts along the glyphs", 1.8e6, 18_000.0),
        MinerDef("Void Excavator", "Digs from somewhere else", 2.4e7, 150_000.0)
    )

    const val UP_TAP = 0
    const val UP_EFF = 1
    const val UP_CRIT = 2
    const val UP_BOUNTY = 3
    const val UP_VAULT = 4

    val upgrades = listOf(
        UpgradeDef("Sharper Picks", "+1 base tap damage per level", 20.0, 1.55, 500),
        UpgradeDef("Team Efficiency", "+25% miner output per level", 600.0, 2.1, 200),
        UpgradeDef("Weak Points", "+1% crit chance per level (crits hit x5), max 40", 300.0, 1.9, 40),
        UpgradeDef("Bounty Hunter", "+20% obelisk break reward per level", 900.0, 1.9, 200),
        UpgradeDef("Offline Vault", "+5% offline earnings per level (max 100%)", 1_500.0, 2.4, 15)
    )

    fun costFor(base: Double, growth: Double, level: Int, n: Int): Double {
        if (n <= 0) return 0.0
        return base * growth.pow(level) * (growth.pow(n) - 1.0) / (growth - 1.0)
    }
}

enum class BoostType { DOUBLE_ORE, OVERDRIVE }
data class TapResult(val damage: Double, val crit: Boolean)

class GameState {
    var ore = 0.0
    var lifetimeOre = 0.0
    var depth = 1
    var maxDepthRun = 1
    var maxDepthEver = 1
    var hp = maxHp(1)
    var relics = 0 // spendable balance
    var totalRelicsEarned = 0 // ascension damage bonus is never lost when buying upgrades
    var ascensions = 0
    var totalTaps = 0L
    var guardiansDefeated = 0
    var echoShards = 0
    var guardianCores = 0
    var relicUpgradeLevels = IntArray(Defs.relicUpgrades.size)
    var minerLevels = IntArray(Defs.miners.size)
    var upgradeLevels = IntArray(Defs.upgrades.size)
    var lastSaveMs = System.currentTimeMillis()
    // M4: boost deadlines are real epoch times; they survive relaunch and keep
    // counting down while the app is closed (no free paused boost time).
    var doubleOreUntilMs = 0L
    var overdriveUntilMs = 0L
    var dailyLastDayKey = 0
    var dailyStreak = 0
    var dailyClaims = 0

    private fun dayKey(calendar: Calendar): Int =
        calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)

    private fun dayKey(nowMs: Long): Int =
        dayKey(Calendar.getInstance().apply { timeInMillis = nowMs })

    fun canClaimDaily(nowMs: Long = System.currentTimeMillis()): Boolean =
        dayKey(nowMs) > dailyLastDayKey

    fun nextDailyStreak(nowMs: Long = System.currentTimeMillis()): Int {
        if (!canClaimDaily(nowMs)) return dailyStreak
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return if (dailyLastDayKey == dayKey(yesterday)) (dailyStreak + 1).coerceAtMost(7) else 1
    }

    fun dailyRewardAmount(nowMs: Long = System.currentTimeMillis()): Double =
        500.0 * (1.0 + 0.5 * (nextDailyStreak(nowMs) - 1).coerceAtLeast(0))

    /** Returns the granted ore, or zero when today's reward was already collected. */
    fun claimDaily(nowMs: Long = System.currentTimeMillis()): Double {
        if (!canClaimDaily(nowMs)) return 0.0
        val amount = dailyRewardAmount(nowMs)
        dailyStreak = nextDailyStreak(nowMs)
        dailyLastDayKey = dayKey(nowMs)
        dailyClaims++
        ore += amount
        lifetimeOre += amount
        return amount
    }

    fun boostCost(type: BoostType): Double = when (type) {
        BoostType.DOUBLE_ORE -> 350.0
        BoostType.OVERDRIVE -> 750.0
    }

    fun boostDurationMs(type: BoostType): Long = when (type) {
        BoostType.DOUBLE_ORE -> 15L * 60L * 1000L
        BoostType.OVERDRIVE -> 10L * 60L * 1000L
    }

    fun boostRemainingMs(type: BoostType, nowMs: Long = System.currentTimeMillis()): Long =
        ((if (type == BoostType.DOUBLE_ORE) doubleOreUntilMs else overdriveUntilMs) - nowMs)
            .coerceAtLeast(0L)

    /** Purchase only when inactive; prevents accidental stacking or double charges. */
    fun buyBoost(type: BoostType, nowMs: Long = System.currentTimeMillis()): Boolean {
        val price = boostCost(type)
        if (boostRemainingMs(type, nowMs) > 0L || ore < price) return false
        ore -= price
        val until = nowMs + boostDurationMs(type)
        when (type) {
            BoostType.DOUBLE_ORE -> doubleOreUntilMs = until
            BoostType.OVERDRIVE -> overdriveUntilMs = until
        }
        return true
    }

    /** Number of obelisks broken since the UI last asked; used for effects. */
    var breaksPending = 0

    fun isBoss(d: Int = depth) = d % 10 == 0

    /** A crystal-rich special monolith appears every seven depths except bosses. */
    fun isEchoNode(d: Int = depth) = d % 7 == 0 && !isBoss(d)

    fun maxHp(d: Int = depth): Double {
        val base = 10.0 * 1.28.pow(d - 1)
        return if (isBoss(d)) base * 4.0 else base
    }

    fun bounty(d: Int = depth): Double {
        val b = maxHp(d) * 0.5 * (1.0 + 0.2 * upgradeLevels[Defs.UP_BOUNTY]) * (1.0 + 0.10 * relicUpgradeLevels[2])
        return if (isBoss(d)) b * 3.0 else b
    }

    fun trophyCompleted(i: Int) = when (i) {
        0 -> totalTaps >= 1
        1 -> totalTaps >= 100
        2 -> guardiansDefeated >= 5
        3 -> maxDepthEver >= 50
        4 -> ascensions >= 1
        5 -> lifetimeOre >= 1_000_000.0
        6 -> echoShards >= 5
        7 -> guardiansDefeated >= 10
        else -> false
    }

    fun trophyCount() = Defs.trophies.indices.count { trophyCompleted(it) }

    fun globalMult() = (1.0 + 0.10 * totalRelicsEarned) *
        (1.0 + 0.02 * trophyCount()) *
        (1.0 + 0.01 * echoShards.coerceAtMost(200)) *
        (1.0 + 0.015 * guardianCores.coerceAtMost(100))

    fun relicUpgradeCost(i: Int): Int {
        val def = Defs.relicUpgrades[i]
        return (def.cost * 2.0.pow(relicUpgradeLevels[i])).toInt().coerceAtLeast(def.cost)
    }

    fun buyRelicUpgrade(i: Int): Boolean {
        if (i !in Defs.relicUpgrades.indices || relicUpgradeLevels[i] >= Defs.relicUpgrades[i].maxLevel) return false
        val cost = relicUpgradeCost(i)
        if (relics < cost) return false
        relics -= cost
        relicUpgradeLevels[i]++
        return true
    }

    fun minerDps(i: Int): Double {
        val lvl = minerLevels[i]
        if (lvl == 0) return 0.0
        val milestone = 2.0.pow(lvl / 25)
        return Defs.miners[i].baseDps * lvl * milestone
    }

    fun baseDps(): Double {
        var sum = 0.0
        for (i in Defs.miners.indices) sum += minerDps(i)
        val eff = 1.0 + 0.25 * upgradeLevels[Defs.UP_EFF]
        return sum * eff * globalMult() * (1.0 + 0.15 * relicUpgradeLevels[1])
    }

    fun dps(nowMs: Long = System.currentTimeMillis()): Double =
        baseDps() * if (boostRemainingMs(BoostType.OVERDRIVE, nowMs) > 0L) 3.0 else 1.0

    fun tapDamage(): Double {
        val base = (1.0 + upgradeLevels[Defs.UP_TAP]) * globalMult() * (1.0 + 0.15 * relicUpgradeLevels[0])
        return base + dps() * 0.01
    }

    fun critChance() = 0.01 * upgradeLevels[Defs.UP_CRIT]

    fun offlineFraction() = 0.25 + 0.05 * upgradeLevels[Defs.UP_VAULT]

    fun tap(): TapResult {
        totalTaps++
        val crit = Random.nextDouble() < critChance()
        val dmg = tapDamage() * if (crit) 5.0 else 1.0
        dealDamage(dmg)
        return TapResult(dmg, crit)
    }

    fun tick(dtSeconds: Double) {
        val d = dps()
        if (d > 0) dealDamage(d * dtSeconds)
    }

    private fun dealDamage(amount: Double) {
        var remaining = amount
        var guard = 0
        while (remaining > 0.0 && guard < 60) {
            guard++
            val dealt = min(remaining, hp)
            val oreEarned = dealt * if (boostRemainingMs(BoostType.DOUBLE_ORE) > 0L) 2.0 else 1.0
            ore += oreEarned
            lifetimeOre += oreEarned
            hp -= dealt
            remaining -= dealt
            if (hp <= 1e-9) {
                if (isBoss()) {
                    guardiansDefeated++
                    guardianCores++
                }
                if (isEchoNode()) echoShards++
                val reward = bounty() * if (isEchoNode()) 1.65 else 1.0
                val boostedReward = reward * if (boostRemainingMs(BoostType.DOUBLE_ORE) > 0L) 2.0 else 1.0
                ore += boostedReward
                lifetimeOre += boostedReward
                depth++
                if (depth > maxDepthRun) maxDepthRun = depth
                if (depth > maxDepthEver) maxDepthEver = depth
                hp = maxHp()
                breaksPending++
            }
        }
    }

    // ----- Purchases -----

    fun minerCost(i: Int, n: Int) =
        Defs.costFor(Defs.miners[i].baseCost, Defs.MINER_GROWTH, minerLevels[i], n)

    fun upgradeCost(i: Int, n: Int): Double {
        val u = Defs.upgrades[i]
        return Defs.costFor(u.baseCost, u.growth, upgradeLevels[i], n)
    }

    /** How many of the requested amount (-1 = max) can actually be bought right now. */
    fun affordableMiners(i: Int, want: Int): Int {
        val limit = if (want < 0) 1000 else want
        var n = 0
        while (n < limit && minerCost(i, n + 1) <= ore) n++
        return n
    }

    fun affordableUpgrades(i: Int, want: Int): Int {
        val u = Defs.upgrades[i]
        val room = u.maxLevel - upgradeLevels[i]
        val limit = min(if (want < 0) 1000 else want, room)
        var n = 0
        while (n < limit && upgradeCost(i, n + 1) <= ore) n++
        return n
    }

    fun buyMiner(i: Int, want: Int): Boolean {
        val n = affordableMiners(i, want)
        if (n <= 0) return false
        ore -= minerCost(i, n)
        minerLevels[i] += n
        return true
    }

    fun buyUpgrade(i: Int, want: Int): Boolean {
        val n = affordableUpgrades(i, want)
        if (n <= 0) return false
        ore -= upgradeCost(i, n)
        upgradeLevels[i] += n
        return true
    }

    // ----- Ascension -----

    fun relicsOnAscend(): Int {
        if (maxDepthRun < Defs.ASCEND_MIN_DEPTH) return 0
        return floor((maxDepthRun / 10.0).pow(1.5)).toInt()
    }

    fun ascend(): Int {
        val gain = relicsOnAscend()
        if (gain <= 0) return 0
        relics += gain
        totalRelicsEarned += gain
        ascensions++
        ore = 0.0
        depth = 1
        maxDepthRun = 1
        minerLevels = IntArray(Defs.miners.size)
        upgradeLevels = IntArray(Defs.upgrades.size)
        hp = maxHp(1)
        return gain
    }

    // ----- Offline earnings -----

    /** Capture elapsed time before applying rewards, which consumes the save timestamp. */
    fun offlineElapsedSeconds(nowMs: Long): Long =
        ((nowMs - lastSaveMs) / 1000L).coerceIn(0L, Defs.MAX_OFFLINE_SECONDS)

    /** Applies offline progress; returns the ore gained (0 if none worth reporting). */
    fun applyOffline(nowMs: Long): Double {
        val elapsed = offlineElapsedSeconds(nowMs)
        if (elapsed < 60) {
            // Consume short absences to prevent accumulating active play time
            // into a later offline claim after many short resumes.
            lastSaveMs = nowMs
            return 0.0
        }
        // Integrate at each boost-expiration boundary to avoid applying a
        // ten-minute boost to an entire eight-hour offline absence.
        val startMs = nowMs - elapsed * 1000L
        val cutPoints = listOf(
            startMs, nowMs,
            doubleOreUntilMs.coerceIn(startMs, nowMs),
            overdriveUntilMs.coerceIn(startMs, nowMs)
        ).distinct().sorted()
        val base = baseDps() * offlineFraction()
        var gain = 0.0
        for (i in 0 until cutPoints.size - 1) {
            val start = cutPoints[i]
            val end = cutPoints[i + 1]
            val midpoint = start + (end - start) / 2L
            val speed = if (midpoint < overdriveUntilMs) 3.0 else 1.0
            val income = if (midpoint < doubleOreUntilMs) 2.0 else 1.0
            gain += base * (end - start) / 1000.0 * speed * income
        }
        ore += gain
        lifetimeOre += gain
        // Consume the offline interval even when no miners produced income.
        // This prevents duplicate claims and retroactive rewards after purchases.
        lastSaveMs = nowMs
        return gain
    }

    // ----- Persistence -----

    fun toJson(): String {
        val o = JSONObject()
        o.put("ore", ore)
        o.put("lifetimeOre", lifetimeOre)
        o.put("depth", depth)
        o.put("maxDepthRun", maxDepthRun)
        o.put("maxDepthEver", maxDepthEver)
        o.put("hp", hp)
        o.put("relics", relics)
        o.put("totalRelicsEarned", totalRelicsEarned)
        o.put("relicUpgradeLevels", JSONArray(relicUpgradeLevels.toList()))
        o.put("totalTaps", totalTaps)
        o.put("guardiansDefeated", guardiansDefeated)
        o.put("echoShards", echoShards)
        o.put("guardianCores", guardianCores)
        o.put("ascensions", ascensions)
        o.put("miners", JSONArray(minerLevels.toList()))
        o.put("upgrades", JSONArray(upgradeLevels.toList()))
        o.put("doubleOreUntilMs", doubleOreUntilMs)
        o.put("overdriveUntilMs", overdriveUntilMs)
        o.put("dailyLastDayKey", dailyLastDayKey)
        o.put("dailyStreak", dailyStreak)
        o.put("dailyClaims", dailyClaims)
        o.put("savedAt", System.currentTimeMillis())
        return o.toString()
    }

    companion object {
        fun fromJson(json: String?): GameState {
            val s = GameState()
            if (json.isNullOrBlank()) return s
            try {
                val o = JSONObject(json)
                // Old/partially corrupted saves must never introduce negative
                // resources, non-finite numbers, or impossible progression.
                val savedOre = o.optDouble("ore", 0.0)
                s.ore = savedOre.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                val savedLifetimeOre = o.optDouble("lifetimeOre", s.ore)
                s.lifetimeOre = savedLifetimeOre.takeIf { it.isFinite() && it >= s.ore } ?: s.ore
                s.depth = o.optInt("depth", 1).coerceAtLeast(1)
                s.maxDepthRun = o.optInt("maxDepthRun", s.depth).coerceAtLeast(s.depth)
                s.maxDepthEver = o.optInt("maxDepthEver", s.maxDepthRun).coerceAtLeast(s.maxDepthRun)
                s.relics = o.optInt("relics", 0).coerceAtLeast(0)
                s.totalRelicsEarned = o.optInt("totalRelicsEarned", s.relics).coerceAtLeast(s.relics)
                s.totalTaps = o.optLong("totalTaps", 0L).coerceAtLeast(0L)
                s.guardiansDefeated = o.optInt("guardiansDefeated", 0).coerceAtLeast(0)
                s.echoShards = o.optInt("echoShards", 0).coerceAtLeast(0)
                s.guardianCores = o.optInt("guardianCores", 0).coerceAtLeast(0)
                val permanent = o.optJSONArray("relicUpgradeLevels")
                if (permanent != null) for (i in 0 until min(permanent.length(), s.relicUpgradeLevels.size)) {
                    s.relicUpgradeLevels[i] = permanent.optInt(i).coerceIn(0, Defs.relicUpgrades[i].maxLevel)
                }
                s.ascensions = o.optInt("ascensions", 0).coerceAtLeast(0)
                val m = o.optJSONArray("miners")
                if (m != null) for (i in 0 until min(m.length(), s.minerLevels.size)) s.minerLevels[i] = m.optInt(i).coerceAtLeast(0)
                val u = o.optJSONArray("upgrades")
                if (u != null) for (i in 0 until min(u.length(), s.upgradeLevels.size)) s.upgradeLevels[i] = u.optInt(i).coerceIn(0, Defs.upgrades[i].maxLevel)
                                val savedHp = o.optDouble("hp", s.maxHp())
                s.hp = savedHp.takeIf { it.isFinite() }?.coerceIn(1e-6, s.maxHp()) ?: s.maxHp()
                s.doubleOreUntilMs = o.optLong("doubleOreUntilMs", 0L).coerceAtLeast(0L)
                s.overdriveUntilMs = o.optLong("overdriveUntilMs", 0L).coerceAtLeast(0L)
                s.dailyLastDayKey = o.optInt("dailyLastDayKey", 0).coerceAtLeast(0)
                s.dailyStreak = o.optInt("dailyStreak", 0).coerceIn(0, 7)
                s.dailyClaims = o.optInt("dailyClaims", 0).coerceAtLeast(0)
                s.lastSaveMs = o.optLong("savedAt", System.currentTimeMillis()).coerceIn(0L, System.currentTimeMillis())
            } catch (_: Exception) {
                return GameState()
            }
            return s
        }
    }
}

object Fmt {
    /** Optional player preference; does not change the stored ore values. */
    var scientificNotation = false
    private val suffixes = arrayOf("", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc")

    fun num(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "∞"
        if (scientificNotation && v >= 1000.0)
            return String.format(java.util.Locale.US, "%.2e", v)
        if (v < 1000.0) return if (v < 10.0) String.format("%.2f", v) else if (v < 100.0) String.format("%.1f", v) else String.format("%.0f", v)
        var idx = 0
        var x = v
        while (x >= 1000.0 && idx < suffixes.size - 1) {
            x /= 1000.0
            idx++
        }
        if (x >= 1000.0) return String.format("%.2e", v)
        return String.format("%.2f%s", x, suffixes[idx])
    }

    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
