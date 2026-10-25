package com.subtracker.app

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class Status { ACTIVE, PAUSED, CANCELLED }

object Categories {
    val all = listOf(
        "Entertainment", "Music", "Gaming", "Work", "Cloud & Storage",
        "Education", "Health", "Utilities", "Other"
    )

    private val colors = mapOf(
        "Entertainment" to "#E11D48",
        "Music" to "#7C3AED",
        "Gaming" to "#2563EB",
        "Work" to "#0891B2",
        "Cloud & Storage" to "#0D9488",
        "Education" to "#D97706",
        "Health" to "#16A34A",
        "Utilities" to "#475569"
    )

    fun color(category: String): Int = Color.parseColor(colors[category] ?: "#6B7280")
}

data class PriceChange(val date: LocalDate, val oldPrice: Double, val newPrice: Double)

data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val price: Double,
    val yearly: Boolean,
    val renewalDate: LocalDate,
    val trial: Boolean,
    val category: String = "Other",
    val status: Status = Status.ACTIVE,
    val cancelUrl: String = "",
    val history: List<PriceChange> = emptyList()
) {
    /** Next date the user will be charged. Trials never roll forward: they end once. */
    fun nextRenewal(today: LocalDate = LocalDate.now()): LocalDate {
        if (trial) return renewalDate
        var n = 0L
        while (true) {
            val candidate = if (yearly) renewalDate.plusYears(n) else renewalDate.plusMonths(n)
            if (!candidate.isBefore(today)) return candidate
            n++
        }
    }

    fun daysLeft(today: LocalDate = LocalDate.now()): Int =
        ChronoUnit.DAYS.between(today, nextRenewal(today)).toInt()

    fun monthlyCost(): Double = if (yearly) price / 12.0 else price

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("price", price)
        put("yearly", yearly)
        put("renewal", renewalDate.toString())
        put("trial", trial)
        put("category", category)
        put("status", status.name)
        put("cancelUrl", cancelUrl)
        val arr = JSONArray()
        history.forEach {
            arr.put(
                JSONObject()
                    .put("date", it.date.toString())
                    .put("old", it.oldPrice)
                    .put("new", it.newPrice)
            )
        }
        put("history", arr)
    }

    companion object {
        /** Reads both the old (v1.0) format and the new one. */
        fun fromJson(o: JSONObject): Subscription {
            val hist = mutableListOf<PriceChange>()
            val arr = o.optJSONArray("history")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val h = arr.getJSONObject(i)
                    hist.add(
                        PriceChange(
                            LocalDate.parse(h.getString("date")),
                            h.getDouble("old"),
                            h.getDouble("new")
                        )
                    )
                }
            }
            val status = try {
                Status.valueOf(o.optString("status", Status.ACTIVE.name))
            } catch (e: Exception) {
                Status.ACTIVE
            }
            return Subscription(
                id = o.getString("id"),
                name = o.getString("name"),
                price = o.getDouble("price"),
                yearly = o.getBoolean("yearly"),
                renewalDate = LocalDate.parse(o.getString("renewal")),
                trial = o.getBoolean("trial"),
                category = o.optString("category", "Other").ifEmpty { "Other" },
                status = status,
                cancelUrl = o.optString("cancelUrl", ""),
                history = hist
            )
        }
    }
}
