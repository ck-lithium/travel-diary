package app.traveldiary.data.diary

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The user's own data. Schema changes need a real migration (schemas are exported to app/schemas),
 * never a destructive one.
 *
 * Version 2 added [PlaceEntity.photo], version 3 the [HomePeriodEntity] table.
 */
@Database(
    entities = [PlaceEntity::class, VisitEntity::class, HomePeriodEntity::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
@TypeConverters(Converters::class)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        fun create(context: Context): DiaryDatabase =
            Room.databaseBuilder(context, DiaryDatabase::class.java, "diary.db").build()
    }
}
