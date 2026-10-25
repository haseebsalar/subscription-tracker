package com.subtracker.app

import android.Manifest
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.subtracker.app.databinding.ActivityMainBinding
import com.subtracker.app.databinding.DialogEditBinding
import java.time.LocalDate

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = SubAdapter { showEditDialog(it) }

    private var interstitial: InterstitialAd? = null
    private var saveCount = 0
    private var adsStarted = false

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.addButton.setOnClickListener { showEditDialog(null) }
        binding.statsButton.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh()
        })
        binding.sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refresh()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        setupConsentAndAds()
        Reminders.schedule(this)
        askNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val all = Store.load(this)
        val q = binding.searchInput.text.toString().trim()
        val filtered = all.filter {
            q.isEmpty() || it.name.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true)
        }

        val comparator: Comparator<Subscription> = when (binding.sortSpinner.selectedItemPosition) {
            1 -> compareByDescending<Subscription> { it.monthlyCost() }
            2 -> compareBy<Subscription> { it.name.lowercase() }
            else -> compareBy<Subscription> { it.nextRenewal() }
        }
        val sorted = filtered.sortedWith(compareBy<Subscription> { it.status.ordinal }.then(comparator))
        adapter.submit(sorted)

        val active = all.filter { it.status == Status.ACTIVE && !it.trial }
        val monthly = active.sumOf { it.monthlyCost() }
        binding.summaryText.text = getString(
            R.string.summary_format,
            Prefs.money(this, monthly),
            Prefs.money(this, monthly * 12),
            active.size
        )

        val cancelled = all.filter { it.status == Status.CANCELLED }
        if (cancelled.isNotEmpty()) {
            binding.savedText.visibility = View.VISIBLE
            binding.savedText.text = getString(
                R.string.saved_format,
                Prefs.money(this, cancelled.sumOf { it.monthlyCost() }),
                cancelled.size
            )
        } else {
            binding.savedText.visibility = View.GONE
        }

        if (sorted.isEmpty()) {
            binding.emptyText.visibility = View.VISIBLE
            binding.emptyText.setText(if (all.isEmpty()) R.string.empty_message else R.string.no_results)
        } else {
            binding.emptyText.visibility = View.GONE
        }
    }

    private fun showEditDialog(existing: Subscription?) {
        val d = DialogEditBinding.inflate(layoutInflater)
        var date: LocalDate = existing?.renewalDate ?: LocalDate.now().plusMonths(1)

        d.categorySpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Categories.all)
        d.statusSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.status_options)
        )

        if (existing != null) {
            d.nameInput.setText(existing.name)
            d.priceInput.setText(existing.price.toString())
            d.radioYearly.isChecked = existing.yearly
            d.radioMonthly.isChecked = !existing.yearly
            d.trialCheck.isChecked = existing.trial
            d.categorySpinner.setSelection(Categories.all.indexOf(existing.category).coerceAtLeast(0))
            d.statusSpinner.setSelection(existing.status.ordinal)
            d.cancelUrlInput.setText(existing.cancelUrl)
            if (existing.history.isNotEmpty()) {
                d.historyText.visibility = View.VISIBLE
                d.historyText.text = getString(R.string.price_history_title) + "\n" +
                    existing.history.joinToString("\n") {
                        "${it.date}: ${Prefs.money(this, it.oldPrice)} → ${Prefs.money(this, it.newPrice)}"
                    }
            }
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
        d.openCancelButton.setOnClickListener {
            openLink(d.cancelUrlInput.text.toString().trim())
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.add_subscription else R.string.edit_subscription)
            .setView(d.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
        if (existing != null) {
            builder.setNeutralButton(R.string.delete) { _, _ -> confirmDelete(existing) }
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

            var history = existing?.history ?: emptyList()
            if (existing != null && existing.price != price) {
                history = history + PriceChange(LocalDate.now(), existing.price, price)
            }

            val sub = Subscription(
                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                name = name,
                price = price,
                yearly = d.radioYearly.isChecked,
                renewalDate = date,
                trial = d.trialCheck.isChecked,
                category = Categories.all[d.categorySpinner.selectedItemPosition],
                status = Status.values()[d.statusSpinner.selectedItemPosition],
                cancelUrl = d.cancelUrlInput.text.toString().trim(),
                history = history
            )
            val list = Store.load(this).filter { it.id != sub.id } + sub
            Store.save(this, list)
            dialog.dismiss()
            refresh()
            maybeShowInterstitial()
        }
    }

    private fun confirmDelete(s: Subscription) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(getString(R.string.delete_confirm_message, s.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                Store.save(this, Store.load(this).filter { it.id != s.id })
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openLink(raw: String) {
        if (raw.isEmpty()) {
            Toast.makeText(this, R.string.enter_cancel_link, Toast.LENGTH_SHORT).show()
            return
        }
        val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    /** Shows an interstitial after every second save, only at this natural break. */
    private fun maybeShowInterstitial() {
        saveCount++
        if (saveCount % 2 == 0) {
            interstitial?.show(this)
        }
    }

    /**
     * Asks for ad consent first where the law requires it (EU, UK and similar),
     * and only starts ads when consent allows it.
     */
    private fun setupConsentAndAds() {
        val info = UserMessagingPlatform.getConsentInformation(this)
        info.requestConsentInfoUpdate(
            this,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { _ ->
                    startAds(info)
                }
            },
            { _ -> startAds(info) }
        )
        // A returning user who already gave consent can see ads straight away.
        startAds(info)
    }

    private fun startAds(info: ConsentInformation) {
        if (!info.canRequestAds() || adsStarted) return
        adsStarted = true
        MobileAds.initialize(this) { }
        binding.adView.loadAd(AdRequest.Builder().build())
        loadInterstitial()
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
