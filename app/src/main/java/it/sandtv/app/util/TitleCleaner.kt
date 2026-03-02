package it.sandtv.app.util

object TitleCleaner {

    /**
     * Cleans an episode name by removing redundant Season/Episode numbers,
     * the series name (if present as a prefix), and any extra "Episodio X" occurrences.
     *
     * Then, it returns a formatted string:
     * - "Episodio {episodeNumber}" (if the title is empty after cleaning or exactly matches "Episodio X")
     * - "Episodio {episodeNumber} - {Clean Title}" (if a valid title remains)
     *
     * @param originalName The raw episode name (e.g., "S1E2 - Fallout - Episodio 2")
     * @param episodeNumber The actual episode number (e.g., 2)
     * @param seriesName The name of the series (optional, to remove it if it's a prefix)
     * @return Formatted episode title like "Episodio 2 - Fallout"
     */
    fun getFormattedEpisodeTitle(
        originalName: String?,
        episodeNumber: Int,
        seriesName: String? = null
    ): String {
        val basePrefix = "Episodio $episodeNumber"
        if (originalName.isNullOrBlank()) {
            return basePrefix
        }

        // At this point we know originalName is neither null nor blank.
        var clean: String = originalName

        // 1. Remove series name prefix if present (e.g., "Fallout - S01E02 - L'inizio")
        seriesName?.let { name ->
            if (clean.startsWith(name, ignoreCase = true)) {
                clean = clean.substring(name.length)
            }
        }

        // 2. Remove standard SXXEYY patterns everywhere (e.g. "S01E02", "S1 E2", "s01e02 -")
        clean = clean.replace(Regex("""S\d+\s*E\d+\s*-?""", RegexOption.IGNORE_CASE), "")

        // 3. Remove standalone "Episode X" or "Episodio X" anywhere in the string
        // Often TMDB or playlists put "Episodio 2 - Title - Episodio 2"
        clean = clean.replace(Regex("""Episode\s*#?\d+\s*-?""", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("""Episodio\s*#?\d+\s*-?""", RegexOption.IGNORE_CASE), "")

        // 4. Remove standalone episode numbers that might be floating (like " - 02 - ")
        // Only if it matches the episode number exactly, to avoid breaking titles with numbers (like "24 Hours")
        clean = clean.replace(Regex("""^\s*-\s*0?$episodeNumber\s*-\s*"""), "")

        // 5. Clean up remaining dashes, colons, and whitespace at the start and end
        clean = clean.trim(' ', '-', ':')

        // If nothing is left (or if the only thing left was the episode number or "Episodio X"),
        // just return the default prefix. Examples: "Episodio 2", "", "Episode 02"
        if (clean.isEmpty() || 
            clean.equals("Episode $episodeNumber", ignoreCase = true) || 
            clean.equals("Episodio $episodeNumber", ignoreCase = true) ||
            clean == episodeNumber.toString() ||
            clean == "0$episodeNumber") {
            return basePrefix
        }

        // Return the final formatted string
        return "$basePrefix - $clean"
    }
}
