package com.wineledger.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // ---- observed lists ----
    @Query("SELECT * FROM profiles ORDER BY id")
    fun observeProfiles(): Flow<List<Profile>>

    @Query("SELECT * FROM wines")
    fun observeWines(): Flow<List<Wine>>

    @Query("SELECT * FROM bottles")
    fun observeBottles(): Flow<List<Bottle>>

    @Query("SELECT * FROM tastings")
    fun observeTastings(): Flow<List<Tasting>>

    @Query("SELECT * FROM openings ORDER BY id")
    fun observeOpenings(): Flow<List<OpeningLog>>

    @Query("SELECT * FROM wishlist")
    fun observeWishlist(): Flow<List<WishlistItem>>

    @Query("SELECT * FROM invitations ORDER BY createdAt")
    fun observeInvitations(): Flow<List<Invitation>>

    // ---- one-shot reads ----
    @Query("SELECT * FROM profiles ORDER BY id")
    suspend fun allProfiles(): List<Profile>

    @Query("SELECT * FROM wines")
    suspend fun allWines(): List<Wine>

    @Query("SELECT * FROM bottles")
    suspend fun allBottles(): List<Bottle>

    @Query("SELECT * FROM tastings")
    suspend fun allTastings(): List<Tasting>

    @Query("SELECT * FROM openings ORDER BY id")
    suspend fun allOpenings(): List<OpeningLog>

    @Query("SELECT * FROM wishlist")
    suspend fun allWishlist(): List<WishlistItem>

    @Query("SELECT * FROM invitations ORDER BY createdAt")
    suspend fun allInvitations(): List<Invitation>

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun profileCount(): Int

    @Query("SELECT * FROM wines WHERE wineKey = :key LIMIT 1")
    suspend fun wineByKey(key: String): Wine?

    @Query("SELECT * FROM wines WHERE id = :id")
    suspend fun wine(id: Long): Wine?

    @Query("SELECT * FROM bottles WHERE id = :id")
    suspend fun bottle(id: Long): Bottle?

    @Query("SELECT * FROM tastings WHERE id = :id")
    suspend fun tasting(id: Long): Tasting?

    @Query("SELECT * FROM invitations WHERE id = :id")
    suspend fun invitation(id: Long): Invitation?

    @Query("SELECT * FROM invitations WHERE wineId = :wineId AND profileId = :profileId AND year = :year LIMIT 1")
    suspend fun findInvitation(wineId: Long, profileId: Long, year: Int): Invitation?

    @Query("SELECT * FROM invitations WHERE wineId = :wineId AND profileId = :profileId")
    suspend fun invitationsFor(wineId: Long, profileId: Long): List<Invitation>

    @Query("SELECT COUNT(*) FROM tastings WHERE wineId = :wineId AND profileId = :profileId")
    suspend fun tastingCount(wineId: Long, profileId: Long): Int

    @Query("SELECT photo FROM wines WHERE photo IS NOT NULL")
    suspend fun winePhotos(): List<String>

    @Query("SELECT photo FROM bottles WHERE photo IS NOT NULL")
    suspend fun bottlePhotos(): List<String>

    @Query("SELECT photo FROM tastings WHERE photo IS NOT NULL")
    suspend fun tastingPhotos(): List<String>

    // ---- writes ----
    @Insert
    suspend fun insertProfile(p: Profile): Long

    @Update
    suspend fun updateProfile(p: Profile)

    @Insert
    suspend fun insertWine(w: Wine): Long

    @Update
    suspend fun updateWine(w: Wine)

    @Query(
        "DELETE FROM wines WHERE id NOT IN (SELECT wineId FROM bottles) " +
            "AND id NOT IN (SELECT wineId FROM tastings) " +
            "AND id NOT IN (SELECT wineId FROM invitations)"
    )
    suspend fun deleteOrphanWines()

    @Insert
    suspend fun insertBottle(b: Bottle): Long

    @Update
    suspend fun updateBottle(b: Bottle)

    @Query("DELETE FROM bottles WHERE id = :id")
    suspend fun deleteBottle(id: Long)

    @Insert
    suspend fun insertTasting(t: Tasting): Long

    @Update
    suspend fun updateTasting(t: Tasting)

    @Query("DELETE FROM tastings WHERE id = :id")
    suspend fun deleteTasting(id: Long)

    @Insert
    suspend fun insertOpening(o: OpeningLog): Long

    @Query("DELETE FROM openings WHERE id = :id")
    suspend fun deleteOpening(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWish(w: WishlistItem)

    @Query("DELETE FROM wishlist WHERE profileId = :profileId AND guideKey = :guideKey")
    suspend fun deleteWish(profileId: Long, guideKey: String)

    @Insert
    suspend fun insertInvitation(i: Invitation): Long

    @Update
    suspend fun updateInvitation(i: Invitation)

    @Query("DELETE FROM invitations WHERE id = :id")
    suspend fun deleteInvitation(id: Long)

    // ---- bulk (restore) ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(list: List<Profile>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWines(list: List<Wine>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBottles(list: List<Bottle>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTastings(list: List<Tasting>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOpenings(list: List<OpeningLog>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWishes(list: List<WishlistItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvitations(list: List<Invitation>)

    @Query("DELETE FROM invitations")
    suspend fun clearInvitations()

    @Query("DELETE FROM wishlist")
    suspend fun clearWishlist()

    @Query("DELETE FROM openings")
    suspend fun clearOpenings()

    @Query("DELETE FROM tastings")
    suspend fun clearTastings()

    @Query("DELETE FROM bottles")
    suspend fun clearBottles()

    @Query("DELETE FROM wines")
    suspend fun clearWines()

    @Query("DELETE FROM profiles")
    suspend fun clearProfiles()
}

@Database(
    entities = [
        Profile::class,
        Wine::class,
        Bottle::class,
        Tasting::class,
        OpeningLog::class,
        WishlistItem::class,
        Invitation::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile
        private var instance: AppDb? = null

        fun get(context: Context): AppDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDb::class.java,
                    "wineledger.db",
                ).build().also { instance = it }
            }
    }
}
