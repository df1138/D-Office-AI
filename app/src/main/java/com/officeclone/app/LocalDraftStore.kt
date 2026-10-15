package com.officeclone.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small, private local draft store. Drafts stay in app-private storage. */
object LocalDraftStore {
    private const val PREFS = "officeclone_local_drafts"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveText(context: Context, text: String) {
        prefs(context).edit().putString("word_draft", text).putLong("word_saved_at", System.currentTimeMillis()).apply()
    }
    fun loadText(context: Context): String? = prefs(context).getString("word_draft", null)

    fun saveWorkbook(context: Context, json: JSONObject) {
        prefs(context).edit().putString("sheet_draft", json.toString()).putLong("sheet_saved_at", System.currentTimeMillis()).apply()
    }
    fun loadWorkbook(context: Context): JSONObject? = runCatching {
        prefs(context).getString("sheet_draft", null)?.let(::JSONObject)
    }.getOrNull()

    fun addRecent(context: Context, name: String, format: String) {
        val p = prefs(context)
        val old = runCatching { JSONArray(p.getString("recent", "[]")) }.getOrDefault(JSONArray())
        val next = JSONArray()
        next.put(JSONObject().put("name", name.take(120)).put("format", format.take(12)).put("time", System.currentTimeMillis()))
        for (i in 0 until minOf(old.length(), 9)) {
            val item = old.optJSONObject(i) ?: continue
            if (item.optString("name") != name || item.optString("format") != format) next.put(item)
        }
        p.edit().putString("recent", next.toString()).apply()
    }
    fun recent(context: Context): List<Pair<String, String>> {
        val array = runCatching { JSONArray(prefs(context).getString("recent", "[]")) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { it.optString("name") to it.optString("format") } }
    }
}
