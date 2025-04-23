package adsbynimbus.solutions.dynamicprice.util.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    foreignKeys = [ForeignKey(
        entity = Network::class,
        parentColumns = ["id"],
        childColumns = ["networkId"],
        onDelete = ForeignKey.CASCADE,
    )]
)
data class Order(
    @PrimaryKey val id: Long,
    val networkId: Long,
    var name: String,
)

@Dao
interface OrdersDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(networks: List<Order>)

    @Query("SELECT * FROM `Order`")
    suspend fun list(): List<Order>

    @Query("SELECT * FROM `Order`")
    fun listFlow(): Flow<List<Order>>

    @Query("SELECT COUNT(*) as count FROM `Order`")
    suspend fun count(): Int
}
