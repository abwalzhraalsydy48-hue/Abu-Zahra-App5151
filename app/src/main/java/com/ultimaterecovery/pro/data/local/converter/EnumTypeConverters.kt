package com.ultimaterecovery.pro.data.local.converter

import androidx.room.TypeConverter
import com.ultimaterecovery.pro.data.local.entity.BackupEntity.BackupStatus
import com.ultimaterecovery.pro.data.local.entity.BackupEntity.BackupType
import com.ultimaterecovery.pro.data.local.entity.BackupEntity.CloudProvider
import com.ultimaterecovery.pro.data.local.entity.CallLogEntity.CallType
import com.ultimaterecovery.pro.data.local.entity.RecoveredFileEntity.FileCategory
import com.ultimaterecovery.pro.data.local.entity.RecoveredFileEntity.RecoveryStatus
import com.ultimaterecovery.pro.data.local.entity.ScanSessionEntity.ScanStatus
import com.ultimaterecovery.pro.data.local.entity.ScanSessionEntity.ScanType
import com.ultimaterecovery.pro.data.local.entity.SmsMessageEntity.SmsType
import com.ultimaterecovery.pro.data.local.entity.AppDataEntity.AppDataType

/**
 * Room TypeConverters for all enum types used across database entities.
 *
 * Room cannot persist enum values directly; these converters translate
 * enums to their ordinal representation for storage and back again
 * for deserialization.
 */
object EnumTypeConverters {

    // --- RecoveredFileEntity.FileCategory ---

    @JvmStatic
    @TypeConverter
    fun fromFileCategory(value: FileCategory?): Int? = value?.ordinal

    @JvmStatic
    @TypeConverter
    fun toFileCategory(ordinal: Int?): FileCategory? =
        ordinal?.let { FileCategory.entries.getOrNull(it) }

    // --- RecoveredFileEntity.RecoveryStatus ---

    @JvmStatic
    @TypeConverter
    fun fromRecoveryStatus(value: RecoveryStatus?): Int? = value?.ordinal

    @JvmStatic
    @TypeConverter
    fun toRecoveryStatus(ordinal: Int?): RecoveryStatus? =
        ordinal?.let { RecoveryStatus.entries.getOrNull(it) }

    // --- ScanSessionEntity.ScanType ---

    @JvmStatic
    @TypeConverter
    fun fromScanType(value: ScanType?): Int? = value?.ordinal

    @JvmStatic
    @TypeConverter
    fun toScanType(ordinal: Int?): ScanType? =
        ordinal?.let { ScanType.entries.getOrNull(it) }

    // --- ScanSessionEntity.ScanStatus ---

    @JvmStatic
    @TypeConverter
    fun fromScanStatus(value: ScanStatus?): Int? = value?.ordinal

    @JvmStatic
    @TypeConverter
    fun toScanStatus(ordinal: Int?): ScanStatus? =
        ordinal?.let { ScanStatus.entries.getOrNull(it) }

    // --- SmsMessageEntity.SmsType ---

    @JvmStatic
    @TypeConverter
    fun fromSmsType(value: SmsType?): Int? = value?.ordinal

    @JvmStatic
    @TypeConverter
    fun toSmsType(ordinal: Int?): SmsType? =
        ordinal?.let { SmsType.entries.getOrNull(it) }

    // --- CallLogEntity.CallType ---

    @JvmStatic
    @TypeConverter
    fun fromCallType(value: CallType?): Int? = value?.ordinal

    @JvmStatic
    @TypeConverter
    fun toCallType(ordinal: Int?): CallType? =
        ordinal?.let { CallType.entries.getOrNull(it) }

    // --- AppDataEntity.AppDataType ---

    @JvmStatic
    @TypeConverter
    fun fromAppDataType(value: AppDataType?): Int? = value?.ordinal

    @JvmStatic
    @TypeConverter
    fun toAppDataType(ordinal: Int?): AppDataType? =
        ordinal?.let { AppDataType.entries.getOrNull(it) }

    // --- BackupEntity.BackupType ---

    @JvmStatic
    @TypeConverter
    fun fromBackupType(value: BackupType?): Int? = value?.ordinal

    @JvmStatic
    @TypeConverter
    fun toBackupType(ordinal: Int?): BackupType? =
        ordinal?.let { BackupType.entries.getOrNull(it) }

    // --- BackupEntity.CloudProvider ---

    @JvmStatic
    @TypeConverter
    fun fromCloudProvider(value: CloudProvider?): Int? = value?.ordinal

    @JvmStatic
    @TypeConverter
    fun toCloudProvider(ordinal: Int?): CloudProvider? =
        ordinal?.let { CloudProvider.entries.getOrNull(it) }

    // --- BackupEntity.BackupStatus ---

    @JvmStatic
    @TypeConverter
    fun fromBackupStatus(value: BackupStatus?): Int? = value?.ordinal

    @JvmStatic
    @TypeConverter
    fun toBackupStatus(ordinal: Int?): BackupStatus? =
        ordinal?.let { BackupStatus.entries.getOrNull(it) }
}
