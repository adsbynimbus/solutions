package adsbynimbus.solutions.dynamicprice.util.service

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity
data class Network(
    @PrimaryKey val id: Long,
    val name: String,
    val networkCode: String,
)

@Dao
interface NetworkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(networks: List<Network>)

    @Query("SELECT * FROM Network")
    suspend fun list(): List<Network>

    @Query("SELECT * FROM Network")
    fun listFlow(): Flow<List<Network>>

    @Query("SELECT COUNT(*) as count FROM Network")
    suspend fun count(): Int
}
