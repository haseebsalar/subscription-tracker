package com.subtracker.app

import android.content.Context
import java.util.Currency
import java.util.Locale

data class CurrencyOption(val code: String, val symbol: String) {
    override fun toString(): String = "$code ($symbol)"
}

/** Small settings store. Everything stays on the device. */
object Prefs {
    private const val FILE = "settings"

    val currencies = listOf(
        CurrencyOption("PKR", "Rs"),
        CurrencyOption("USD", "$"),
        CurrencyOption("EUR", "€"),
        CurrencyOption("GBP", "£"),
        CurrencyOption("INR", "₹"),
        CurrencyOption("AED", "AED"),
        CurrencyOption("SAR", "SAR"),
        CurrencyOption("CAD", "C$"),
        CurrencyOption("AUD", "A$"),
        CurrencyOption("BDT", "৳"),
        CurrencyOption("TRY", "₺"),
        CurrencyOption("NGN", "₦"),
        CurrencyOption("EGP", "E£"),
        CurrencyOption("IDR", "Rp"),
        CurrencyOption("MYR", "RM")
    )

    /** Days before renewal on which a reminder can be sent (0 = on the day). */
    val leadDayOptions = listOf(7, 3, 2, 1, 0)

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun currency(c: Context): CurrencyOption {
        val saved = p(c).getString("currency", null)
        val code = saved ?: try {
            Currency.getInstance(Locale.getDefault()).currencyCode
        } catch (e: Exception) {
            "USD"
        }
        return currencies.firstOrNull { it.code == code }
            ?: currencies.first { it.code == "USD" }
    }

    fun setCurrency(c: Context, code: String) {
        p(c).edit().putString("currency", code).apply()
    }

    fun money(c: Context, value: Double): String =
        String.format(Locale.US, "%s %.2f", currency(c).symbol, value)

    fun reminderHour(c: Context): Int = p(c).getInt("hour", 9)
    fun reminderMinute(c: Context): Int = p(c).getInt("minute", 0)

    fun setReminderTime(c: Context, hour: Int, minute: Int) {
        p(c).edit().putInt("hour", hour).putInt("minute", minute).apply()
    }

    fun leadDays(c: Context): Set<Int> {
        val raw = p(c).getString("lead", "3,1,0") ?: "3,1,0"
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    fun setLeadDays(c: Context, days: Set<Int>) {
        p(c).edit().putString("lead", days.sortedDescending().joinToString(",")).apply()
    }

    fun lockEnabled(c: Context): Boolean = p(c).getBoolean("lock", false)

    fun setLockEnabled(c: Context, enabled: Boolean) {
        p(c).edit().putBoolean("lock", enabled).apply()
    }
}
