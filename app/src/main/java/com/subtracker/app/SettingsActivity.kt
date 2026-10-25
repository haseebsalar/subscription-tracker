package com.subtracker.app

import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.subtracker.app.databinding.ActivitySettingsBinding
import java.util.Locale

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) writeBackup(uri)
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) readBackup(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.backButton.setOnClickListener { finish() }

        setupCurrency()
        setupReminders()
        setupLock()
        setupBackup()
        setupPrivacy()
    }

    private fun setupCurrency() {
        binding.currencySpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Prefs.currencies)
        val current = Prefs.currencies.indexOfFirst { it.code == Prefs.currency(this).code }
        binding.currencySpinner.setSelection(current.coerceAtLeast(0))
        binding.currencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                Prefs.setCurrency(this@SettingsActivity, Prefs.currencies[pos].code)
                RenewalsWidget.updateAll(this@SettingsActivity)
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupReminders() {
        fun updateTimeText() {
            binding.timeButton.text = getString(
                R.string.reminder_time_format,
                String.format(
                    Locale.US, "%02d:%02d", Prefs.reminderHour(this), Prefs.reminderMinute(this)
                )
            )
        }
        updateTimeText()
        binding.timeButton.setOnClickListener {
            TimePickerDialog(
                this,
                { _, h, m ->
                    Prefs.setReminderTime(this, h, m)
                    updateTimeText()
                },
                Prefs.reminderHour(this), Prefs.reminderMinute(this), true
            ).show()
        }

        val selected = Prefs.leadDays(this).toMutableSet()
        binding.leadContainer.removeAllViews()
        Prefs.leadDayOptions.forEach { d ->
            val box = CheckBox(this)
            box.text = when (d) {
                0 -> getString(R.string.lead_day_zero)
                1 -> getString(R.string.lead_day_one)
                else -> getString(R.string.lead_day_many, d)
            }
            box.isChecked = d in selected
            box.setOnCheckedChangeListener { _, checked ->
                if (checked) selected.add(d) else selected.remove(d)
                Prefs.setLeadDays(this, selected)
            }
            binding.leadContainer.addView(box)
        }

        binding.batteryButton.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupLock() {
        binding.lockSwitch.isChecked = Prefs.lockEnabled(this)
        binding.lockSwitch.setOnCheckedChangeListener { button, checked ->
            if (checked && !lockAvailable(this)) {
                button.isChecked = false
                Toast.makeText(this, R.string.app_lock_unavailable, Toast.LENGTH_LONG).show()
            } else {
                Prefs.setLockEnabled(this, checked)
                if (checked) markUnlocked()
            }
        }
    }

    private fun setupBackup() {
        binding.exportButton.setOnClickListener { exportLauncher.launch("subscriptions-backup.json") }
        binding.importButton.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
    }

    private fun setupPrivacy() {
        val info = UserMessagingPlatform.getConsentInformation(this)
        val required = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        binding.privacyButton.visibility = if (required) View.VISIBLE else View.GONE
        binding.privacyButton.setOnClickListener {
            UserMessagingPlatform.showPrivacyOptionsForm(this) { _ -> }
        }
    }

    private fun writeBackup(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(Backup.toJson(Store.load(this)).toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, R.string.backup_saved, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun readBackup(uri: Uri) {
        val imported = try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() } ?: throw IllegalStateException("empty")
            Backup.parse(text)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.import_title)
            .setMessage(getString(R.string.import_message, imported.size))
            .setPositiveButton(R.string.merge) { _, _ ->
                val byId = Store.load(this).associateBy { it.id }.toMutableMap()
                imported.forEach { byId[it.id] = it }
                Store.save(this, byId.values.toList())
                Toast.makeText(this, R.string.import_done, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.replace) { _, _ ->
                Store.save(this, imported)
                Toast.makeText(this, R.string.import_done, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }
}
