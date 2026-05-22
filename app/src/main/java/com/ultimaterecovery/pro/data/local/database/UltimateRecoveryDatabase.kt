package com.ultimaterecovery.pro.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ultimaterecovery.pro.data.local.converter.EnumTypeConverters
import com.ultimaterecovery.pro.data.local.dao.AccountDataDao
import com.ultimaterecovery.pro.data.local.dao.AppDataDao
import com.ultimaterecovery.pro.data.local.dao.BackupDao
import com.ultimaterecovery.pro.data.local.dao.CallLogDao
import com.ultimaterecovery.pro.data.local.dao.RecycleBinItemDao
import com.ultimaterecovery.pro.data.local.dao.RecoveredFileDao
import com.ultimaterecovery.pro.data.local.dao.RecoveryHistoryDao
import com.ultimaterecovery.pro.data.local.dao.ScanSessionDao
import com.ultimaterecovery.pro.data.local.dao.SmsMessageDao
import com.ultimaterecovery.pro.data.local.entity.AccountDataEntity
import com.ultimaterecovery.pro.data.local.entity.AppDataEntity
import com.ultimaterecovery.pro.data.local.entity.BackupEntity
import com.ultimaterecovery.pro.data.local.entity.CallLogEntity
import com.ultimaterecovery.pro.data.local.entity.RecycleBinItemEntity
import com.ultimaterecovery.pro.data.local.entity.RecoveredFileEntity
import com.ultimaterecovery.pro.data.local.entity.RecoveryHistoryEntity
import com.ultimaterecovery.pro.data.local.entity.ScanSessionEntity
import com.ultimaterecovery.pro.data.local.entity.SmsMessageEntity

/**
 * Room database for Ultimate Recovery Pro.
 *
 * Serves as the main access point for the underlying SQLite database,
 * managing all entity tables and exposing DAO instances for each
 * data domain.
 *
 * ## Migration Strategy
 * When schema changes are required:
 * 1. Increment [VERSION].
 * 2. Add a new [androidx.room.migration.Migration] in the companion
 *    object that migrates from the old version to the new one.
 * 3. Pass the migration to the `.addMigrations()` builder call inside
 *    [getDatabase].
 * 4. For destructive fallback during development, use
 *    `.fallbackToDestructiveMigration()` — **never** ship this to
 *    production as it will destroy user data.
 *
 * @see EnumTypeConverters for enum persistence logic
 */
@Database(
    entities = [
        RecoveredFileEntity::class,
        ScanSessionEntity::class,
        SmsMessageEntity::class,
        CallLogEntity::class,
        AppDataEntity::class,
        AccountDataEntity::class,
        BackupEntity::class,
        RecycleBinItemEntity::class,
        RecoveryHistoryEntity::class
    ],
    version = UltimateRecoveryDatabase.VERSION,
    exportSchema = true
)
@TypeConverters(EnumTypeConverters::class)
abstract class UltimateRecoveryDatabase : RoomDatabase() {

    // ──────────────────────────────────────────────
    // DAO accessors
    // ──────────────────────────────────────────────

    abstract fun recoveredFileDao(): RecoveredFileDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun smsMessageDao(): SmsMessageDao
    abstract fun callLogDao(): CallLogDao
    abstract fun appDataDao(): AppDataDao
    abstract fun accountDataDao(): AccountDataDao
    abstract fun backupDao(): BackupDao
    abstract fun recycleBinItemDao(): RecycleBinItemDao
    abstract fun recoveryHistoryDao(): RecoveryHistoryDao

    companion object {
        const val VERSION = 1

        @Volatile
        private var INSTANCE: UltimateRecoveryDatabase? = null

        /**
         * Returns the singleton [UltimateRecoveryDatabase] instance,
         * creating it on first access.
         *
         * Thread-safe via double-checked locking with [@Volatile] +
         * [@Synchronized].
         *
         * @param context Application or activity context used to build
         *   the Room database. Internally uses
         *   [Context.getApplicationContext] to avoid leaking activity
         *   references.
         */
        @JvmStatic
        @Synchronized
        fun getDatabase(context: Context): UltimateRecoveryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UltimateRecoveryDatabase::class.java,
                    "ultimate_recovery_db"
                )
                    .addCallback(DatabaseCallback(context.applicationContext))
                    // ── Migration strategy ──────────────────────────
                    // Add versioned migrations here, e.g.:
                    //   .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    //
                    // During development only:
                    //   .fallbackToDestructiveMigration()
                    // ────────────────────────────────────────────────
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /**
         * Forces the singleton to be recreated on the next call to
         * [getDatabase]. Intended **only** for use in tests.
         */
        @JvmStatic
        fun destroyInstance() {
            INSTANCE = null
        }
    }

    /**
     * Room callback invoked when the database is created for the first time.
     *
     * Use this to seed default data or perform one-time schema setup.
     * Runs on the database's background thread — do **not** launch
     * long-running operations here.
     */
    private class DatabaseCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {

        override fun onCreate(connection: androidx.sqlite.db.SupportSQLiteDatabase) {
            super.onCreate(connection)

            // ── Seed default data ──────────────────────────────
            // Example: insert a default recycle-bin auto-delete
            // threshold or placeholder scan session. Expand as
            // the product requires.
            //
            // connection.execSQL(
            //     "INSERT INTO scan_sessions (start_time, scan_type, status, storage_path) "
            //         + "VALUES (0, 0, 2, '/storage/emulated/0')"
            // )

            // ── Enable SQLite optimizations ────────────────────
            connection.execSQL("PRAGMA journal_mode=WAL")
            connection.execSQL("PRAGMA foreign_keys=ON")
        }

        override fun onOpen(connection: androidx.sqlite.db.SupportSQLiteDatabase) {
            super.onOpen(connection)
            // Re-enable constraints every time the DB is opened,
            // since SQLite does not persist PRAGMA settings.
            connection.execSQL("PRAGMA foreign_keys=ON")
        }
    }
}
