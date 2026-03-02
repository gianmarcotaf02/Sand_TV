package it.sandtv.app.player

import it.sandtv.app.data.database.dao.CustomGroupDao
import it.sandtv.app.data.database.dao.EpisodeDao
import it.sandtv.app.data.database.dao.SeriesDao
import it.sandtv.app.data.database.entity.ContentType
import it.sandtv.app.data.database.entity.GroupItem
import it.sandtv.app.util.TitleCleaner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages auto-play next episode/movie logic
 * Supports both series episodes and custom groups (e.g., Harry Potter saga)
 */
@Singleton
class PlayNextManager @Inject constructor(
    private val episodeDao: EpisodeDao,
    private val customGroupDao: CustomGroupDao,
    private val seriesDao: SeriesDao
) {
    
    data class NextContent(
        val contentType: ContentType,
        val contentId: Long,
        val streamUrl: String,
        val title: String,
        val subtitle: String? = null, // e.g., "Stagione 1 - Episodio 2"
        val season: Int? = null,
        val episode: Int? = null,
        val fromGroup: Boolean = false,
        val groupId: Long? = null
    )
    
    /**
     * Get next episode in a series
     */
    suspend fun getNextEpisode(seriesId: Long, currentSeason: Int, currentEpisode: Int): NextContent? {
        val series = seriesDao.getSeriesById(seriesId)
        val seriesName = series?.name ?: ""
        
        // Try next episode in same season
        val nextInSeason = episodeDao.getEpisode(seriesId, currentSeason, currentEpisode + 1)
        if (nextInSeason != null) {
            val epTitle = TitleCleaner.getFormattedEpisodeTitle(
                originalName = nextInSeason.tmdbName ?: nextInSeason.name,
                episodeNumber = currentEpisode + 1,
                seriesName = seriesName
            )
            val displayTitle = if (epTitle == "Episodio ${currentEpisode + 1}") {
                seriesName
            } else {
                "$seriesName - $epTitle"
            }
            return NextContent(
                contentType = ContentType.EPISODE,
                contentId = nextInSeason.id,
                streamUrl = nextInSeason.streamUrl,
                title = displayTitle,
                subtitle = "Stagione $currentSeason - Episodio ${currentEpisode + 1}",
                season = currentSeason,
                episode = currentEpisode + 1
            )
        }
        
        // Try first episode of next season
        val firstOfNextSeason = episodeDao.getEpisode(seriesId, currentSeason + 1, 1)
        if (firstOfNextSeason != null) {
            val epTitle = TitleCleaner.getFormattedEpisodeTitle(
                originalName = firstOfNextSeason.tmdbName ?: firstOfNextSeason.name,
                episodeNumber = 1,
                seriesName = seriesName
            )
            val displayTitle = if (epTitle == "Episodio 1") {
                seriesName
            } else {
                "$seriesName - $epTitle"
            }
            return NextContent(
                contentType = ContentType.EPISODE,
                contentId = firstOfNextSeason.id,
                streamUrl = firstOfNextSeason.streamUrl,
                title = displayTitle,
                subtitle = "Stagione ${currentSeason + 1} - Episodio 1",
                season = currentSeason + 1,
                episode = 1
            )
        }
        
        return null
    }
    
    /**
     * Get previous episode in a series
     */
    suspend fun getPreviousEpisode(seriesId: Long, currentSeason: Int, currentEpisode: Int): NextContent? {
        val series = seriesDao.getSeriesById(seriesId)
        val seriesName = series?.name ?: ""
        
        if (currentEpisode > 1) {
            // Try previous episode in same season
            val prevInSeason = episodeDao.getEpisode(seriesId, currentSeason, currentEpisode - 1)
            if (prevInSeason != null) {
                val epTitle = TitleCleaner.getFormattedEpisodeTitle(
                    originalName = prevInSeason.tmdbName ?: prevInSeason.name,
                    episodeNumber = currentEpisode - 1,
                    seriesName = seriesName
                )
                val displayTitle = if (epTitle == "Episodio ${currentEpisode - 1}") {
                    seriesName
                } else {
                    "$seriesName - $epTitle"
                }
                return NextContent(
                    contentType = ContentType.EPISODE,
                    contentId = prevInSeason.id,
                    streamUrl = prevInSeason.streamUrl,
                    title = displayTitle,
                    subtitle = "Stagione $currentSeason - Episodio ${currentEpisode - 1}",
                    season = currentSeason,
                    episode = currentEpisode - 1
                )
            }
        }
        
        if (currentSeason > 1) {
            // Try last episode of previous season
            val lastOfPrevSeason = episodeDao.getLastEpisodeOfSeason(seriesId, currentSeason - 1)
            if (lastOfPrevSeason != null) {
                val epTitle = TitleCleaner.getFormattedEpisodeTitle(
                    originalName = lastOfPrevSeason.tmdbName ?: lastOfPrevSeason.name,
                    episodeNumber = lastOfPrevSeason.episodeNumber,
                    seriesName = seriesName
                )
                val displayTitle = if (epTitle == "Episodio ${lastOfPrevSeason.episodeNumber}") {
                    seriesName
                } else {
                    "$seriesName - $epTitle"
                }
                return NextContent(
                    contentType = ContentType.EPISODE,
                    contentId = lastOfPrevSeason.id,
                    streamUrl = lastOfPrevSeason.streamUrl,
                    title = displayTitle,
                    subtitle = "Stagione ${currentSeason - 1} - Episodio ${lastOfPrevSeason.episodeNumber}",
                    season = currentSeason - 1,
                    episode = lastOfPrevSeason.episodeNumber
                )
            }
        }
        
        return null
    }
    
    /**
     * Get next item in a custom group (e.g., Harry Potter 2 after Harry Potter 1)
     */
    suspend fun getNextInGroup(groupId: Long, currentContentId: Long): NextContent? {
        val group = customGroupDao.getGroupById(groupId) ?: return null
        
        // Get current item to find its order
        val currentItem = customGroupDao.getItemPosition(groupId, currentContentId) ?: return null
        
        // Get next item
        var nextItem = customGroupDao.getNextItem(groupId, currentItem.displayOrder)
        
        // If no next and loop is enabled, get first
        if (nextItem == null && group.loopPlayback) {
            nextItem = customGroupDao.getFirstItem(groupId)
            // Don't loop to self
            if (nextItem?.contentId == currentContentId) {
                nextItem = null
            }
        }
        
        return nextItem?.toNextContent(groupId)
    }
    
    /**
     * Get previous item in a custom group
     */
    suspend fun getPreviousInGroup(groupId: Long, currentContentId: Long): NextContent? {
        val currentItem = customGroupDao.getItemPosition(groupId, currentContentId) ?: return null
        val prevItem = customGroupDao.getPreviousItem(groupId, currentItem.displayOrder)
        return prevItem?.toNextContent(groupId)
    }
    
    /**
     * Check if content has a next item available
     */
    suspend fun hasNext(
        contentType: ContentType,
        contentId: Long,
        seriesId: Long? = null,
        season: Int? = null,
        episode: Int? = null,
        groupId: Long? = null
    ): Boolean {
        // Check custom group first
        if (groupId != null) {
            return getNextInGroup(groupId, contentId) != null
        }
        
        // Check series
        if (contentType == ContentType.EPISODE && seriesId != null && season != null && episode != null) {
            return getNextEpisode(seriesId, season, episode) != null
        }
        
        return false
    }
    
    /**
     * Get the appropriate next content based on context
     */
    suspend fun getNext(
        contentType: ContentType,
        contentId: Long,
        seriesId: Long? = null,
        season: Int? = null,
        episode: Int? = null,
        groupId: Long? = null
    ): NextContent? {
        // Priority: Custom group > Series
        if (groupId != null) {
            val nextInGroup = getNextInGroup(groupId, contentId)
            if (nextInGroup != null) return nextInGroup
        }
        
        if (contentType == ContentType.EPISODE && seriesId != null && season != null && episode != null) {
            return getNextEpisode(seriesId, season, episode)
        }
        
        return null
    }
    
    /**
     * Get the appropriate previous content based on context
     */
    suspend fun getPrevious(
        contentType: ContentType,
        contentId: Long,
        seriesId: Long? = null,
        season: Int? = null,
        episode: Int? = null,
        groupId: Long? = null
    ): NextContent? {
        // Priority: Custom group > Series
        if (groupId != null) {
            val prevInGroup = getPreviousInGroup(groupId, contentId)
            if (prevInGroup != null) return prevInGroup
        }
        
        if (contentType == ContentType.EPISODE && seriesId != null && season != null && episode != null) {
            return getPreviousEpisode(seriesId, season, episode)
        }
        
        return null
    }
    
    private fun GroupItem.toNextContent(groupId: Long) = NextContent(
        contentType = contentType,
        contentId = contentId,
        streamUrl = streamUrl ?: "",
        title = title,
        subtitle = subtitle,
        fromGroup = true,
        groupId = groupId
    )
}
