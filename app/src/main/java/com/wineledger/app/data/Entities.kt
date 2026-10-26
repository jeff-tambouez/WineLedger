package com.wineledger.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One person using the app. The cellar is shared, notes and rankings are per profile. */
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val accent: Int,
)

/** A wine, identified by producer + name (see logic.wineKey). */
@Entity(
    tableName = "wines",
    indices = [Index(value = ["wineKey"], unique = true)],
)
data class Wine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wineKey: String,
    val producer: String,
    val name: String,
    val country: String = "",
    val region: String = "",
    val style: String = "",
    /** Grapes, comma separated. */
    val grapes: String = "",
    /** File name inside the private photos folder, or null. */
    val photo: String? = null,
)

/** Physical bottles of one wine and vintage sitting in the shared cellar. */
@Entity(
    tableName = "bottles",
    foreignKeys = [
        ForeignKey(
            entity = Wine::class,
            parentColumns = ["id"],
            childColumns = ["wineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("wineId")],
)
data class Bottle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wineId: Long,
    val vintage: Int? = null,
    val format: Double = 0.75,
    val quantity: Int = 1,
    val price: Double? = null,
    val readyFrom: Int? = null,
    val drinkBy: Int? = null,
    val supplier: String = "",
    /** ISO date (yyyy-MM-dd) or empty. */
    val purchaseDate: String = "",
    val notes: String = "",
    val photo: String? = null,
    val addedAt: Long = 0,
)

/** One person's tasting of a wine. */
@Entity(
    tableName = "tastings",
    foreignKeys = [
        ForeignKey(
            entity = Wine::class,
            parentColumns = ["id"],
            childColumns = ["wineId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("wineId"), Index("profileId")],
)
data class Tasting(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wineId: Long,
    val profileId: Long,
    val year: Int,
    /** Score out of 100. */
    val score: Double,
    val notes: String = "",
    val photo: String? = null,
    val createdAt: Long = 0,
    /** Year printed on the bottle, when known. Not the year of the tasting. */
    val vintage: Int? = null,
)

/** History of opened bottles. Texts are copied so the log survives deletions. */
@Entity(tableName = "openings")
data class OpeningLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO date. */
    val date: String,
    val producer: String,
    val wine: String,
    val vintage: Int? = null,
    val price: Double? = null,
    val score: Double? = null,
)

/** A Veronelli guide wine starred by a profile. */
@Entity(
    tableName = "wishlist",
    primaryKeys = ["profileId", "guideKey"],
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WishlistItem(
    val profileId: Long,
    val guideKey: String,
)

/**
 * Card waiting for a profile at the top of its ledger.
 * stage 0 = "Did you also drink this bottle?" asked, stage 1 = answered yes, rating pending,
 * stage 2 = "Tasting to complete": the other profile put this wine in the cellar.
 */
@Entity(
    tableName = "invitations",
    foreignKeys = [
        ForeignKey(
            entity = Wine::class,
            parentColumns = ["id"],
            childColumns = ["wineId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("wineId"), Index("profileId")],
)
data class Invitation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wineId: Long,
    /** The profile that sees the card. */
    val profileId: Long,
    /** The profile that tasted (stages 0 and 1) or that added the bottle (stage 2). */
    val fromProfileId: Long,
    val year: Int,
    val vintage: Int? = null,
    val stage: Int = 0,
    val createdAt: Long = 0,
) {
    companion object {
        const val STAGE_BACKLOG = 2
    }
}
