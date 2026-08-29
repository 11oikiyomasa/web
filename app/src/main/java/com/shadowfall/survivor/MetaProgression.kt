package com.shadowfall.survivor

import android.content.Context
import kotlin.math.max

object MetaProgression {
    private const val PREFS = "shadowfall"
    private const val SAVE_VERSION = 1
    private const val DEFAULT_SOULS = 0
    private const val MAX_LEVEL = 20

    enum class Upgrade(val key: String) {
        VITALITY("meta_vitality"),
        MIGHT("meta_might"),
        SWIFTNESS("meta_swiftness")
    }

    data class Stats(val maxHp: Float, val attack: Float, val moveSpeed: Float)

    private lateinit var prefs: android.content.SharedPreferences
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt("saveVersion", 0) < SAVE_VERSION) {
            prefs.edit().putInt("saveVersion", SAVE_VERSION).apply()
        }
        initialized = true
    }

    private fun requirePrefs(): android.content.SharedPreferences {
        check(initialized) { "MetaProgression.init must be called first" }
        return prefs
    }

    private fun upgradeLevel(upgrade: Upgrade): Int =
        requirePrefs().getInt(upgrade.key, 0).coerceIn(0, MAX_LEVEL)

    fun souls(): Int = requirePrefs().getInt("souls", DEFAULT_SOULS).coerceAtLeast(0)

    fun level(upgrade: Upgrade): Int = upgradeLevel(upgrade)

    fun cost(upgrade: Upgrade): Int = 20 + upgradeLevel(upgrade) * 15

    fun canPurchase(upgrade: Upgrade): Boolean =
        upgradeLevel(upgrade) < MAX_LEVEL && souls() >= cost(upgrade)

    fun purchase(upgrade: Upgrade): Boolean {
        val currentLevel = upgradeLevel(upgrade)
        val price = cost(upgrade)
        if (currentLevel >= MAX_LEVEL || souls() < price) return false
        requirePrefs().edit()
            .putInt("souls", souls() - price)
            .putInt(upgrade.key, currentLevel + 1)
            .putInt("saveVersion", SAVE_VERSION)
            .apply()
        return true
    }

    fun startingStats(): Stats = Stats(
        maxHp = 100f + upgradeLevel(Upgrade.VITALITY) * 10f,
        attack = 18f + upgradeLevel(Upgrade.MIGHT) * 2f,
        moveSpeed = 260f + upgradeLevel(Upgrade.SWIFTNESS) * 8f
    )

    fun awardRun(roomsCleared: Int, kills: Int, won: Boolean): Int {
        val safeRooms = roomsCleared.coerceAtLeast(0)
        val safeKills = kills.coerceAtLeast(0)
        val reward = safeRooms * 8 + safeKills * 2 + if (won) 60 else 0
        if (reward <= 0) return 0
        val newSouls = max(0, souls()) + reward
        requirePrefs().edit()
            .putInt("souls", newSouls)
            .putInt("saveVersion", SAVE_VERSION)
            .apply()
        return reward
    }
}
