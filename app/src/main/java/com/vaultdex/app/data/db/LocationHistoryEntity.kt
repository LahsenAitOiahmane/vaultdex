package com.vaultdex.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * VULN-LOC: Location history stored in unencrypted database.
 *
 * Real-world vulnerability: Fitness, ride-sharing, and mapping apps often track
 * detailed location history. Storing precise GPS coordinates and timestamps
 * in plaintext allows an attacker to build a complete profile of the user's
 * movements, home address, and daily routine.
 */
@Entity(tableName = "location_history")
data class LocationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val latitude: Double,              // VULN: Precise latitude
    val longitude: Double,             // VULN: Precise longitude
    val accuracy: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val addressStr: String             // VULN: Geocoded physical address in plaintext
)

@Dao
interface LocationHistoryDao {
    @Query("SELECT * FROM location_history ORDER BY timestamp DESC")
    fun getAllLocations(): Flow<List<LocationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationHistoryEntity)

    @Query("DELETE FROM location_history")
    suspend fun deleteAll()
}
