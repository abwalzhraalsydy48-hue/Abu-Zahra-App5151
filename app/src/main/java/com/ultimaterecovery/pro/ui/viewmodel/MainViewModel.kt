package com.ultimaterecovery.pro.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ultimaterecovery.pro.data.local.entity.RecoveredFileEntity.FileCategory
import com.ultimaterecovery.pro.data.local.entity.ScanSessionEntity
import com.ultimaterecovery.pro.data.local.entity.ScanSessionEntity.ScanType
import com.ultimaterecovery.pro.data.repository.RecoveryHistoryRepository
import com.ultimaterecovery.pro.data.repository.RecoveredFileRepository
import com.ultimaterecovery.pro.data.repository.Resource
import com.ultimaterecovery.pro.data.repository.ScanSessionRepository
import com.ultimaterecovery.pro.engine.root.RootManager
import com.ultimaterecovery.pro.engine.root.RootState
import com.ultimaterecovery.pro.engine.scanner.IScanEngine
import com.ultimaterecovery.pro.manager.FileManager
import com.ultimaterecovery.pro.manager.FileManager.StorageInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

// ──────────────────────────────────────────────
// UI State
// ──────────────────────────────────────────────

/**
 * UI state for the main screen.
 *
 * Aggregates storage information, quick stats, root status,
 * and loading/error indicators into a single immutable snapshot
 * that the UI can observe.
 */
data class MainUiState(
    val storageInfo: List<StorageInfo> = emptyList(),
    val totalRecovered: Int = 0,
    val totalRecoveredSize: Long = 0L,
    val lastScanDate: Long? = null,
    val lastScanType: ScanType? = null,
    val categoryCounts: Map<FileCategory, Int> = emptyMap(),
    val rootState: RootState = RootState.Unknown,
    val isRootAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * One-shot navigation or notification events emitted from the ViewModel.
 *
 * Using [SharedFlow] ensures that configuration changes (e.g. rotation)
 * don't re-trigger navigation.
 */
sealed class MainNavigationEvent {
    data class NavigateToScan(val scanType: ScanType = ScanType.QUICK) : MainNavigationEvent()
    data class NavigateToRecovery(val category: FileCategory) : MainNavigationEvent()
    data object NavigateToDeepScan : MainNavigationEvent()
    data class ShowMessage(val message: String) : MainNavigationEvent()
}

// ──────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────

/**
 * ViewModel for the main/dashboard screen.
 *
 * Exposes [MainUiState] as a [StateFlow] and provides methods for:
 * - Loading storage info and quick stats
 * - Checking root access
 * - Triggering quick and deep scans
 * - Navigating to scan/recovery modules
 *
 * All long-running work is performed on [viewModelScope] with
 * IO-dispatched coroutines, and results are reduced into a single
 * [MainUiState] emission for deterministic UI rendering.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val recoveredFileRepository: RecoveredFileRepository,
    private val recoveryHistoryRepository: RecoveryHistoryRepository,
    private val scanSessionRepository: ScanSessionRepository,
    private val scanEngine: IScanEngine,
    private val rootManager: RootManager,
    private val fileManager: FileManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState(isLoading = true))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<MainNavigationEvent>()
    val navigationEvents: SharedFlow<MainNavigationEvent> = _navigationEvents.asSharedFlow()

    // ──────────────────────────────────────────
    // Initialization
    // ──────────────────────────────────────────

    init {
        loadStorageInfo()
        loadQuickStats()
        checkRootStatus()
        observeRootState()
        observeLastScan()
    }

    // ──────────────────────────────────────────
    // Storage
    // ──────────────────────────────────────────

    /**
     * Loads storage volume information (internal + SD card).
     */
    fun loadStorageInfo() {
        viewModelScope.launch {
            val volumes = fileManager.getStorageVolumes()
            _uiState.value = _uiState.value.copy(storageInfo = volumes)
        }
    }

    // ──────────────────────────────────────────
    // Quick Stats
    // ──────────────────────────────────────────

    /**
     * Loads aggregate recovery statistics: total count, total size,
     * per-category counts.
     */
    fun loadQuickStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Collect total recovered file stats
            recoveredFileRepository.getStats().collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        _uiState.value = _uiState.value.copy(
                            totalRecovered = resource.data.totalCount,
                            totalRecoveredSize = resource.data.totalSize,
                            isLoading = false
                        )
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            error = resource.message,
                            isLoading = false
                        )
                    }
                    is Resource.Loading -> { /* keep current loading state */ }
                }
            }
        }

        // Collect per-category file counts
        viewModelScope.launch {
            val categoryMap = mutableMapOf<FileCategory, Int>()
            for (category in FileCategory.entries) {
                recoveredFileRepository.getFilesByCategory(category).collect { resource ->
                    if (resource is Resource.Success) {
                        categoryMap[category] = resource.data.size
                        _uiState.value = _uiState.value.copy(
                            categoryCounts = categoryMap.toMap()
                        )
                    }
                }
            }
        }
    }

    // ──────────────────────────────────────────
    // Root Status
    // ──────────────────────────────────────────

    /**
     * Checks whether the device has root access.
     */
    fun checkRootStatus() {
        viewModelScope.launch {
            val result = rootManager.isRootAvailable()
            _uiState.value = _uiState.value.copy(
                isRootAvailable = result.isRooted
            )
        }
    }

    /**
     * Observes the reactive [RootManager.rootState] flow so the UI
     * automatically reflects changes when the user grants or revokes root.
     */
    private fun observeRootState() {
        viewModelScope.launch {
            rootManager.rootState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    rootState = state,
                    isRootAvailable = state is RootState.Granted || state is RootState.Available
                )
            }
        }
    }

    // ──────────────────────────────────────────
    // Last Scan
    // ──────────────────────────────────────────

    /**
     * Observes the most recently completed scan session to display
     * "Last scan" information on the dashboard.
     */
    private fun observeLastScan() {
        viewModelScope.launch {
            scanSessionRepository.getCompletedSessions().collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val lastSession = resource.data.maxByOrNull { it.endTime ?: 0L }
                        _uiState.value = _uiState.value.copy(
                            lastScanDate = lastSession?.endTime,
                            lastScanType = lastSession?.scanType
                        )
                    }
                    else -> { /* ignore */ }
                }
            }
        }
    }

    // ──────────────────────────────────────────
    // Scan triggers
    // ──────────────────────────────────────────

    /**
     * Starts a quick scan using the [IScanEngine].
     *
     * The scan runs on the default storage paths. On completion
     * the user is navigated to the results screen.
     */
    fun startQuickScan() {
        viewModelScope.launch {
            val paths = listOf(
                android.os.Environment.getExternalStorageDirectory().absolutePath
            )
            scanEngine.startQuickScan(paths).catch { e ->
                _navigationEvents.emit(
                    MainNavigationEvent.ShowMessage("Quick scan failed: ${e.message}")
                )
            }.collect { /* ScanEngine updates its own StateFlow; UI observes that */ }
            _navigationEvents.emit(MainNavigationEvent.NavigateToScan(ScanType.QUICK))
        }
    }

    /**
     * Starts a deep scan (requires root on most devices).
     */
    fun startDeepScan() {
        viewModelScope.launch {
            _navigationEvents.emit(MainNavigationEvent.NavigateToDeepScan)
        }
    }

    // ──────────────────────────────────────────
    // Navigation helpers
    // ──────────────────────────────────────────

    /**
     * Requests navigation to a specific recovery module.
     */
    fun navigateToRecovery(category: FileCategory) {
        viewModelScope.launch {
            _navigationEvents.emit(MainNavigationEvent.NavigateToRecovery(category))
        }
    }

    /**
     * Clears the current error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
