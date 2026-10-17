package com.fantest.monolithminer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.os.SystemClock
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fantest.monolithminer.databinding.ActivityMainBinding
import com.fantest.monolithminer.databinding.ItemRowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private enum class Tab { MINERS, UPGRADES, ASCEND, RELICS, TROPHIES, EXTRAS }

    private data class Row(
        val title: String,
        val sub: String,
        val button: String,
        val enabled: Boolean,
        val onClick: () -> Unit
    )

    private lateinit var binding: ActivityMainBinding
    private lateinit var state: GameState
    private val adapter = RowAdapter()

    private var tab = Tab.MINERS
    private var buyAmount = 1 // -1 = max
    private var uiTimer = 0.0
    private var saveTimer = 0.0
    private var hapticsEnabled = true
    private var soundEnabled = false
    private var rewardedAtMs = 0L
    private var gameAds: GameAds? = null
    private var scientificNotation = false
    private val biomePreviewDepths = intArrayOf(1, 100, 300, 600, 1000)
    private var biomePreviewIndex = -1
    private var toneGenerator: ToneGenerator? = null
    private var lastSoundMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val preferences = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        hapticsEnabled = preferences.getBoolean(KEY_HAPTICS, true)
        soundEnabled = preferences.getBoolean(KEY_SOUND, false)
        rewardedAtMs = preferences.getLong(KEY_REWARDED_AT, 0L)
        scientificNotation = preferences.getBoolean(KEY_NOTATION, false)
        Fmt.scientificNotation = scientificNotation

        state = GameState.fromJson(getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
        val startNow = System.currentTimeMillis()
        val offlineSeconds = state.offlineElapsedSeconds(startNow)
        val offline = state.applyOffline(startNow)
        if (offline > 0) {
            showOfflineDialog(offline, offlineSeconds)
            // Immediately commit collected offline earnings so relaunches cannot claim them twice.
            save()
        }

        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.list.itemAnimator = null

        binding.gameView.state = state
        binding.gameView.onTapListener = { x, y ->
            val r = state.tap()
            binding.gameView.tapEffect(x, y, r.damage, r.crit)
            if (hapticsEnabled) {
                binding.gameView.performHapticFeedback(
                    if (r.crit) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
                )
            }
            playTapSound(r.crit)
        }
        binding.gameView.onFrame = { dt ->
            if (biomePreviewIndex < 0) state.tick(dt)
            uiTimer += dt
            saveTimer += dt
            if (uiTimer >= 0.25) {
                uiTimer = 0.0
                refreshUi(false)
            }
            if (saveTimer >= 10.0) {
                saveTimer = 0.0
                save()
            }
        }

        binding.tabGroup.check(R.id.tabMiners)
        binding.buyGroup.check(R.id.buy1)
        binding.tabGroup.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            tab = when (id) {
                R.id.tabUpgrades -> Tab.UPGRADES
                R.id.tabAscend -> Tab.ASCEND
                R.id.tabRelics -> Tab.RELICS
                R.id.tabTrophies -> Tab.TROPHIES
                R.id.tabExtras -> Tab.EXTRAS
                else -> Tab.MINERS
            }
            binding.buyGroup.visibility = if (tab == Tab.MINERS || tab == Tab.UPGRADES) android.view.View.VISIBLE else android.view.View.GONE
            refreshUi(true)
        }
        binding.buyGroup.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            buyAmount = when (id) {
                R.id.buy10 -> 10
                R.id.buyMax -> -1
                else -> 1
            }
            refreshUi(false)
        }
        refreshUi(true)
        gameAds = GameAds(this, binding.adViewContainer) {
            if (::state.isInitialized) refreshUi(true)
        }
        gameAds?.start()
    }

    override fun onResume() {
        super.onResume()
        // A paused Activity may be resumed without onCreate. Award the capped
        // offline income and commit immediately so rotations/reopens cannot
        // collect the same absence twice.
        if (::state.isInitialized) {
            // A rewarded ad is intentional gameplay, not a return from a long absence.
            val returningFromRewardedAd = gameAds?.consumeRewardedAdReturn() == true
            val resumeNow = System.currentTimeMillis()
            val offlineSeconds = state.offlineElapsedSeconds(resumeNow)
            val offline = state.applyOffline(resumeNow)
            if (offline > 0.0) {
                if (!returningFromRewardedAd) showOfflineDialog(offline, offlineSeconds)
                save()
                refreshUi(true)
            }
        }
        binding.gameView.start()
        gameAds?.onResume()
    }

    override fun onPause() {
        gameAds?.onPause()
        binding.gameView.stop()
        save()
        super.onPause()
    }

    override fun onDestroy() {
        gameAds?.destroy()
        gameAds = null
        toneGenerator?.release()
        toneGenerator = null
        super.onDestroy()
    }

    private fun playTapSound(crit: Boolean) {
        if (!soundEnabled) return
        val now = SystemClock.uptimeMillis()
        if (now - lastSoundMs < 110L) return
        lastSoundMs = now
        try {
            if (toneGenerator == null) toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 20)
            toneGenerator?.startTone(
                if (crit) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP,
                35
            )
        } catch (_: RuntimeException) {
            // Audio output is optional: tapping must still work when sound is unavailable.
        }
    }

    private fun toggleHaptics() {
        hapticsEnabled = !hapticsEnabled
        getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HAPTICS, hapticsEnabled).apply()
        refreshUi(true)
    }

    private fun toggleNotation() {
        scientificNotation = !scientificNotation
        Fmt.scientificNotation = scientificNotation
        getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NOTATION, scientificNotation).apply()
        refreshUi(true)
    }

    private fun toggleSound() {
        soundEnabled = !soundEnabled
        if (!soundEnabled) toneGenerator?.stopTone()
        getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SOUND, soundEnabled).apply()
        refreshUi(true)
    }

    private fun rewardedCooldownRemaining(now: Long = System.currentTimeMillis()): Long =
        (rewardedAtMs + REWARDED_COOLDOWN_MS - now).coerceAtLeast(0L)

    private fun watchRewardedOre() {
        if (rewardedCooldownRemaining() > 0L) return
        gameAds?.showReward {
            // Save immediately. No reward is granted for a failed or skipped ad.
            if (rewardedCooldownRemaining() == 0L) {
                val amount = state.claimRewardedOre()
                rewardedAtMs = System.currentTimeMillis()
                getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_REWARDED_AT, rewardedAtMs).commit()
                save()
                refreshUi(true)
                MaterialAlertDialogBuilder(this)
                    .setTitle("Sponsored ore cache")
                    .setMessage("Collected ${Fmt.num(amount)} ore. Thanks for supporting Monolith Miner!")
                    .setPositiveButton("Continue", null)
                    .show()
            }
        }
    }

    private fun remainingClock(ms: Long): String {
        val secs = (ms + 999L) / 1000L
        return "${secs / 60}:${(secs % 60).toString().padStart(2, '0')}"
    }

    private fun boostRow(type: BoostType, title: String, detail: String): Row {
        val remaining = state.boostRemainingMs(type)
        return Row(
            title,
            detail + if (remaining > 0L) "\nActive: ${remainingClock(remaining)} remaining" else "",
            if (remaining > 0L) "ACTIVE\n${remainingClock(remaining)}" else
                "START\n${Fmt.num(state.boostCost(type))} ore",
            remaining <= 0L && state.ore >= state.boostCost(type)
        ) {
            if (state.buyBoost(type)) {
                save()
                refreshUi(true)
            }
        }
    }

    private fun confirmResetSave() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset all progress?")
            .setMessage("This permanently erases ore, mining depth, miners, relics, trophies, daily rewards and boost timers. Sound and vibration settings will be kept.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Final confirmation")
                    .setMessage("This cannot be undone. Delete the current Monolith Miner save?")
                    .setNegativeButton("Keep progress", null)
                    .setPositiveButton("Delete save") { _, _ ->
                        state = GameState()
                        binding.gameView.state = state
                        save()
                        binding.gameView.invalidate()
                        refreshUi(true)
                    }
                    .show()
            }
            .show()
    }

    private fun save() {
        state.lastSaveMs = System.currentTimeMillis()
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, state.toJson()).apply()
    }

    private fun showOfflineDialog(gain: Double, elapsedSeconds: Long) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Welcome back")
            .setMessage("Your miners dug for ${Fmt.duration(elapsedSeconds)} while you were away and brought back ${Fmt.num(gain)} ore (${(state.offlineFraction() * 100).toInt()}% efficiency).")
            .setPositiveButton("Collect", null)
            .show()
    }

    // ----- UI -----

    private fun refreshUi(forceRebuild: Boolean) {
        binding.oreText.text = "${Fmt.num(state.ore)} ore"
        val now = System.currentTimeMillis()
        val boostStatus = listOfNotNull(
            if (state.boostRemainingMs(BoostType.DOUBLE_ORE, now) > 0L) "2x ORE" else null,
            if (state.boostRemainingMs(BoostType.OVERDRIVE, now) > 0L) "3x MINERS" else null
        ).joinToString("  |  ")
        val bonusStatus = if (state.echoShards > 0 || state.guardianCores > 0)
            "\nEcho ${state.echoShards}  •  Guardian cores ${state.guardianCores}" else ""
        binding.statsText.text =
            "${Fmt.num(state.dps(now))}/s  |  tap ${Fmt.num(state.tapDamage())}  |  relics ${state.relics} (earned ${state.totalRelicsEarned})" +
                bonusStatus + if (boostStatus.isNotEmpty()) "\n$boostStatus" else ""
        adapter.submit(buildRows(), forceRebuild)
    }

    private fun amountLabel(n: Int) = if (buyAmount < 0) "Max" else "x$buyAmount"

    private fun buildRows(): List<Row> = when (tab) {
        Tab.MINERS -> Defs.miners.indices.map { i ->
            val def = Defs.miners[i]
            val n = state.affordableMiners(i, buyAmount)
            val shownN = if (buyAmount < 0) max1(n) else buyAmount
            Row(
                "${def.name}  Lv ${state.minerLevels[i]}",
                "${def.desc}\nProducing ${Fmt.num(state.minerDps(i) * (1.0 + 0.25 * state.upgradeLevels[Defs.UP_EFF]) * state.globalMult())} dps",
                "Buy ${if (buyAmount < 0) "x$shownN" else amountLabel(buyAmount)}\n${Fmt.num(state.minerCost(i, shownN))}",
                n > 0 && (buyAmount < 0 || n == buyAmount)
            ) {
                if (state.buyMiner(i, buyAmount)) refreshUi(false)
            }
        }
        Tab.UPGRADES -> Defs.upgrades.indices.map { i ->
            val def = Defs.upgrades[i]
            val lvl = state.upgradeLevels[i]
            val maxed = lvl >= def.maxLevel
            val n = state.affordableUpgrades(i, buyAmount)
            val shownN = if (buyAmount < 0) max1(n) else buyAmount
            Row(
                "${def.name}  Lv $lvl${if (def.maxLevel < 500) "/${def.maxLevel}" else ""}",
                def.desc,
                if (maxed) "MAX" else "Buy ${if (buyAmount < 0) "x$shownN" else amountLabel(buyAmount)}\n${Fmt.num(state.upgradeCost(i, shownN))}",
                !maxed && n > 0 && (buyAmount < 0 || n == buyAmount)
            ) {
                if (state.buyUpgrade(i, buyAmount)) refreshUi(false)
            }
        }
        Tab.ASCEND -> {
            val gain = state.relicsOnAscend()
            listOf(
                Row(
                    "Ascend",
                    "Reset ore, depth, miners and upgrades. Every earned relic permanently adds +10% to damage, even after spending it. Needs depth ${Defs.ASCEND_MIN_DEPTH}+ this run (best: ${state.maxDepthRun}).",
                    if (gain > 0) "Ascend\n+$gain relics" else "Locked",
                    gain > 0
                ) { confirmAscend(gain) },
                Row(
                    "Stats",
                    "Relics ${state.relics}  |  Ascensions ${state.ascensions}\nDeepest ever: ${state.maxDepthEver}  |  Lifetime ore: ${Fmt.num(state.lifetimeOre)}",
                    "",
                    false
                ) {}
            )
        }
        Tab.RELICS -> {
            val info = Row(
                "Relic Treasury",
                "Available: ${state.relics}  |  Lifetime earned: ${state.totalRelicsEarned}\nRelics spent on permanent upgrades do not reduce ascension damage.",
                "", false
            ) {}
            listOf(info) + Defs.relicUpgrades.indices.map { i ->
                val def = Defs.relicUpgrades[i]
                val level = state.relicUpgradeLevels[i]
                val capped = level >= def.maxLevel
                val price = state.relicUpgradeCost(i)
                Row(
                    "${def.name} Lv $level/${def.maxLevel}",
                    def.desc,
                    if (capped) "MAX" else "Buy\n$price relics",
                    !capped && state.relics >= price
                ) {
                    if (state.buyRelicUpgrade(i)) {
                        save()
                        refreshUi(true)
                    }
                }
            }
        }
        Tab.TROPHIES -> {
            listOf(Row(
                "Trophy Collection",
                "${state.trophyCount()}/${Defs.trophies.size} unlocked. Each completed trophy permanently adds +2% global damage.",
                "", false
            ) {}) + Defs.trophies.indices.map { i ->
                val def = Defs.trophies[i]
                val unlocked = state.trophyCompleted(i)
                Row(
                    (if (unlocked) "Unlocked: " else "Locked: ") + def.name,
                    def.desc,
                    "", false
                ) {}
            }
        }
        Tab.EXTRAS -> listOf(
            Row(
                "Biome gallery",
                if (biomePreviewIndex < 0) {
                    "Preview all five animated environments without changing the mining depth or save."
                } else {
                    "Previewing ${BiomeArt.forDepth(biomePreviewDepths[biomePreviewIndex]).title}. Tap NEXT to cycle."
                },
                if (biomePreviewIndex < 0) "PREVIEW" else "NEXT",
                true
            ) {
                biomePreviewIndex = (biomePreviewIndex + 1) % biomePreviewDepths.size
                binding.gameView.previewDepth = biomePreviewDepths[biomePreviewIndex]
                refreshUi(true)
            },
            Row(
                "Return to live mining",
                "Stop previewing and resume real mining at depth ${state.depth}.",
                "LIVE", biomePreviewIndex >= 0
            ) {
                biomePreviewIndex = -1
                binding.gameView.previewDepth = null
                refreshUi(true)
            },
            Row(
                "Echo & Guardians",
                "Echo shards: ${state.echoShards} (+1% global mining each, up to 200).\nGuardian cores: ${state.guardianCores} (+1.5% global mining each, up to 100).",
                "", false
            ) {},
            Row(
                "Number notation",
                "Switch between abbreviated values (12.34K) and scientific notation (1.23e4).",
                if (scientificNotation) "SCIENTIFIC" else "COMPACT",
                true
            ) { toggleNotation() },
            Row(
                "Daily Reward",
                if (state.canClaimDaily()) {
                    "Day ${state.nextDailyStreak()} / 7 • ${Fmt.num(state.dailyRewardAmount())} ore. One claim per local calendar day."
                } else {
                    "Collected today • streak ${state.dailyStreak}/7 • ${state.dailyClaims} lifetime claims. Return tomorrow."
                },
                if (state.canClaimDaily()) "CLAIM\n${Fmt.num(state.dailyRewardAmount())}" else "COLLECTED",
                state.canClaimDaily()
            ) {
                if (state.claimDaily() > 0.0) {
                    save()
                    refreshUi(true)
                }
            },
            boostRow(
                BoostType.DOUBLE_ORE,
                "Double Ore",
                "2x ore earned from taps, mining and obelisk bounties for 15 minutes."
            ),
            boostRow(
                BoostType.OVERDRIVE,
                "Overdrive",
                "3x automatic miner speed for 10 minutes. Both boosts can run together."
            ),
            Row(
                "Vibration",
                "Haptic feedback when tapping the monolith. Saved across launches.",
                if (hapticsEnabled) "ON" else "OFF",
                true
            ) { toggleHaptics() },
            Row(
                "Sound",
                "Light sound effects for manual mining. Muted by default.",
                if (soundEnabled) "ON" else "OFF",
                true
            ) { toggleSound() },
            Row(
                "Reset progress",
                "Erase game progress only, with two confirmations. Settings are preserved.",
                "RESET", true
            ) { confirmResetSave() },
            Row(
                "Privacy policy",
                "Read how local saves and Android backup work.",
                "VIEW", true
            ) {
                val url = "https://mrfantest2.github.io/monolith-miner-legal/privacy-policy.html"
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                catch (_: ActivityNotFoundException) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Privacy policy")
                        .setMessage("Open in a browser: $url")
                        .setPositiveButton("OK", null)
                        .show()
                }
            },
            Row(
                "About",
                "Monolith Miner v${BuildConfig.VERSION_NAME} • Animated adventure build",
                "", false
            ) {}
        ) + if (BuildConfig.ADS_ENABLED) {
            listOf(
                Row(
                    "Sponsored ore cache",
                    if (rewardedCooldownRemaining() > 0L) {
                        "Reward claimed. Another optional ad becomes available later."
                    } else {
                        "Watch an optional ad for ${Fmt.num(state.rewardedOreAmount())} ore."
                    },
                    when {
                        rewardedCooldownRemaining() > 0L -> remainingClock(rewardedCooldownRemaining())
                        gameAds?.isRewardReady == true -> "WATCH AD"
                        gameAds?.canRetryReward == true -> "RETRY"
                        else -> "LOADING"
                    },
                    rewardedCooldownRemaining() == 0L &&
                        (gameAds?.isRewardReady == true || gameAds?.canRetryReward == true)
                ) {
                    if (gameAds?.canRetryReward == true) gameAds?.retryReward()
                    else watchRewardedOre()
                }
            ) + if (gameAds?.privacyOptionsRequired == true) {
                listOf(Row(
                    "Ad privacy choices",
                    "Update advertising consent preferences.",
                    "MANAGE", true
                ) { gameAds?.showPrivacyOptions() })
            } else emptyList()
        } else emptyList()
    }

    private fun max1(n: Int) = if (n < 1) 1 else n

    private fun confirmAscend(gain: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Ascend?")
            .setMessage("You will gain $gain relics and restart from depth 1. Every relic earned permanently boosts damage, even after shop purchases.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Ascend") { _, _ ->
                state.ascend()
                save()
                refreshUi(true)
            }
            .show()
    }

    // ----- Adapter -----

    private inner class RowAdapter : RecyclerView.Adapter<RowAdapter.VH>() {
        private var rows: List<Row> = emptyList()

        inner class VH(val b: ItemRowBinding) : RecyclerView.ViewHolder(b.root)

        fun submit(newRows: List<Row>, forceRebuild: Boolean) {
            val sameSize = newRows.size == rows.size
            rows = newRows
            if (forceRebuild || !sameSize) notifyDataSetChanged() else notifyItemRangeChanged(0, rows.size)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = rows[position]
            holder.b.rowTitle.text = r.title
            holder.b.rowSub.text = r.sub
            holder.b.rowButton.text = r.button
            holder.b.rowButton.visibility = if (r.button.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            holder.b.rowButton.isEnabled = r.enabled
            holder.b.rowButton.setOnClickListener { r.onClick() }
        }
    }

    companion object {
        private const val PREFS = "monolith_miner"
        private const val KEY = "save"
        private const val SETTINGS_PREFS = "monolith_miner_settings"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_SOUND = "sound"
        private const val KEY_NOTATION = "scientific"
        private const val KEY_REWARDED_AT = "rewarded_ad_last_ms"
        private const val REWARDED_COOLDOWN_MS = 30L * 60L * 1000L
    }
}
