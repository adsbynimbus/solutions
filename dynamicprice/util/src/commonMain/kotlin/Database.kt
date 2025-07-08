package adsbynimbus.solutions.dynamicprice.util

import adsbynimbus.solutions.dynamicprice.util.data.*
import androidx.room.*
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@ConstructedBy(AppDatabaseConstructor::class)
@Database(entities = [Network::class, Order::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract val networkDao: NetworkDao
    abstract val ordersDao: OrdersDao
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .fallbackToDestructiveMigration(dropAllTables = false)
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()

expect fun updateTextNow()
