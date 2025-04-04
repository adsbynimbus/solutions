package adsbynimbus.solutions.dynamicprice.util

import adsbynimbus.solutions.dynamicprice.util.service.Network
import adsbynimbus.solutions.dynamicprice.util.service.NetworkDao
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@ConstructedBy(AppDatabaseConstructor::class)
@Database(entities = [Network::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun networkDao(): NetworkDao
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
