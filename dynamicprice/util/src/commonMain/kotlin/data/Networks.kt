package adsbynimbus.solutions.dynamicprice.util.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity
data class Network(
    @PrimaryKey val id: Long,
    val name: String,
    val networkCode: String,
) {
    companion object {
        val None = Network(id = 0, name = "None Selected", networkCode = "")
    }
}

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
