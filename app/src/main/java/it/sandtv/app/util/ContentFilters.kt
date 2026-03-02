package it.sandtv.app.util

/**
 * Content Filters for categories and content names
 * Replicated from backup project - EXACT same rules
 */
object ContentFilters {
    
    // =====================================================
    // ADULT CONTENT - Keywords to filter from hero/featured
    // =====================================================
    private val adultKeywords = listOf(
        "xxx", "adult", "adults", "hot", "18+", "sex", "erotic", "erotica",
        "porno", "porn", "nude", "naked", "milf", "busty", "hentai",
        "fetish", "bdsm", "stripper", "playboy", "penthouse", "brazzers",
        "bangbros", "naughty", "slutty", "hardcore", "softcore", "threesome",
        "orgy", "blowjob", "handjob", "cumshot", "creampie", "anal",
        "lesbian", "gay", "trans", "shemale", "transsexual", "escort",
        "massage parlor", "kama sutra", "kamasutra", "sensual", "clip"
    )
    
    /**
     * Check if content name/title contains adult keywords
     * Used to filter hero banners and featured content
     */
    fun isAdultContent(name: String?): Boolean {
        if (name == null) return false
        val lower = name.lowercase()
        return adultKeywords.any { lower.contains(it) }
    }
    
    /**
     * Check if content (by name AND category) is adult
     */
    fun isAdultContent(name: String?, category: String?): Boolean {
        return isAdultContent(name) || isAdultContent(category)
    }
    
    /**
     * Check if MOVIE should be excluded from hero banner
     * Combines adult content filter + all category exclusion rules
     */
    fun shouldExcludeMovieFromHero(name: String?, category: String?): Boolean {
        // Adult content filter
        if (isAdultContent(name, category)) return true
        
        // All category filters
        if (shouldExcludeMovieCategory(category)) return true
        
        // Specific name patterns to exclude
        val lower = name?.lowercase() ?: return false
        val excludeNames = listOf(
            "clip", "trailer", "teaser", "promo", 
            "just dance", "zumba", "karaoke", "baby dance",
            "motogp", "moto gp", "formula 1", "formula1", "f1 "
        )
        if (excludeNames.any { lower.contains(it) }) return true
        
        return false
    }
    
    /**
     * Check if SERIES should be excluded from hero banner
     * Combines adult content filter + all category exclusion rules
     */
    fun shouldExcludeSeriesFromHero(name: String?, category: String?): Boolean {
        // Adult content filter
        if (isAdultContent(name, category)) return true
        
        // All category filters
        if (shouldExcludeSeriesCategory(category)) return true
        
        // Hidden series name patterns
        if (name != null && isHiddenSeriesName(name)) return true
        
        // Specific name patterns to exclude
        val lower = name?.lowercase() ?: return false
        val excludeNames = listOf(
            "clip", "trailer", "teaser", "promo",
            "video corsi", "videocorsi", "corsi"
        )
        if (excludeNames.any { lower.contains(it) }) return true
        
        return false
    }
    
    // =====================================================
    // HIDDEN COUNTRIES - Shared between movies and series
    // =====================================================
    private val hiddenCountries = listOf(
        "romania", "germany", "germania", "france", "francia",
        "spain", "spagna", "uk", "england", "inghilterra",
        "poland", "polonia", "turkey", "turchia", "greece", "grecia",
        "portugal", "portogallo", "netherlands", "olanda", "paesi bassi",
        "belgium", "belgio", "russia", "ukraine", "ucraina",
        "brazil", "brasile", "argentina", "mexico", "messico",
        "albania", "serbia", "croatia", "croazia", "bulgaria",
        "hungary", "ungheria", "czech", "republic", "ceca",
        "austria", "switzerland", "svizzera", "sweden", "svezia",
        "norway", "norvegia", "denmark", "danimarca", "finland", "finlandia",
        "arabic", "arabo", "arab", "indian", "indiano", "hindi",
        "chinese", "cinese", "japanese", "giapponese", "korean", "coreano"
    )
    
    // =====================================================
    // FILM - Check if movie category should be hidden
    // =====================================================
    fun shouldExcludeMovieCategory(category: String?): Boolean {
        if (category == null) return false
        val lower = category.lowercase()
        val upper = category.uppercase()
        
        // Exact match categories to hide
        val hiddenExact = listOf(
            "fabio inka training", "general movies", "just dance",
            "musica strumentale", "zumba fitness", "karaoke",
            "argomenti religiosi", "concerti", "baby dance",
            "cartoni animati", "film 3d", "film per non udenti",
            "spettacoli teatrali", "saga me contro te", "documentari fotografia",
            // New exclusions for hero
            "formula uno ondemand", "formula 1 ondemand",
            "motogp ondemand", "moto gp ondemand",
            "saga - me contro te", "saga - tom & jerry", "saga - tom e jerry",
            "ufc channel", "wwe ppv events", "wwe events",
            "film erotici"
        )
        if (hiddenExact.any { lower == it || lower.contains(it) }) return true
        
        // Hide categories containing "Documentari"
        if (lower.contains("documentari")) return true
        
        // Partial match - hide if category contains any of these
        val hiddenContains = listOf(
            "xxx", "zumba", "3d", "karaoke", "cartoni", 
            "non udenti", "teatrali", "baby dance"
        )
        if (hiddenContains.any { lower.contains(it) }) return true
        
        // GERMANY filter - hide categories containing germany
        if (lower.contains("germany")) return true
        
        // VOD filter - hide categories containing VOD as word
        if (upper.startsWith("VOD ") || upper.startsWith("VOD-") || upper.startsWith("VOD|") ||
            upper.contains(" VOD ") || upper.contains(" VOD-") || upper.contains(" VOD|") ||
            upper.contains("|VOD ") || upper.contains("|VOD|") || upper.contains("|VOD-") ||
            upper.endsWith(" VOD") || upper.endsWith("|VOD") || upper == "VOD") {
            return true
        }
        
        // DE filter - more comprehensive
        // Hide if contains "DE" as word boundary (including "DE TOP")
        if (upper.startsWith("DE ") || upper.startsWith("DE:") || upper.startsWith("DE-") ||
            upper.startsWith("DE|") || upper.startsWith("DE_") || upper.startsWith("DE TOP") ||
            upper.contains(" DE ") || upper.contains(" DE:") || upper.contains(" DE-") ||
            upper.contains(" DE|") || upper.contains(" DE_") || upper.contains(" DE TOP") ||
            upper.contains("|DE ") || upper.contains("|DE|") || upper.contains("|DE:") ||
            upper.contains("|DE-") || upper.contains("|DE TOP") ||
            upper.contains("_DE ") || upper.contains("_DE_") || upper.contains("_DE|") ||
            upper.endsWith(" DE") || upper.endsWith("|DE") || upper.endsWith("_DE") ||
            upper == "DE") {
            return true
        }
        
        return hiddenCountries.any { lower.contains(it) }
    }
    
    // =====================================================
    // SERIE TV - Check if series category should be hidden
    // =====================================================
    fun shouldExcludeSeriesCategory(category: String?): Boolean {
        if (category == null) return false
        val lower = category.lowercase()
        val upper = category.uppercase()
        
        // Video Corsi - exact match or contains
        val hiddenExact = listOf(
            "video corsi", "videocorsi", "corsi video", "corsi",
            // New exclusions for hero series
            "programmi dazn", "programmi history channel", "programmi discovery+",
            "programmi discovery", "serie cartoni animati anni",
            "serie bambini e ragazzi", "bambini e ragazzi"
        )
        if (hiddenExact.any { lower == it || lower.contains(it) }) return true
        
        // English - contains match (case insensitive)
        if (lower.contains("english")) return true
        
        // USA filter - hide categories containing USA as word
        if (upper.startsWith("USA ") || upper.startsWith("USA|") || upper.startsWith("USA-") ||
            upper.contains(" USA ") || upper.contains(" USA|") || upper.contains(" USA-") ||
            upper.contains("|USA ") || upper.contains("|USA|") || upper.contains("|USA-") ||
            upper.endsWith(" USA") || upper.endsWith("|USA") ||
            upper == "USA") {
            return true
        }
        
        // DE filter - more comprehensive
        // Hide if contains "DE" as word boundary (not part of another word like "comedy")
        if (upper.startsWith("DE ") || upper.startsWith("DE:") || upper.startsWith("DE-") ||
            upper.startsWith("DE|") || upper.startsWith("DE_") ||
            upper.contains(" DE ") || upper.contains(" DE:") || upper.contains(" DE-") ||
            upper.contains(" DE|") || upper.contains(" DE_") ||
            upper.contains("|DE ") || upper.contains("|DE|") || upper.contains("|DE:") ||
            upper.contains("_DE ") || upper.contains("_DE_") || upper.contains("_DE|") ||
            upper.endsWith(" DE") || upper.endsWith("|DE") || upper.endsWith("_DE") ||
            upper == "DE") {
            return true
        }
        
        // Other partial matches
        val hiddenContains = listOf("xxx", "argomenti religiosi", "baby dance", "concerti")
        if (hiddenContains.any { lower.contains(it) }) return true
        
        return hiddenCountries.any { lower.contains(it) }
    }
    
    // =====================================================
    // SERIE TV - Check if series NAME should be hidden
    // =====================================================
    fun isHiddenSeriesName(name: String): Boolean {
        val upper = name.uppercase()
        return upper.startsWith("DE ") || 
               upper.startsWith("DE-") ||
               upper.startsWith("DE_")
    }
    
    // =====================================================
    // UTILITY - Filter category lists
    // =====================================================
    fun filterMovieCategories(categories: List<String>): List<String> {
        return categories.filterNot { shouldExcludeMovieCategory(it) }
    }
    
    fun filterSeriesCategories(categories: List<String>): List<String> {
        return categories.filterNot { shouldExcludeSeriesCategory(it) }
    }
    
    // =====================================================
    // CLEAN CATEGORY TITLE - Remove symbols
    // =====================================================
    fun cleanCategoryTitle(title: String): String {
        var cleaned = title
            .replace(Regex("[★☆✦✧⭐🌟✩✪✫✬✭✮✯✰✡✢✣✤✥❋✱✲✳✴✵✶✷✸✹✺⋆]"), "")
            .replace(Regex("[◆◇◈◉◊●○◌◍◎•‣⁃◦◘◙]"), "")
            .replace(Regex("[►◄▶◀→←↑↓↔↕➔➜➤»«]"), "")
            .replace(Regex("[▪▫▬▭▮▯■□▢▣▤▥▦▧▨▩]"), "")
            .replace(Regex("[❤💕💗💖💘💝💞💟❣♥🖤🤍🤎💙💚💛🧡💜🩷🩶🩵]"), "")  // Heart emojis removed
            .replace(Regex("[|~=]+"), "")
            .replace(Regex("[-_]+"), " ")
        
        val redundantWords = listOf("film", "serie", "serie tv", "tv", "movies", "series")
        redundantWords.forEach { word ->
            cleaned = cleaned.replace(Regex("(?i)\\b$word\\b"), "")
        }
        
        return cleaned.replace(Regex("\\s+"), " ").trim().ifEmpty { title.trim() }
    }
}
