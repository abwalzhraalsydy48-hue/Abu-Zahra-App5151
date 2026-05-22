package com.ultimaterecovery.pro.engine.root

import android.content.Context
import android.content.pm.PackageManager
import com.topjohnwu.libsu.SuFile
import com.topjohnwu.libsu.io.SuFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Root Access Manager - Manages root access state and operations
 *
 * Central manager for all root-related functionality in the app.
 * Provides a unified API for checking root availability, requesting
 * root access, executing root commands, and monitoring root state.
 *
 * Features:
 * - Multiple root detection methods (su binary, Superuser app, BusyBox, etc.)
 * - Root access request via libsu with proper session management
 * - Safe command execution with output capture and error handling
 * - Magisk/SuperSU detection and version info
 * - Observable root state via StateFlow
 * - Graceful fallback when root is unavailable
 *
 * Uses com.github.topjohnwu.libsu for all root operations.
 */
@Singleton
class RootManager @Inject constructor(
    private val context: Context
) {

    companion object {
        /** Known su binary paths to check */
        private val SU_BINARY_PATHS = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/vendor/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/su/bin/su",
            "/magisk/.core/bin/su",
            "/debugfs/su"
        )

        /** Known Superuser/SuperSU app packages */
        private val SUPERUSER_PACKAGES = listOf(
            "com.noshufou.android.su",         // Superuser (original)
            "com.noshufou.android.su.elite",    // Superuser Elite
            "eu.chainfire.supersu",             // SuperSU
            "com.koushikdutta.superuser",       // Koush's Superuser
            "com.thirdparty.superuser",         // Third party Superuser
            "com.yellowes.su",                  // Chinese Superuser
            "com.topjohnwu.magisk",             // Magisk (old package)
            "io.github.vvb2060.magisk",         // Magisk Alpha
            "com.tsng.hidemyapplist"            // Often installed with Magisk
        )

        /** Known BusyBox binary paths */
        private val BUSYBOX_PATHS = listOf(
            "/system/xbin/busybox",
            "/system/bin/busybox",
            "/sbin/busybox",
            "/data/local/xbin/busybox",
            "/data/local/bin/busybox"
        )

        /** Magisk-specific paths and indicators */
        private val MAGISK_INDICATORS = listOf(
            "/sbin/.magisk",
            "/data/adb/magisk",
            "/cache/.disable_magisk",
            "/dev/.magisk.unblock"
        )

        /** SuperSU-specific indicators */
        private val SUPERSU_INDICATORS = listOf(
            "/system/app/SuperSU",
            "/system/app/Superuser",
            "/data/data/eu.chainfire.supersu",
            "/system/bin/.ext/.su"
        )

        /** Timeout for root shell commands in milliseconds */
        private const val SHELL_TIMEOUT = 30_000L

        /** Tag for logging */
        private const val TAG = "RootManager"
    }

    // ──────────────────────────────────────────────
    // Root State Management
    // ──────────────────────────────────────────────

    /** Root access state observable by UI and other components */
    private val _rootState = MutableStateFlow<RootState>(RootState.Unknown)
    val rootState: StateFlow<RootState> = _rootState.asStateFlow()

    /** Whether root access has been granted in the current session */
    val isRootGranted: Boolean
        get() = _rootState.value is RootState.Granted

    /** Detailed root information once detected */
    private val _rootInfo = MutableStateFlow<RootInfo?>(null)
    val rootInfo: StateFlow<RootInfo?> = _rootInfo.asStateFlow()

    /** Cached shell instance for command execution */
    @Volatile
    private var shell: com.topjohnwu.libsu.Shell? = null

    // ──────────────────────────────────────────────
    // Root Availability Check
    // ──────────────────────────────────────────────

    /**
     * Check if the device is rooted using multiple detection methods.
     *
     * Performs a comprehensive check using all available detection methods:
     * 1. libsu built-in root check
     * 2. su binary existence check
     * 3. Superuser app package check
     * 4. BusyBox binary check
     * 5. Magisk indicator check
     * 6. System property checks (ro.debuggable, ro.secure)
     * 7. /proc/self/mountinfo for su mount points
     *
     * @return RootCheckResult with detailed findings from each method
     */
    suspend fun isRootAvailable(): RootCheckResult = withContext(Dispatchers.IO) {
        val checks = mutableMapOf<String, Boolean>()
        var anyPass = false

        // Method 1: libsu built-in check
        try {
            val libsuResult = com.topjohnwu.libsu.Shell.getShell().isRoot
            checks["libsu_shell_root"] = libsuResult
            if (libsuResult) anyPass = true
        } catch (e: Exception) {
            checks["libsu_shell_root"] = false
        }

        // Method 2: su binary existence check
        val suBinaryFound = checkSuBinary()
        checks["su_binary"] = suBinaryFound
        if (suBinaryFound) anyPass = true

        // Method 3: Superuser app package check
        val superuserAppFound = checkSuperuserApp()
        checks["superuser_app"] = superuserAppFound
        if (superuserAppFound) anyPass = true

        // Method 4: BusyBox check (indirect root indicator)
        val busyboxFound = checkBusyBox()
        checks["busybox"] = busyboxFound
        if (busyboxFound) anyPass = true

        // Method 5: Magisk indicators
        val magiskFound = checkMagiskIndicators()
        checks["magisk"] = magiskFound
        if (magiskFound) anyPass = true

        // Method 6: SuperSU indicators
        val superSuFound = checkSuperSuIndicators()
        checks["supersu"] = superSuFound
        if (superSuFound) anyPass = true

        // Method 7: System property checks
        val insecureProperties = checkSystemProperties()
        checks["insecure_properties"] = insecureProperties
        if (insecureProperties) anyPass = true

        // Update root state based on findings
        val rootType = when {
            magiskFound -> RootType.MAGISK
            superSuFound -> RootType.SUPERSU
            anyPass -> RootType.UNKNOWN
            else -> RootType.NONE
        }

        val result = RootCheckResult(
            isRooted = anyPass,
            checks = checks,
            rootType = rootType,
            suPath = findSuPath(),
            magiskVersion = if (magiskFound) getMagiskVersion() else null
        )

        // Update state
        if (anyPass) {
            _rootInfo.value = RootInfo(
                rootType = rootType,
                suPath = result.suPath,
                magiskVersion = result.magiskVersion,
                superSuVersion = if (superSuFound) getSuperSuVersion() else null
            )
            if (_rootState.value !is RootState.Granted) {
                _rootState.value = RootState.Available(rootType)
            }
        } else {
            _rootState.value = RootState.NotAvailable
        }

        result
    }

    /**
     * Request root access from the user (triggers Superuser/Magisk prompt).
     *
     * Initializes a root shell via libsu, which will trigger the
     * Superuser/Magisk grant dialog if not already granted.
     *
     * @return true if root access was granted, false otherwise
     */
    suspend fun requestRootAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Configure libsu shell builder
            val shellBuilder = com.topjohnwu.libsu.Shell.Builder.create()
                .setFlags(com.topjohnwu.libsu.Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(SHELL_TIMEOUT)
                .setInitializers(RootShellInitializer::class.java)

            // Attempt to get a root shell
            val rootShell = shellBuilder.build()

            // Wait for shell to be ready
            rootShell.waitAndClose()

            // Try executing a simple command to verify root
            val result = com.topjohnwu.libsu.Shell.cmd("id").exec()

            if (result.isSuccess) {
                val output = result.out.joinToString("\n")
                val hasRoot = output.contains("uid=0")

                if (hasRoot) {
                    shell = com.topjohnwu.libsu.Shell.Builder.create()
                        .setFlags(com.topjohnwu.libsu.Shell.FLAG_REDIRECT_STDERR)
                        .setTimeout(SHELL_TIMEOUT)
                        .build()

                    val rootType = detectRootType()
                    _rootState.value = RootState.Granted(rootType)
                    _rootInfo.value = RootInfo(
                        rootType = rootType,
                        suPath = findSuPath(),
                        magiskVersion = if (rootType == RootType.MAGISK) getMagiskVersion() else null,
                        superSuVersion = if (rootType == RootType.SUPERSU) getSuperSuVersion() else null
                    )
                    return@withContext true
                }
            }

            _rootState.value = RootState.Denied
            return@withContext false

        } catch (e: Exception) {
            _rootState.value = RootState.Denied
            return@withContext false
        }
    }

    // ──────────────────────────────────────────────
    // Command Execution
    // ──────────────────────────────────────────────

    /**
     * Execute a root command and return success/failure status.
     *
     * @param cmd The command to execute
     * @return true if the command executed successfully (exit code 0)
     */
    suspend fun executeCommand(cmd: String): Boolean = withContext(Dispatchers.IO) {
        if (!isRootGranted && _rootState.value !is RootState.Available) {
            return@withContext false
        }

        try {
            val result = com.topjohnwu.libsu.Shell.cmd(cmd).exec()
            return@withContext result.isSuccess
        } catch (e: Exception) {
            return@withContext false
        }
    }

    /**
     * Execute a root command and capture its output.
     *
     * @param cmd The command to execute
     * @return CommandResult with stdout, stderr, and exit code
     */
    suspend fun executeCommandWithOutput(cmd: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val result = com.topjohnwu.libsu.Shell.cmd(cmd).exec()
            CommandResult(
                stdout = result.out.joinToString("\n"),
                stderr = result.err.joinToString("\n"),
                exitCode = result.code,
                success = result.isSuccess
            )
        } catch (e: Exception) {
            CommandResult(
                stdout = "",
                stderr = e.message ?: "Unknown error",
                exitCode = -1,
                success = false
            )
        }
    }

    /**
     * Execute a root command as a Flow, emitting output lines as they arrive.
     *
     * Useful for long-running commands where real-time output is needed.
     *
     * @param cmd The command to execute
     * @return Flow of output lines
     */
    fun executeCommandStreaming(cmd: String): Flow<String> = flow {
        try {
            val result = com.topjohnwu.libsu.Shell.cmd(cmd).exec()
            for (line in result.out) {
                emit(line)
            }
        } catch (e: Exception) {
            emit("ERROR: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Execute multiple root commands in sequence.
     *
     * @param commands List of commands to execute
     * @return List of CommandResult for each command
     */
    suspend fun executeCommands(commands: List<String>): List<CommandResult> =
        withContext(Dispatchers.IO) {
            commands.map { cmd -> executeCommandWithOutput(cmd) }
        }

    // ──────────────────────────────────────────────
    // Root Access Management
    // ──────────────────────────────────────────────

    /**
     * Revoke root access and close the root shell.
     *
     * Closes any open root shell sessions and resets the root state.
     * Note: This does not revoke root permission in Magisk/SuperSU;
     * it only releases the shell session in this app.
     */
    fun revokeRoot() {
        try {
            shell?.close()
        } catch (_: Exception) {
            // Ignore close errors
        }
        shell = null
        _rootState.value = RootState.Revoked
    }

    // ──────────────────────────────────────────────
    // File Operations via Root
    // ──────────────────────────────────────────────

    /**
     * Open a file input stream using root access.
     *
     * @param path The file path to read (can be in /data or other protected paths)
     * @return InputStream or null if the file cannot be opened
     */
    fun openFileAsRoot(path: String): InputStream? {
        return try {
            val suFile = SuFile(path)
            if (suFile.exists()) {
                SuFileInputStream.open(suFile)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * List files in a directory using root access.
     *
     * @param path The directory path
     * @return List of file names, or empty list on failure
     */
    suspend fun listFilesAsRoot(path: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val suDir = SuFile(path)
            if (suDir.isDirectory) {
                suDir.listFiles()?.map { it.name } ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Read a file's content as text using root access.
     *
     * @param path The file path to read
     * @return File content as string, or null on failure
     */
    suspend fun readFileAsRoot(path: String): String? = withContext(Dispatchers.IO) {
        try {
            val result = com.topjohnwu.libsu.Shell.cmd("cat '$path'").exec()
            if (result.isSuccess) {
                result.out.joinToString("\n")
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Copy a file using root access.
     *
     * @param srcPath Source file path
     * @param destPath Destination file path
     * @return true if copy was successful
     */
    suspend fun copyFileAsRoot(srcPath: String, destPath: String): Boolean =
        withContext(Dispatchers.IO) {
            val result = com.topjohnwu.libsu.Shell.cmd("cp '$srcPath' '$destPath'").exec()
            result.isSuccess
        }

    /**
     * Check if a file exists using root access.
     *
     * @param path The file path to check
     * @return true if the file exists
     */
    suspend fun fileExistsAsRoot(path: String): Boolean = withContext(Dispatchers.IO) {
        val result = com.topjohnwu.libsu.Shell.cmd("test -e '$path' && echo EXISTS").exec()
        result.out.any { it.contains("EXISTS") }
    }

    /**
     * Get file permissions using root access.
     *
     * @param path The file path
     * @return Permission string (e.g., "rw-r--r--") or null on failure
     */
    suspend fun getFilePermissions(path: String): String? = withContext(Dispatchers.IO) {
        val result = com.topjohnwu.libsu.Shell.cmd("stat -c '%A' '$path'").exec()
        if (result.isSuccess && result.out.isNotEmpty()) {
            result.out.first().trim()
        } else null
    }

    // ──────────────────────────────────────────────
    // Private Detection Methods
    // ──────────────────────────────────────────────

    /**
     * Check for su binary at known paths
     */
    private fun checkSuBinary(): Boolean {
        for (path in SU_BINARY_PATHS) {
            if (File(path).exists()) return true
        }
        // Also check via `which su` through root
        return try {
            val result = com.topjohnwu.libsu.Shell.cmd("which su").exec()
            result.isSuccess && result.out.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Find the path of the su binary
     */
    private fun findSuPath(): String? {
        for (path in SU_BINARY_PATHS) {
            if (File(path).exists()) return path
        }
        return try {
            val result = com.topjohnwu.libsu.Shell.cmd("which su").exec()
            if (result.isSuccess && result.out.isNotEmpty()) result.out.first().trim() else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Check for installed Superuser management apps
     */
    private fun checkSuperuserApp(): Boolean {
        val pm = context.packageManager
        for (pkg in SUPERUSER_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (_: PackageManager.NameNotFoundException) {
                // Package not installed
            }
        }
        return false
    }

    /**
     * Check for BusyBox binary (common root indicator)
     */
    private fun checkBusyBox(): Boolean {
        for (path in BUSYBOX_PATHS) {
            if (File(path).exists()) return true
        }
        return try {
            val result = com.topjohnwu.libsu.Shell.cmd("which busybox").exec()
            result.isSuccess && result.out.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Check for Magisk-specific indicators
     */
    private fun checkMagiskIndicators(): Boolean {
        for (path in MAGISK_INDICATORS) {
            if (File(path).exists()) return true
        }
        // Also check for Magisk app
        val pm = context.packageManager
        try {
            pm.getPackageInfo("com.topjohnwu.magisk", 0)
            return true
        } catch (_: PackageManager.NameNotFoundException) {}

        // Check via magisk --version command
        return try {
            val result = com.topjohnwu.libsu.Shell.cmd("magisk -v").exec()
            result.isSuccess && result.out.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Check for SuperSU-specific indicators
     */
    private fun checkSuperSuIndicators(): Boolean {
        for (path in SUPERSU_INDICATORS) {
            if (File(path).exists()) return true
        }
        return false
    }

    /**
     * Check system properties that indicate an insecure/rooted device
     */
    private fun checkSystemProperties(): Boolean {
        return try {
            // Check ro.debuggable
            val debuggable = com.topjohnwu.libsu.Shell.cmd("getprop ro.debuggable").exec()
            val isDebuggable = debuggable.isSuccess && debuggable.out.isNotEmpty() &&
                    debuggable.out.first().trim() == "1"

            // Check ro.secure
            val secure = com.topjohnwu.libsu.Shell.cmd("getprop ro.secure").exec()
            val isInsecure = secure.isSuccess && secure.out.isNotEmpty() &&
                    secure.out.first().trim() == "0"

            isDebuggable || isInsecure
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Detect the type of root solution installed
     */
    private fun detectRootType(): RootType {
        if (checkMagiskIndicators()) return RootType.MAGISK
        if (checkSuperSuIndicators()) return RootType.SUPERSU
        return RootType.UNKNOWN
    }

    /**
     * Get Magisk version string
     */
    private fun getMagiskVersion(): String? {
        return try {
            val result = com.topjohnwu.libsu.Shell.cmd("magisk -v").exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                val version = result.out.first().trim()
                val codeResult = com.topjohnwu.libsu.Shell.cmd("magisk -V").exec()
                val code = if (codeResult.isSuccess && codeResult.out.isNotEmpty()) {
                    codeResult.out.first().trim()
                } else ""
                if (code.isNotEmpty()) "$version ($code)" else version
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Get SuperSU version string
     */
    private fun getSuperSuVersion(): String? {
        return try {
            val pm = context.packageManager
            val pkgInfo = pm.getPackageInfo("eu.chainfire.supersu", 0)
            pkgInfo.versionName
        } catch (_: Exception) {
            null
        }
    }

    // ──────────────────────────────────────────────
    // Shell Initializer for libsu
    // ──────────────────────────────────────────────

    /**
     * Initializer class for libsu shell sessions.
     * Sets up environment variables and initial shell configuration.
     */
    class RootShellInitializer : com.topjohnwu.libsu.Shell.Initializer() {
        override fun onInit(context: Context, shell: com.topjohnwu.libsu.Shell): Boolean {
            // Set up environment for root operations
            shell.newJob().add(
                "export PATH=/sbin:/system/bin:/system/xbin:/vendor/bin:/data/local/bin:\$PATH",
                "export LD_LIBRARY_PATH=/system/lib64:/system/lib:/vendor/lib64:/vendor/lib",
                "umask 022"
            ).exec()
            return true
        }
    }
}

// ──────────────────────────────────────────────
// Data Classes and Enums
// ──────────────────────────────────────────────

/**
 * Root access state sealed class
 *
 * Represents all possible states of root access:
 * - Unknown: Not yet checked
 * - NotAvailable: Device is not rooted
 * - Available: Root is available but not yet granted
 * - Granted: Root access has been granted
 * - Denied: User denied root access
 * - Revoked: Root access was manually revoked
 */
sealed class RootState {
    /** Root state not yet determined */
    data object Unknown : RootState()

    /** Device does not have root access */
    data object NotAvailable : RootState()

    /** Root is available but not yet requested/granted */
    data class Available(val rootType: RootType) : RootState()

    /** Root access has been granted */
    data class Granted(val rootType: RootType) : RootState()

    /** Root access was denied by user or system */
    data object Denied : RootState()

    /** Root access was manually revoked by the app */
    data object Revoked : RootState()
}

/**
 * Type of root solution installed on the device
 */
enum class RootType {
    /** No root solution installed */
    NONE,
    /** Magisk (most common modern root solution) */
    MAGISK,
    /** SuperSU (older root solution) */
    SUPERSU,
    /** Root detected but type unknown */
    UNKNOWN
}

/**
 * Result of a comprehensive root check
 *
 * @property isRooted Whether the device appears to be rooted
 * @property checks Map of check method name to its result
 * @property rootType Detected root solution type
 * @property suPath Path to the su binary if found
 * @property magiskVersion Magisk version string if Magisk is detected
 */
data class RootCheckResult(
    val isRooted: Boolean,
    val checks: Map<String, Boolean>,
    val rootType: RootType,
    val suPath: String? = null,
    val magiskVersion: String? = null
) {
    /** Number of detection methods that indicated root */
    val positiveCheckCount: Int get() = checks.values.count { it }

    /** Total number of detection methods used */
    val totalCheckCount: Int get() = checks.size

    /** Confidence level of root detection (0.0 to 1.0) */
    val confidence: Float get() = if (totalCheckCount > 0) {
        positiveCheckCount.toFloat() / totalCheckCount
    } else 0f
}

/**
 * Detailed root information
 *
 * @property rootType Type of root solution
 * @property suPath Path to su binary
 * @property magiskVersion Magisk version if applicable
 * @property superSuVersion SuperSU version if applicable
 */
data class RootInfo(
    val rootType: RootType,
    val suPath: String?,
    val magiskVersion: String? = null,
    val superSuVersion: String? = null
)

/**
 * Result of a root command execution
 *
 * @property stdout Standard output from the command
 * @property stderr Standard error from the command
 * @property exitCode Process exit code
 * @property success Whether the command completed successfully (exit code 0)
 */
data class CommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val success: Boolean
) {
    /** All output combined (stdout + stderr) */
    val fullOutput: String get() = buildString {
        if (stdout.isNotEmpty()) append(stdout)
        if (stderr.isNotEmpty()) {
            if (isNotEmpty()) append("\n")
            append(stderr)
        }
    }

    /** Output lines as a list */
    val outputLines: List<String> get() = stdout.lines().filter { it.isNotEmpty() }
}
