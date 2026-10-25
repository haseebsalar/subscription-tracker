package com.subtracker.app

import org.json.JSONArray
import org.json.JSONObject

/** Backup file format: a JSON file the user can keep anywhere (Drive, WhatsApp, email). */
object Backup {
    fun toJson(list: List<Subscription>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return JSONObject()
            .put("app", "subscription-tracker")
            .put("version", 2)
            .put("items", arr)
            .toString(2)
    }

    /** Throws if the text is not a valid backup. */
    fun parse(text: String): List<Subscription> {
        val arr = JSONObject(text).getJSONArray("items")
        return (0 until arr.length()).map { Subscription.fromJson(arr.getJSONObject(it)) }
    }
}
