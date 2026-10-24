package com.subtracker.app

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.subtracker.app.databinding.ActivityMainBinding
import com.subtracker.app.databinding.DialogEditBinding
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = SubAdapter { showEditDialog(it) }

    private var interstitial: InterstitialAd? = null
    private var saveCount = 0

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.addButton.setOnClickListener { showEditDialog(null) }

        MobileAds.initialize(this)
        binding.adView.loadAd(AdRequest.Builder().build())
        loadInterstitial()

        scheduleReminders()
        askNotificationPermission()
        refresh()
    }

    private fun refresh() {
        val list = Store.load(this).sortedBy { it.nextRenewal() }
        adapter.submit(list)

        val active = list.filter { !it.trial }
        val monthly = active.sumOf { it.monthlyCost() }
        binding.summaryText.text = getString(
            R.string.summary_format,
            String.format(Locale.getDefault(), "%.2f", monthly),
            String.format(Locale.getDefault(), "%.2f", monthly * 12),
            active.size
        )
        binding.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showEditDialog(existing: Subscription?) {
        val d = DialogEditBinding.inflate(layoutInflater)
        var date: LocalDate = existing?.renewalDate ?: LocalDate.now().plusMonths(1)

        if (existing != null) {
            d.nameInput.setText(existing.name)
            d.priceInput.setText(existing.price.toString())
            d.radioYearly.isChecked = existing.yearly
            d.radioMonthly.isChecked = !existing.yearly
            d.trialCheck.isChecked = existing.trial
        } else {
            d.radioMonthly.isChecked = true
        }
        d.dateButton.text = getString(R.string.renewal_date_format, date.toString())
        d.dateButton.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, day ->
                    date = LocalDate.of(y, m + 1, day)
                    d.dateButton.text = getString(R.string.renewal_date_format, date.toString())
                },
                date.year, date.monthValue - 1, date.dayOfMonth
            ).show()
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.add_subscription else R.string.edit_subscription)
            .setView(d.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
        if (existing != null) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                val rest = Store.load(this).filter { it.id != existing.id }
                Store.save(this, rest)
                refresh()
            }
        }
        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = d.nameInput.text.toString().trim()
            val price = d.priceInput.text.toString().trim().toDoubleOrNull()
            if (name.isEmpty()) {
                Toast.makeText(this, R.string.enter_name, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (price == null || price < 0) {
                Toast.makeText(this, R.string.enter_price, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val sub = Subscription(
                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                name = name,
                price = price,
                yearly = d.radioYearly.isChecked,
                renewalDate = date,
                trial = d.trialCheck.isChecked
            )
            val list = Store.load(this).filter { it.id != sub.id } + sub
            Store.save(this, list)
            dialog.dismiss()
            refresh()
            maybeShowInterstitial()
        }
    }

    /** Shows an interstitial after every second save, only at this natural break. */
    private fun maybeShowInterstitial() {
        saveCount++
        if (saveCount % 2 == 0) {
            interstitial?.show(this)
        }
    }

    private fun loadInterstitial() {
        InterstitialAd.load(
            this,
            INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitial = null
                            loadInterstitial()
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                }
            }
        )
    }

    private fun scheduleReminders() {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "renewal-check", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        // Google's official TEST ad unit id. Replace with your real unit id before release.
        private const val INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    }
}
