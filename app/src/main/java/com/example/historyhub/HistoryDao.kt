package com.example.historyhub

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: HistoryEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: HistoryEntity)

    @Query("SELECT * FROM history_bookmarks")
    suspend fun getAllBookmarks(): List<HistoryEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM history_bookmarks WHERE wikiUrl = :wikiUrl)")
    suspend fun isBookmarked(wikiUrl: String): Boolean

    @Query("DELETE FROM history_bookmarks WHERE wikiUrl = :wikiUrl")
    suspend fun deleteByWikiUrl(wikiUrl: String): Int
}