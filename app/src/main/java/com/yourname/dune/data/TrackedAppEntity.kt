package com.yourname.dune.data
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "tracked_apps")
data class TrackedAppEntity(
 @PrimaryKey val packageName: String,
 val label: String,
 val lastUsedDays: Int,
 val state: String = "ACTIVE",
 val dustLevel: Float = 0f,
 val rescueCount: Int = 0,
 val buriedAt: Long = 0L,
 val wipeProgress: Float = 0f
)
