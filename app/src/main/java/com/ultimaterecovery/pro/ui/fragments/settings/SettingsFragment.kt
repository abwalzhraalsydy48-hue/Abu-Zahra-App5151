package com.ultimaterecovery.pro.ui.fragments.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ultimaterecovery.pro.utils.storage.formatFileSize
import com.ultimaterecovery.pro.R
import com.ultimaterecovery.pro.databinding.FragmentSettingsBinding
import com.ultimaterecovery.pro.ui.viewmodel.AppLanguage
import com.ultimaterecovery.pro.ui.viewmodel.AppTheme
import com.ultimaterecovery.pro.ui.viewmodel.SecurityLevel
import com.ultimaterecovery.pro.ui.viewmodel.SettingsUiState
import com.ultimaterecovery.pro.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Settings fragment using a custom layout with Material 3 components
 * (equivalent to PreferenceFragmentCompat but with richer UI).
 *
 * Sections:
 * - Appearance (theme, language, dynamic color)
 * - Scanning defaults
 * - Recovery options
 * - Recycle bin configuration
 * - Backup settings
 * - Security
 * - About
 */
@AndroidEntryPoint
class SettingsFragment : Fragment() {

    // ──────────────────────────────────────────
    // ViewBinding
    // ──────────────────────────────────────────

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    // ──────────────────────────────────────────
    // ViewModel
    // ──────────────────────────────────────────

    private val viewModel: SettingsViewModel by viewModels()

    // ──────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupClickListeners()
        observeUiState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ──────────────────────────────────────────
    // Toolbar
    // ──────────────────────────────────────────

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setTitle(R.string.settings)
        }
    }

    // ──────────────────────────────────────────
    // Click listeners
    // ──────────────────────────────────────────

    private fun setupClickListeners() {
        // ── Appearance ──
        binding.itemTheme.setOnClickListener { showThemeDialog() }
        binding.itemLanguage.setOnClickListener { showLanguageDialog() }
        binding.switchDynamicColor.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setDynamicColor(isChecked)
        }

        // ── Scanning ──
        binding.switchAutoScan.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutoScanOnStart(isChecked)
        }
        binding.itemDefaultScanType.setOnClickListener { showDefaultScanTypeDialog() }
        binding.switchScanNotification.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setScanNotificationEnabled(isChecked)
        }

        // ── Recovery ──
        binding.switchAutoSaveResults.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutoSaveResults(isChecked)
        }
        binding.itemRecoveryPath.setOnClickListener { showRecoveryPathDialog() }
        binding.switchOverwrite.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setOverwriteExistingFiles(isChecked)
        }
        binding.switchVerifyIntegrity.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setVerifyIntegrity(isChecked)
        }

        // ── Recycle bin ──
        binding.itemAutoDeleteDays.setOnClickListener { showAutoDeleteDaysDialog() }
        binding.itemStorageLimit.setOnClickListener { showStorageLimitDialog() }
        binding.switchSecureDelete.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setSecureDeleteEnabled(isChecked)
        }
        binding.switchRecycleBinMonitoring.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setRecycleBinMonitoring(isChecked)
        }

        // ── Backup ──
        binding.switchAutoBackup.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutoBackupEnabled(isChecked)
        }
        binding.itemBackupFrequency.setOnClickListener { showBackupFrequencyDialog() }
        binding.switchBackupEncryption.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setBackupEncryptionEnabled(isChecked)
        }

        // ── Security ──
        binding.itemSecurityLevel.setOnClickListener { showSecurityLevelDialog() }
        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setBiometricEnabled(isChecked)
        }
        binding.switchLockOnSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setLockOnAppSwitch(isChecked)
        }

        // ── History ──
        binding.switchRecoveryHistory.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setRecoveryHistoryEnabled(isChecked)
        }
        binding.btnClearHistory.setOnClickListener { confirmClearHistory() }

        // ── About ──
        binding.itemPrivacyPolicy.setOnClickListener { openUrl("https://ultimaterecovery.pro/privacy") }
        binding.itemTerms.setOnClickListener { openUrl("https://ultimaterecovery.pro/terms") }
        binding.itemRateApp.setOnClickListener { openPlayStore() }
    }

    // ──────────────────────────────────────────
    // UI State observation
    // ──────────────────────────────────────────

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: SettingsUiState) {
        // ── Appearance ──
        binding.tvThemeValue.text = state.theme.name.lowercase().replaceFirstChar { it.uppercase() }
        binding.tvLanguageValue.text = state.language.displayName
        binding.switchDynamicColor.isChecked = state.dynamicColor

        // ── Scanning ──
        binding.switchAutoScan.isChecked = state.autoScanOnStart
        binding.tvDefaultScanTypeValue.text = state.defaultScanType
        binding.switchScanNotification.isChecked = state.scanNotificationEnabled

        // ── Recovery ──
        binding.switchAutoSaveResults.isChecked = state.autoSaveResults
        binding.tvRecoveryPathValue.text = state.defaultRecoveryPath.ifBlank {
            getString(R.string.default_path)
        }
        binding.switchOverwrite.isChecked = state.overwriteExistingFiles
        binding.switchVerifyIntegrity.isChecked = state.verifyIntegrityAfterRecovery

        // ── Recycle bin ──
        binding.tvAutoDeleteDaysValue.text = getString(R.string.days, state.recycleBinAutoDeleteDays)
        binding.tvStorageLimitValue.text = getString(R.string.mb, state.recycleBinStorageLimitMb)
        binding.switchSecureDelete.isChecked = state.secureDeleteEnabled
        binding.switchRecycleBinMonitoring.isChecked = state.recycleBinMonitoring

        // ── Backup ──
        binding.switchAutoBackup.isChecked = state.autoBackupEnabled
        binding.tvBackupFrequencyValue.text = state.backupFrequency
        binding.switchBackupEncryption.isChecked = state.backupEncryptionEnabled

        // ── Security ──
        binding.tvSecurityLevelValue.text = state.securityLevel.name.lowercase()
            .replaceFirstChar { it.uppercase() }
        binding.switchBiometric.isChecked = state.biometricEnabled
        binding.switchLockOnSwitch.isChecked = state.lockOnAppSwitch

        // ── History ──
        binding.switchRecoveryHistory.isChecked = state.recoveryHistoryEnabled
        binding.tvTotalRecoveryCount.text = state.totalRecoveryCount.toString()
        binding.tvTotalRecoverySize.text = formatFileSize(state.totalRecoverySize)
        binding.btnClearHistory.isEnabled = !state.isClearingHistory
        binding.progressClearHistory.visibility =
            if (state.isClearingHistory) View.VISIBLE else View.GONE

        // ── About ──
        binding.tvAppVersion.text = state.appVersion
        binding.tvBuildNumber.text = state.buildNumber

        // Messages
        state.successMessage?.let { msg ->
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            viewModel.clearSuccessMessage()
        }
        state.error?.let { error ->
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // ──────────────────────────────────────────
    // Dialogs
    // ──────────────────────────────────────────

    private fun showThemeDialog() {
        val themes = AppTheme.entries.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.theme)
            .setItems(themes) { _, which ->
                viewModel.setTheme(AppTheme.entries[which])
            }
            .show()
    }

    private fun showLanguageDialog() {
        val languages = AppLanguage.entries.map { it.displayName }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.language)
            .setItems(languages) { _, which ->
                viewModel.setLanguage(AppLanguage.entries[which])
            }
            .show()
    }

    private fun showDefaultScanTypeDialog() {
        val types = arrayOf("QUICK", "DEEP", "SIGNATURE", "RAW", "PARTITION")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.default_scan_type)
            .setItems(types) { _, which ->
                viewModel.setDefaultScanType(types[which])
            }
            .show()
    }

    private fun showRecoveryPathDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.path)
            setText(viewModel.uiState.value.defaultRecoveryPath)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.recovery_path)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                viewModel.setDefaultRecoveryPath(input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAutoDeleteDaysDialog() {
        val days = arrayOf("7", "14", "30", "60", "90")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.auto_delete_days)
            .setItems(days) { _, which ->
                viewModel.setRecycleBinAutoDeleteDays(days[which].toInt())
            }
            .show()
    }

    private fun showStorageLimitDialog() {
        val limits = arrayOf("100", "250", "500", "1000", "2000")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.storage_limit_mb)
            .setItems(limits) { _, which ->
                viewModel.setRecycleBinStorageLimit(limits[which].toLong())
            }
            .show()
    }

    private fun showBackupFrequencyDialog() {
        val frequencies = arrayOf("DAILY", "WEEKLY", "MONTHLY")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_frequency)
            .setItems(frequencies) { _, which ->
                viewModel.setBackupFrequency(frequencies[which])
            }
            .show()
    }

    private fun showSecurityLevelDialog() {
        val levels = SecurityLevel.entries.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.security_level)
            .setItems(levels) { _, which ->
                val level = SecurityLevel.entries[which]
                viewModel.setSecurityLevel(level)
                if (level == SecurityLevel.PIN || level == SecurityLevel.PIN_AND_BIOMETRIC) {
                    showPinSetupDialog()
                }
            }
            .show()
    }

    private fun showPinSetupDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.enter_pin)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.set_pin)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val pin = input.text.toString()
                if (pin.length in 4..6) {
                    requireContext().getSharedPreferences("app_settings", 0)
                        .edit().putString("app_pin", pin).apply()
                } else {
                    Toast.makeText(requireContext(), R.string.pin_length_error, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmClearHistory() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clear_history_title)
            .setMessage(R.string.clear_history_message)
            .setPositiveButton(R.string.clear) { _, _ ->
                viewModel.clearRecoveryHistory()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ──────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun openPlayStore() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${requireContext().packageName}")))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${requireContext().packageName}")))
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }
}
