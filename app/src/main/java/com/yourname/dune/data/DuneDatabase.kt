package com.yourname.dune.data
import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface TrackedAppDao {
 @Query("SELECT * FROM tracked_apps ORDER BY lastUsedDays DESC") fun getAll(): Flow<List<TrackedAppEntity>>
 @Query("SELECT * FROM tracked_apps") suspend fun getSnapshot(): List<TrackedAppEntity>
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(app: TrackedAppEntity)
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(apps: List<TrackedAppEntity>)
 @Query("DELETE FROM tracked_apps WHERE packageName = :pkg") suspend fun delete(pkg: String)
}
@Database(entities = [TrackedAppEntity::class], version = 1, exportSchema = false)
abstract class DuneDatabase : RoomDatabase() {
 abstract fun trackedApps(): TrackedAppDao
 companion object {
  @Volatile private var instance: DuneDatabase? = null
  fun get(context: Context): DuneDatabase = instance ?: synchronized(this) {
   instance ?: Room.databaseBuilder(context.applicationContext, DuneDatabase::class.java, "dune.db").fallbackToDestructiveMigration().build().also { instance = it }
  }
 }
}
