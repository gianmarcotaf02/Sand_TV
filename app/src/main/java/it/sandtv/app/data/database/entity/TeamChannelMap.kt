package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "team_channel_map")
data class TeamChannelMap(
    @PrimaryKey
    val teamName: String,
    val channelIds: String, // Comma separated IDs
    val cachedAt: Long = System.currentTimeMillis()
)
