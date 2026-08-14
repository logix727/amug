package dev.logix.amug

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "mugs", indices = [Index(value = ["bleAddress"], unique = true)])
data class MugEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bleAddress: String,
    val name: String,
    val advertisedName: String,
    val createdAt: Long,
    val lastSeenAt: Long,
)

@Entity(
    tableName = "mug_preferences",
    foreignKeys = [ForeignKey(entity = MugEntity::class, parentColumns = ["id"], childColumns = ["mugId"], onDelete = ForeignKey.CASCADE)],
)
data class MugPreferencesEntity(
    @PrimaryKey val mugId: Long,
    val ambientTemperatureMode: Boolean = false,
    val ledPalette: String = "",
)

@Entity(
    tableName = "presets",
    foreignKeys = [ForeignKey(entity = MugEntity::class, parentColumns = ["id"], childColumns = ["mugId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mugId"), Index(value = ["mugId", "name"], unique = true)],
)
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mugId: Long,
    val name: String,
    val temperatureCentiC: Int,
    val approved: Boolean = false,
)

@Entity(
    tableName = "mug_sessions",
    foreignKeys = [ForeignKey(entity = MugEntity::class, parentColumns = ["id"], childColumns = ["mugId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mugId")],
)
data class MugSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mugId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val endReason: String? = null,
)

@Entity(
    tableName = "session_samples",
    foreignKeys = [ForeignKey(entity = MugSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class SessionSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val sampledAt: Long,
    val currentCentiC: Int,
    val targetCentiC: Int,
    val batteryPercent: Int?,
    val maintenanceEnabled: Boolean,
    val empty: Boolean,
    val charging: Boolean,
)

@Entity(
    tableName = "latest_snapshots",
    foreignKeys = [ForeignKey(entity = MugEntity::class, parentColumns = ["id"], childColumns = ["mugId"], onDelete = ForeignKey.CASCADE)],
)
data class LatestSnapshotEntity(
    @PrimaryKey val mugId: Long,
    val currentCentiC: Int,
    val targetCentiC: Int,
    val batteryPercent: Int?,
    val maintenanceEnabled: Boolean,
    val empty: Boolean,
    val charging: Boolean,
    val updatedAt: Long,
)

@Entity(
    tableName = "target_choices",
    foreignKeys = [ForeignKey(entity = MugEntity::class, parentColumns = ["id"], childColumns = ["mugId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mugId"), Index("chosenAt")],
)
data class TargetChoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mugId: Long,
    val targetCentiC: Int,
    val source: String,
    val presetName: String?,
    val chosenAt: Long,
    val localHour: Int,
)

@Dao
interface MugDao {
    @Query("SELECT * FROM mugs ORDER BY lastSeenAt DESC") fun observeAll(): Flow<List<MugEntity>>
    @Query("SELECT * FROM mugs WHERE id = :id") suspend fun byId(id: Long): MugEntity?
    @Query("SELECT * FROM mugs WHERE bleAddress = :address") suspend fun byAddress(address: String): MugEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(mug: MugEntity): Long
    @Update suspend fun update(mug: MugEntity)
    @Query("UPDATE mugs SET name = :name WHERE id = :id") suspend fun rename(id: Long, name: String)
    @Query("DELETE FROM mugs WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface MugPreferenceDao {
    @Query("SELECT * FROM mug_preferences WHERE mugId = :mugId") fun observe(mugId: Long): Flow<MugPreferencesEntity?>
    @Query("SELECT * FROM mug_preferences WHERE mugId = :mugId") suspend fun get(mugId: Long): MugPreferencesEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(preferences: MugPreferencesEntity)
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets WHERE mugId = :mugId ORDER BY temperatureCentiC, name") fun observe(mugId: Long): Flow<List<PresetEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(presets: List<PresetEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(preset: PresetEntity): Long
    @Query("DELETE FROM presets WHERE id = :id AND approved = 0") suspend fun delete(id: Long)
}

@Dao
interface MugSessionDao {
    @Insert suspend fun insert(session: MugSessionEntity): Long
    @Query("UPDATE mug_sessions SET endedAt = :endedAt, endReason = :reason WHERE id = :id AND endedAt IS NULL") suspend fun end(id: Long, endedAt: Long, reason: String)
    @Query("SELECT * FROM mug_sessions WHERE mugId = :mugId ORDER BY startedAt DESC") fun observe(mugId: Long): Flow<List<MugSessionEntity>>
    @Query("DELETE FROM mug_sessions WHERE mugId = :mugId") suspend fun clear(mugId: Long)
    @Query("UPDATE mug_sessions SET endedAt = :endedAt, endReason = :reason WHERE endedAt IS NULL") suspend fun closeAbandoned(endedAt: Long, reason: String)
    @Query("DELETE FROM mug_sessions WHERE COALESCE(endedAt, startedAt) < :before AND id NOT IN (SELECT DISTINCT sessionId FROM session_samples WHERE sampledAt >= :before)") suspend fun prune(before: Long)
}

@Dao
interface SessionSampleDao {
    @Insert suspend fun insert(sample: SessionSampleEntity)
    @Query("SELECT * FROM session_samples WHERE sessionId = :sessionId ORDER BY sampledAt") fun observe(sessionId: Long): Flow<List<SessionSampleEntity>>
}

@Dao
interface LatestSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(snapshot: LatestSnapshotEntity)
    @Query("SELECT * FROM latest_snapshots WHERE mugId = :mugId") fun observe(mugId: Long): Flow<LatestSnapshotEntity?>
    @Query("SELECT * FROM latest_snapshots WHERE mugId = :mugId") suspend fun get(mugId: Long): LatestSnapshotEntity?
}

@Dao
interface TargetChoiceDao {
    @Insert suspend fun insert(choice: TargetChoiceEntity)
    @Query("SELECT * FROM target_choices WHERE mugId = :mugId ORDER BY chosenAt DESC LIMIT :limit") fun observeRecent(mugId: Long, limit: Int = 100): Flow<List<TargetChoiceEntity>>
    @Query("DELETE FROM target_choices WHERE mugId = :mugId") suspend fun clear(mugId: Long)
}

@Database(
    entities = [MugEntity::class, MugPreferencesEntity::class, PresetEntity::class, MugSessionEntity::class, SessionSampleEntity::class, LatestSnapshotEntity::class, TargetChoiceEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class MugDatabase : RoomDatabase() {
    abstract fun mugs(): MugDao
    abstract fun mugPreferences(): MugPreferenceDao
    abstract fun presets(): PresetDao
    abstract fun sessions(): MugSessionDao
    abstract fun samples(): SessionSampleDao
    abstract fun snapshots(): LatestSnapshotDao
    abstract fun targetChoices(): TargetChoiceDao

    companion object {
        @Volatile private var instance: MugDatabase? = null
        fun get(context: Context): MugDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, MugDatabase::class.java, "amug.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `target_choices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mugId` INTEGER NOT NULL, `targetCentiC` INTEGER NOT NULL, `source` TEXT NOT NULL, `presetName` TEXT, `chosenAt` INTEGER NOT NULL, `localHour` INTEGER NOT NULL, FOREIGN KEY(`mugId`) REFERENCES `mugs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_target_choices_mugId` ON `target_choices` (`mugId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_target_choices_chosenAt` ON `target_choices` (`chosenAt`)")
            }
        }
    }
}
