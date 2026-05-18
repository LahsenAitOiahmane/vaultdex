package com.vaultdex.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * VULN-HEALTH: Medical/health records stored in unencrypted database.
 *
 * Real-world vulnerability: Health and fitness apps store HIPAA-protected
 * information locally. Medical records, diagnoses, medications, and SSNs
 * in a plain SQLite database violate healthcare data protection regulations.
 */
@Entity(tableName = "health_records")
data class HealthRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val patientName: String,           // VULN: Patient PII
    val patientSsn: String,            // VULN: SSN stored in plaintext — CRITICAL
    val dateOfBirth: String,           // VULN: DOB
    val diagnosis: String,             // VULN: Medical diagnosis — HIPAA violation
    val medication: String,            // VULN: Medication details
    val doctorName: String,
    val insuranceId: String,           // VULN: Insurance ID number
    val recordDate: Long = System.currentTimeMillis()
)

@Dao
interface HealthRecordDao {
    @Query("SELECT * FROM health_records ORDER BY recordDate DESC")
    fun getAllRecords(): Flow<List<HealthRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: HealthRecordEntity)

    @Query("DELETE FROM health_records")
    suspend fun deleteAll()
}
