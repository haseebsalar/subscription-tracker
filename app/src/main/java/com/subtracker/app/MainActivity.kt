package com.subtracker.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.PopupMenu
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.subtracker.app.databinding.ActivityMainBinding
import com.subtracker.app.databinding.SheetEditBinding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = SubAdapter { showEditSheet(it) }

    private var interstitial: InterstitialAd? = null
    private var saveCount = 0
    private var adsStarted = false
    private var sortIndex = 0

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.addButton.setOnClickListener { showEditSheet(null) }
        binding.statsButton.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.sortButton.setOnClickListener { showSortMenu() }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh()
        })

        setupConsentAndAds()
        Reminders.schedule(this)
        askNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun showSortMenu() {
        val menu = PopupMenu(this, binding.sortButton)
        val labels = resources.getStringArray(R.array.sort_options)
        labels.forEachIndexed { i, label ->
            menu.menu.add(0, i, i, label).isChecked = (i == sortIndex)
        }
        menu.menu.setGroupCheckable(0, true, true)
        menu.setOnMenuItemClickListener {
            sortIndex = it.itemId
            refresh()
            true
        }
        menu.show()
    }

    private fun whenText(days: Int): String = when (days) {
        0 -> getString(R.string.renews_today)
        1 -> getString(R.string.renews_tomorrow)
        else -> getString(R.string.renews_in_days, days)
    }

    private fun refresh() {
        val all = Store.load(this)
        val q = binding.searchInput.text.toString().trim()
        val filtered = all.filter {
            q.isEmpty() || it.name.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true)
        }

        val comparator: Comparator<Subscription> = when (sortIndex) {
            1 -> compareByDescending<Subscription> { it.monthlyCost() }
            2 -> compareBy<Subscription> { it.name.lowercase() }
            else -> compareBy<Subscription> { it.nextRenewal() }
        }
        val sorted = filtered.sortedWith(compareBy<Subscription> { it.status.ordinal }.then(comparator))
        adapter.submit(sorted)

        // Summary card
        val billing = all.filter { it.status == Status.ACTIVE && !it.trial }
        val monthly = billing.sumOf { it.monthlyCost() }
        binding.heroAmount.text = Prefs.money(this, monthly)
        binding.heroSub.text = getString(
            R.string.hero_sub, Prefs.money(this, monthly * 12), billing.size
        )

        val next = all
            .filter { it.status == Status.ACTIVE && it.daysLeft() >= 0 }
            .minByOrNull { it.nextRenewal() }
        binding.heroNext.text = if (next != null) {
            getString(R.string.hero_next, next.name, whenText(next.daysLeft()))
        } else {
            getString(R.string.hero_none)
        }

        val cancelled = all.filter { it.status == Status.CANCELLED }
        if (cancelled.isNotEmpty()) {
            binding.heroSaved.visibility = View.VISIBLE
            binding.heroSaved.text = getString(
                R.string.hero_saved,
                Prefs.money(this, cancelled.sumOf { it.monthlyCost() }),
                cancelled.size
            )
        } else {
            binding.heroSaved.visibility = View.GONE
        }

        // Empty states
        if (sorted.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            if (all.isEmpty()) {
                binding.emptyTitle.setText(R.string.empty_title)
                binding.emptyBody.setText(R.string.empty_body)
            } else {
                binding.emptyTitle.setText(R.string.no_results_title)
                binding.emptyBody.setText(R.string.no_results_body)
            }
        } else {
            binding.emptyView.visibility = View.GONE
        }
    }

    private fun showEditSheet(existing: Subscription?) {
        val d = SheetEditBinding.inflate(layoutInflater)
        var date: LocalDate = existing?.renewalDate ?: LocalDate.now().plusMonths(1)
        var categoryIndex = existing?.let { Categories.all.indexOf(it.category).coerceAtLeast(0) }
            ?: 0
        var statusIndex = existing?.status?.ordinal ?: 0
        val statusLabels = resources.getStringArray(R.array.status_options)

        d.sheetTitle.setText(if (existing == null) R.string.add_subscription else R.string.edit_subscription)
        d.priceLayout.prefixText = Prefs.currency(this).symbol + " "

        d.categoryInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, Categories.all)
        )
        d.categoryInput.setText(Categories.all[categoryIndex], false)
        d.categoryInput.setOnItemClickListener { _, _, pos, _ -> categoryIndex = pos }

        d.statusInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statusLabels)
        )
        d.statusInput.setText(statusLabels[statusIndex], false)
        d.statusInput.setOnItemClickListener { _, _, pos, _ -> statusIndex = pos }

        d.cycleToggle.check(R.id.btnMonthly)
        if (existing != null) {
            d.nameInput.setText(existing.name)
            d.priceInput.setText(existing.price.toString())
            d.cycleToggle.check(if (existing.yearly) R.id.btnYearly else R.id.btnMonthly)
            d.trialSwitch.isChecked = existing.trial
            d.cancelUrlInput.setText(existing.cancelUrl)
            d.deleteButton.visibility = View.VISIBLE
            if (existing.history.isNotEmpty()) {
                d.historyText.visibility = View.VISIBLE
                d.historyText.text = getString(R.string.price_history_title) + "\n" +
                    existing.history.joinToString("\n") {
                        "${it.date}:  ${Prefs.money(this, it.oldPrice)} → ${Prefs.money(this, it.newPrice)}"
                    }
            }
        }

        fun updateDateButton() {
            d.dateButton.text = getString(R.string.renewal_date_format, date.toString())
        }
        updateDateButton()
        d.dateButton.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.pick_date)
                .setSelection(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                updateDateButton()
            }
            picker.show(supportFragmentManager, "date")
        }
        d.cancelUrlLayout.setEndIconOnClickListener {
            openLink(d.cancelUrlInput.text.toString().trim())
        }

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(d.root)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        d.closeButton.setOnClickListener { dialog.dismiss() }
        d.deleteButton.setOnClickListener {
            if (existing != null) {
                dialog.dismiss()
                confirmDelete(existing)
            }
        }
        d.saveButton.setOnClickListener {
            val name = d.nameInput.text.toString().trim()
            val price = d.priceInput.text.toString().trim().toDoubleOrNull()
            d.nameLayout.error = null
            d.priceLayout.error = null
            if (name.isEmpty()) {
                d.nameLayout.error = getString(R.string.enter_name)
                return@setOnClickListener
            }
            if (price == null || price < 0) {
                d.priceLayout.error = getString(R.string.enter_price)
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
                yearly = d.cycleToggle.checkedButtonId == R.id.btnYearly,
                renewalDate = date,
                trial = d.trialSwitch.isChecked,
                category = Categories.all[categoryIndex],
                status = Status.values()[statusIndex],
                cancelUrl = d.cancelUrlInput.text.toString().trim(),
                history = history
            )
            Store.save(this, Store.load(this).filter { it.id != sub.id } + sub)
            dialog.dismiss()
            refresh()
            maybeShowInterstitial()
        }
        dialog.show()
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
