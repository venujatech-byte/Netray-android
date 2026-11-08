package com.venujatech.netray.whitelist

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow

internal interface AppWhitelistStorage {
    fun load(): AppWhitelist
    fun save(value: AppWhitelist)
}

private class SharedPreferencesWhitelistStorage(private val preferences: SharedPreferences) : AppWhitelistStorage {
    override fun load(): AppWhitelist {
        val stored = preferences.getString(KEY, null) ?: return AppWhitelist()
        return runCatching {
            val json = JSONObject(stored)
            val invalidRules = mutableListOf<String>()
            val rules = mutableListOf<WhitelistRule>()
            val ruleRows = json.optJSONArray("rules") ?: JSONArray()
            for (index in 0 until ruleRows.length()) {
                val raw = ruleRows.optString(index)
                runCatching { WhitelistRuleParser.parse(raw) }
                    .onSuccess { rule ->
                        if (rules.none { WhitelistRuleParser.overlaps(it, rule) }) rules.add(rule)
                        else invalidRules += raw
                    }
                    .onFailure { invalidRules += raw }
            }
            val apps = buildList {
                val rows = json.optJSONArray("apps") ?: JSONArray()
                for (index in 0 until rows.length()) {
                    val packageName = rows.optString(index)
                    if (validPackageName(packageName) && packageName !in this) add(packageName)
                }
            }
            val invalidApps = (json.optJSONArray("apps") ?: JSONArray()).let { rows ->
                (0 until rows.length()).any { !validPackageName(rows.optString(it)) }
            }
            AppWhitelist(rules, apps, when {
                invalidRules.isNotEmpty() || invalidApps -> "Some saved whitelist entries were invalid and were skipped."
                else -> null
            })
        }.getOrElse { AppWhitelist(loadError = "Saved whitelist data could not be read. Review it before replacing it.") }
    }

    override fun save(value: AppWhitelist) {
        val json = JSONObject()
            .put("rules", JSONArray(value.rules.map(WhitelistRule::value)))
            .put("apps", JSONArray(value.appPackages))
        preferences.edit().putString(KEY, json.toString()).apply()
    }

    companion object { private const val KEY = "app_whitelist_v1" }
}

class AppWhitelistRepository internal constructor(private val storage: AppWhitelistStorage) {
    private val lock = Any()
    private val mutableWhitelist = MutableStateFlow(storage.load())
    val whitelist: StateFlow<AppWhitelist> = mutableWhitelist.asStateFlow()

    fun addRule(raw: String): Boolean {
        val rule = WhitelistRuleParser.parse(raw)
        synchronized(lock) {
            val current = mutableWhitelist.value
            require(current.rules.none { WhitelistRuleParser.overlaps(it, rule) }) {
                "This entry is already covered by a whitelist rule."
            }
            save(current.copy(rules = current.rules + rule))
        }
        return true
    }

    fun removeRule(rule: WhitelistRule) = update { it.copy(rules = it.rules.filterNot { saved -> saved.matcher == rule.matcher }) }

    fun setApps(packageNames: Collection<String>) {
        val normalized = packageNames.map(String::trim).filter(::validPackageName).distinct().sorted()
        update { it.copy(appPackages = normalized) }
    }

    fun removeApp(packageName: String) = update { it.copy(appPackages = it.appPackages - packageName) }

    private fun update(transform: (AppWhitelist) -> AppWhitelist) = synchronized(lock) {
        save(transform(mutableWhitelist.value))
    }

    private fun save(value: AppWhitelist) {
        storage.save(value.copy(loadError = null))
        mutableWhitelist.value = value.copy(loadError = null)
    }

    companion object {
        @Volatile private var instance: AppWhitelistRepository? = null

        fun get(context: Context): AppWhitelistRepository = instance ?: synchronized(this) {
            instance ?: AppWhitelistRepository(
                SharedPreferencesWhitelistStorage(context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE))
            ).also { instance = it }
        }
    }
}

internal fun validPackageName(value: String): Boolean =
    value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))
