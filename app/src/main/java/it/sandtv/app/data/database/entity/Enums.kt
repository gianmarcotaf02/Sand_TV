package it.sandtv.app.data.database.entity

/**
 * Enum types used across database entities
 */

// Content type for watch progress and favorites
enum class ContentType {
    CHANNEL,
    MOVIE,
    SERIES,
    EPISODE
}

// Category types for content organization
enum class CategoryType {
    LIVE_TV,    // Canali live
    MOVIE,      // Film
    SERIES      // Serie TV
}

// Favorite content types (same as ContentType for now)
enum class FavoriteType {
    CHANNEL,
    MOVIE,
    SERIES
}

// Stream quality options
enum class StreamQuality {
    UNKNOWN,
    AUTO,
    SD,
    HD,
    FHD,    // 1080p
    UHD,    // 4K
    UHD_4K  // Alias for UHD
}
