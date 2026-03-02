package it.sandtv.app.ui.seriea

import it.sandtv.app.R

object TeamLogoUtils {
    
    fun getTeamLogoUrl(teamId: Long): String {
        return "https://api.sofascore.app/api/v1/team/$teamId/image"
    }

    fun getTeamLogo(teamName: String): Int {
        val name = teamName.lowercase().trim()
        
        return when {
            name.contains("atalanta") -> R.drawable.atalanta
            name.contains("bologna") -> R.drawable.bologna
            name.contains("cagliari") -> R.drawable.cagliari
            name.contains("como") -> R.drawable.como
            name.contains("cremonese") -> R.drawable.cremonese
            name.contains("fiorentina") -> R.drawable.fiorentina
            name.contains("genoa") -> R.drawable.genoa
            name.contains("inter") -> R.drawable.inter
            name.contains("juventus") -> R.drawable.juventus
            name.contains("lazio") -> R.drawable.lazio
            name.contains("lecce") -> R.drawable.lecce
            name.contains("milan") -> R.drawable.milan
            name.contains("napoli") -> R.drawable.napoli
            name.contains("parma") -> R.drawable.parma
            name.contains("pisa") -> R.drawable.pisa
            name.contains("roma") -> R.drawable.roma
            name.contains("sassuolo") -> R.drawable.sassuolo
            name.contains("torino") -> R.drawable.torino
            name.contains("udinese") -> R.drawable.udinese
            name.contains("verona") -> R.drawable.verona
            else -> R.drawable.serie_a // Default/Fallback
        }
    }
}
