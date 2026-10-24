package com.subtracker.app

import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val price: Double,
    val yearly: Boolean,
    val renewalDate: LocalDate,
    val trial: Boolean
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
    }

    companion object {
        fun fromJson(o: JSONObject): Subscription = Subscription(
            id = o.getString("id"),
            name = o.getString("name"),
            price = o.getDouble("price"),
            yearly = o.getBoolean("yearly"),
            renewalDate = LocalDate.parse(o.getString("renewal")),
            trial = o.getBoolean("trial")
        )
    }
}
