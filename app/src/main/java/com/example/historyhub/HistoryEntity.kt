package com.example.historyhub

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_bookmarks")
data class HistoryEntity(
    @PrimaryKey val wikiUrl: String,
    val year: Int,
    val text: String,
    val thumbnailUrl: String?,
    val type: String
)